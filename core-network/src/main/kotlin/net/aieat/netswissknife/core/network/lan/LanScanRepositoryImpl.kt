package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newTcpSocket
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationResourcesContext
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ResourceScope
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

private val QUICK_PORTS = listOf(
    21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 3306, 3389, 5900, 8080, 8443,
)

/**
 * Multi-probe LAN discovery repository.
 *
 * Each host gets one ICMP attempt, a sequential TCP presence probe, and optional
 * NetBIOS/mDNS presence probes. Name/MAC/port enrichment runs only after presence is
 * established. The final ARP pass is intentionally after all workers complete so the
 * probes can populate the kernel neighbour cache first.
 */
class LanScanRepositoryImpl(
    /** Legacy seam retained for existing callers; when set it is the sole presence probe. */
    private val hostChecker: HostChecker? = null,
    private val arpTableReader: ArpTableReader = DEFAULT_ARP_READER,
    private val portChecker: PortChecker? = null,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val icmpProbe: IcmpProbe = ReachabilityIcmpProbe(),
    private val tcpProbe: TcpPresenceProbe? = null,
    private val nameProbes: List<NameProbe>? = null,
    macResolver: MacResolver? = null,
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> Socket = { Socket() },
    private val operationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LanScanRepository {

    private val effectiveMacResolver: MacResolver = macResolver ?: ArpFileMacResolver(arpTableReader)
    private val effectiveTcpProbe: TcpPresenceProbe = tcpProbe ?: SocketTcpPresenceProbe(
        binder = binder,
        socketFactory = socketFactory,
    )
    private val effectivePortChecker: PortChecker? = portChecker
    private val effectiveNameProbes: List<NameProbe> = nameProbes ?: run {
        val udpExchange = DefaultUdpExchange.withBinder(binder)
        listOf(
            ReverseDnsNameProbe(),
            NetBiosNameProbe(udpExchange),
            MdnsReverseNameProbe(udpExchange),
        )
    }
    private val effectiveIcmpProbe: IcmpProbe = hostChecker?.let { checker ->
        IcmpProbe { ip, timeoutMs -> checker(ip, timeoutMs) }
    } ?: icmpProbe

    companion object {
        private const val MAX_DIAGNOSTIC_DETAILS = 100

        private fun createPortChecker(
            binder: NetworkBinder,
            socketFactory: () -> Socket,
            resources: ResourceScope?,
        ): PortChecker =
            { ip, port, timeoutMs ->
                var socket: Socket? = null
                try {
                    socket = binder.newTcpSocket(ip) {
                        socketFactory().also { created ->
                            socket = created
                            resources?.register(created)
                        }
                    }
                    socket.connect(InetSocketAddress(ip, port), timeoutMs.coerceAtMost(500))
                    true
                } catch (error: SecurityException) {
                    throw LocalNetworkPermissionDeniedException(error)
                } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
                    throw permissionDenied
                } catch (_: Exception) {
                    false
                } finally {
                    val socketToClose = socket
                    if (
                        socketToClose != null &&
                        (resources == null || resources.release(socketToClose))
                    ) {
                        runCatching { socketToClose.close() }
                    }
                }
            }

        val DEFAULT_HOST_CHECKER: HostChecker = { ip, timeoutMs ->
            try {
                val start = System.nanoTime()
                if (InetAddress.getByName(ip).isReachable(timeoutMs)) {
                    ((System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000L).coerceAtLeast(1L)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        val DEFAULT_ARP_READER: ArpTableReader = {
            runCatching { java.io.File("/proc/net/arp").readText() }.getOrDefault("")
        }

    }

    override fun scan(request: LanScanRequest): Flow<LanScanUpdate> = flow {
        scan(request, newSession(request)).collect { emit(it) }
    }

    override fun scan(
        request: LanScanRequest,
        operationSession: OperationSession,
    ): Flow<LanScanUpdate> = channelFlow {
        val startTime = clock.nowNanos()
        val session = operationSession
        val effectiveConcurrency = request.concurrency
            .coerceIn(1, 500)
            .coerceAtMost(session.budget.maxConcurrentProbes)
        var completedSummary: LanScanSummary? = null

        OperationRunner.runOrJoin(session) {
            val ips = SubnetUtils.parseSubnet(request.subnet)
            val totalCount = ips.size
            val aliveHosts = mutableListOf<LanHost>()
            var uncertainDiagnostics: List<LanScanDiagnostic> = emptyList()
            var uncertainTotal = 0

            data class CompletedHost(
                val host: LanHost?,
                val diagnostic: LanScanDiagnostic?,
            )

            coroutineScope {
                val pending = Channel<String>(capacity = effectiveConcurrency)
                val completed = Channel<CompletedHost>(capacity = effectiveConcurrency)
                val producer = launch {
                    try {
                        for (ip in ips) {
                            ensureOperationActive()
                            pending.send(ip)
                        }
                    } finally {
                        pending.close()
                    }
                }
                val workers = List(minOf(effectiveConcurrency, ips.size)) {
                    launch(operationDispatcher) {
                        try {
                            for (ip in pending) {
                                ensureOperationActive()
                                val result = discoverHost(ip, request)
                                ensureOperationActive()
                                completed.send(CompletedHost(result.host, result.diagnostic))
                            }
                        } catch (cancelled: CancellationException) {
                            // A probe that independently throws CancellationException must stop
                            // the owning operation so its cancellation hook closes sibling sockets.
                            // Caller cancellation already cancels this worker and the operation.
                            if (currentCoroutineContext().isActive) {
                                session.cancel(CancellationReason.PARENT_CANCELLED)
                            }
                            throw cancelled
                        }
                    }
                }
                launch {
                    producer.join()
                    workers.joinAll()
                    completed.close()
                }

                var scannedCount = 0
                val uncertainHosts = mutableListOf<LanScanDiagnostic>()
                var uncertainCount = 0
                for (completedHost in completed) {
                    ensureOperationActive()
                    scannedCount++
                    var diagnosticForUpdate: LanScanDiagnostic? = null
                    completedHost.diagnostic?.let {
                        uncertainCount++
                        if (uncertainHosts.size < MAX_DIAGNOSTIC_DETAILS) {
                            uncertainHosts += it
                            diagnosticForUpdate = it
                        }
                    }
                    completedHost.host?.let {
                        aliveHosts += it
                        send(LanScanUpdate.HostFound(it, scannedCount, totalCount, uncertainCount))
                    } ?: send(
                        LanScanUpdate.ScanProgress(
                            scannedCount = scannedCount,
                            totalCount = totalCount,
                            uncertainCount = uncertainCount,
                            diagnostic = diagnosticForUpdate,
                        ),
                    )
                }

                // Keep this local until ScanComplete so uncertain observations never enter hosts.
                uncertainDiagnostics = uncertainHosts.toList()
                uncertainTotal = uncertainCount
            }

            // ARP may have been read during concurrent enrichment, or during an earlier scan,
            // before this scan populated the kernel cache. A readable final snapshot is the
            // authoritative MAC view for this scan, including clearing stale prior mappings.
            val scanMacResolver = try {
                effectiveMacResolver.snapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val freshSnapshotSupported = try {
                scanMacResolver?.supported == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (freshSnapshotSupported && scanMacResolver != null) {
                for (index in aliveHosts.indices) {
                    ensureOperationActive()
                    val host = aliveHosts[index]
                    val mac = try {
                        scanMacResolver.resolve(host.ip)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A failed final lookup must not erase a mapping already found by a
                        // worker; continue completing the scan with that best-effort value.
                        continue
                    }
                    ensureOperationActive()
                    aliveHosts[index] = host.copy(
                        macAddress = mac,
                        vendor = mac?.let(OuiDatabase::lookup),
                        macSource = if (mac == null) MacSource.NONE else MacSource.ARP,
                    )
                }
            }

            val cachedResolverSupported = try {
                effectiveMacResolver.supported
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            val macResolutionSupported = freshSnapshotSupported || cachedResolverSupported

            val hosts = aliveHosts.sortedBy { SubnetUtils.parseIpToLong(it.ip) }
            completedSummary = LanScanSummary(
                subnet = request.subnet,
                totalScanned = totalCount,
                aliveHosts = hosts.size,
                scanDurationMs = clock.elapsedMillisSince(startTime),
                hosts = hosts,
                macResolutionSupported = macResolutionSupported,
                uncertainHosts = uncertainDiagnostics,
                uncertainCount = uncertainTotal,
            )
        }
        send(LanScanUpdate.ScanComplete(checkNotNull(completedSummary)))
    }.flowOn(operationDispatcher)

    private fun newSession(request: LanScanRequest): OperationSession {
        val effectiveConcurrency = request.concurrency.coerceIn(1, 500)
        return OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                maxConcurrentProbes = effectiveConcurrency,
                clock = clock,
            )
        )
    }

    private data class Presence(
        val methods: MutableSet<DiscoveryMethod>,
        val pingMs: Long,
        val presenceName: String? = null,
    )

    private data class DiscoveryResult(
        val host: LanHost?,
        val diagnostic: LanScanDiagnostic?,
    )

    private suspend fun discoverHost(ip: String, request: LanScanRequest): DiscoveryResult {
        ensureCurrentOperationActive()
        val result = discoverPresence(ip, request)
        ensureCurrentOperationActive()
        val presence = result.presence ?: return DiscoveryResult(null, result.diagnostic)
        val host = enrich(ip, presence, request)
        ensureCurrentOperationActive()
        return DiscoveryResult(host, null)
    }

    private data class PresenceResult(
        val presence: Presence?,
        val diagnostic: LanScanDiagnostic? = null,
    )

    private suspend fun discoverPresence(ip: String, request: LanScanRequest): PresenceResult {
        val icmpRtt = effectiveIcmpProbe.echo(ip, request.timeoutMs)
        ensureCurrentOperationActive()
        if (icmpRtt != null) {
            return PresenceResult(
                presenceFromEvidence(listOf(PresenceEvidence.IcmpEcho(icmpRtt))),
            )
        }

        // Old callers supplied a synchronous checker and expect its exact behavior. New
        // callers use the strategy pipeline below.
        if (hostChecker != null) return PresenceResult(null)

        val tcp = effectiveTcpProbe.probe(ip, request.presencePorts, request.timeoutMs)
        ensureCurrentOperationActive()
        when (tcp) {
            is TcpPresence.Open -> {
                return PresenceResult(
                    presenceFromEvidence(listOf(PresenceEvidence.TcpConnection(tcp.port))),
                )
            }
            is TcpPresence.Refused -> Unit
            TcpPresence.None -> Unit
            else -> Unit
        }

        val tcpDiagnostic = when (tcp) {
            is TcpPresence.Refused -> LanScanDiagnostic(ip, LanScanDiagnosticReason.TCP_REFUSED, tcp.port, tcp.detail)
            is TcpPresence.TimedOut -> LanScanDiagnostic(ip, LanScanDiagnosticReason.TCP_TIMED_OUT, tcp.port, tcp.detail)
            is TcpPresence.Unreachable -> LanScanDiagnostic(ip, LanScanDiagnosticReason.TCP_UNREACHABLE, tcp.port, tcp.detail)
            is TcpPresence.PolicyDenied -> LanScanDiagnostic(ip, LanScanDiagnosticReason.TCP_POLICY_DENIED, tcp.port, tcp.detail)
            is TcpPresence.UnknownFailure -> LanScanDiagnostic(ip, LanScanDiagnosticReason.TCP_UNKNOWN_FAILURE, tcp.port, tcp.detail)
            else -> null
        }

        if (request.enableNameProbes) {
            for (probe in effectiveNameProbes.filterNot { it is ReverseDnsNameProbe }) {
                val reply = try {
                    when (probe) {
                        is PresenceNameProbe -> probe.probePresence(ip, request.timeoutMs)
                        else -> null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
                    throw permissionDenied
                } catch (_: Exception) {
                    null
                }
                ensureCurrentOperationActive()
                if (reply != null) {
                    return PresenceResult(
                        presenceFromEvidence(
                            listOf(PresenceEvidence.LocalProtocolReply(reply.method, reply.name)),
                        ),
                        null,
                    )
                }
            }
        }
        return PresenceResult(null, tcpDiagnostic)
    }

    private fun presenceFromEvidence(evidence: Iterable<PresenceEvidence>): Presence? {
        val decision = PresenceClassifier.classify(evidence)
        if (!decision.confirmed) return null
        return Presence(
            methods = decision.methods.toMutableSet(),
            pingMs = decision.pingMs,
            presenceName = decision.name,
        )
    }

    private suspend fun enrich(ip: String, presence: Presence, request: LanScanRequest): LanHost {
        ensureCurrentOperationActive()
        var hostname: String? = presence.presenceName ?: if (hostChecker != null) {
            runCatching {
                InetAddress.getByName(ip).canonicalHostName.takeUnless { it == ip }
            }.getOrNull()
        } else {
            var resolved: String? = null
            for (probe in effectiveNameProbes.filterNot { it is PresenceNameProbe }) {
                ensureCurrentOperationActive()
                resolved = try {
                    probe.resolveName(ip, request.timeoutMs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
                    throw permissionDenied
                } catch (_: Exception) {
                    null
                }
                ensureCurrentOperationActive()
                if (!resolved.isNullOrBlank()) {
                    if (probe is ReverseDnsNameProbe) presence.methods += DiscoveryMethod.RDNS
                    break
                }
            }
            resolved
        }
        hostname = hostname?.takeIf { it.isNotBlank() } ?: presence.presenceName

        ensureCurrentOperationActive()
        val mac = try {
            effectiveMacResolver.resolve(ip)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        ensureCurrentOperationActive()
        val openPorts = mutableListOf<Int>()
        val resources = currentCoroutineContext()[OperationResourcesContext]?.resources
        val portChecker = effectivePortChecker ?: createPortChecker(binder, socketFactory, resources)
        for (port in QUICK_PORTS) {
            ensureCurrentOperationActive()
            if (portChecker(ip, port, request.timeoutMs)) openPorts += port
            ensureCurrentOperationActive()
        }
        return LanHost(
            ip = ip,
            hostname = hostname,
            macAddress = mac,
            vendor = mac?.let(OuiDatabase::lookup),
            openPorts = openPorts,
            pingTimeMs = presence.pingMs,
            isGateway = ip == request.gatewayIp,
            discoveredVia = presence.methods.toSet(),
            macSource = if (mac == null) MacSource.NONE else MacSource.ARP,
        )
    }
}
