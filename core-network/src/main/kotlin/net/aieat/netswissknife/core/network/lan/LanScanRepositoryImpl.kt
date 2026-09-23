package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newTcpSocket
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
) : LanScanRepository {

    private val effectiveMacResolver: MacResolver = macResolver ?: ArpFileMacResolver(arpTableReader)
    private val effectiveTcpProbe: TcpPresenceProbe = tcpProbe ?: SocketTcpPresenceProbe(
        binder = binder,
        socketFactory = socketFactory,
    )
    private val effectivePortChecker: PortChecker = portChecker ?: createPortChecker(binder, socketFactory)
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

        private fun createPortChecker(binder: NetworkBinder, socketFactory: () -> Socket): PortChecker =
            { ip, port, timeoutMs ->
                var socket: Socket? = null
                try {
                    socket = binder.newTcpSocket(ip, socketFactory)
                    socket.connect(InetSocketAddress(ip, port), timeoutMs.coerceAtMost(500))
                    true
                } catch (error: SecurityException) {
                    throw LocalNetworkPermissionDeniedException(error)
                } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
                    throw permissionDenied
                } catch (_: Exception) {
                    false
                } finally {
                    runCatching { socket?.close() }
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

        val DEFAULT_PORT_CHECKER: PortChecker = { ip, port, timeoutMs ->
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs.coerceAtMost(500))
                true
            } catch (_: Exception) {
                false
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    override fun scan(request: LanScanRequest): Flow<LanScanUpdate> = flow {
        val startTime = clock.nowNanos()
        val ips = SubnetUtils.parseSubnet(request.subnet)
        val totalCount = ips.size
        val aliveHosts = mutableListOf<LanHost>()
        var uncertainDiagnostics: List<LanScanDiagnostic> = emptyList()
        var uncertainTotal = 0
        val effectiveConcurrency = request.concurrency.coerceIn(1, 500)

        data class CompletedHost(
            val host: LanHost?,
            val diagnostic: LanScanDiagnostic?,
        )

        coroutineScope {
            val scanJob = currentCoroutineContext()[Job] ?: error("Scan coroutine has no Job")
            val pending = Channel<String>(capacity = effectiveConcurrency)
            val completed = Channel<CompletedHost>(capacity = effectiveConcurrency)
            val producer = launch {
                try {
                    for (ip in ips) pending.send(ip)
                } finally {
                    pending.close()
                }
            }
            val workers = List(minOf(effectiveConcurrency, ips.size)) {
                launch(Dispatchers.IO) {
                    try {
                        for (ip in pending) {
                            currentCoroutineContext().ensureActive()
                            val result = discoverHost(ip, request)
                            completed.send(CompletedHost(result.host, result.diagnostic))
                        }
                    } catch (cancelled: CancellationException) {
                        // A probe that independently throws CancellationException must stop
                        // the whole scan; cancellation caused by the caller is already active.
                        if (currentCoroutineContext().isActive) scanJob.cancel(cancelled)
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
                    emit(LanScanUpdate.HostFound(it, scannedCount, totalCount, uncertainCount))
                } ?: emit(
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
                currentCoroutineContext().ensureActive()
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
        emit(
            LanScanUpdate.ScanComplete(
                LanScanSummary(
                    subnet = request.subnet,
                    totalScanned = totalCount,
                    aliveHosts = hosts.size,
                    scanDurationMs = clock.elapsedMillisSince(startTime),
                    hosts = hosts,
                    macResolutionSupported = macResolutionSupported,
                    uncertainHosts = uncertainDiagnostics,
                    uncertainCount = uncertainTotal,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

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
        val result = discoverPresence(ip, request)
        val presence = result.presence ?: return DiscoveryResult(null, result.diagnostic)
        return DiscoveryResult(enrich(ip, presence, request), null)
    }

    private data class PresenceResult(
        val presence: Presence?,
        val diagnostic: LanScanDiagnostic? = null,
    )

    private suspend fun discoverPresence(ip: String, request: LanScanRequest): PresenceResult {
        val icmpRtt = effectiveIcmpProbe.echo(ip, request.timeoutMs)
        if (icmpRtt != null) {
            return PresenceResult(
                presenceFromEvidence(listOf(PresenceEvidence.IcmpEcho(icmpRtt))),
            )
        }

        // Old callers supplied a synchronous checker and expect its exact behavior. New
        // callers use the strategy pipeline below.
        if (hostChecker != null) return PresenceResult(null)

        val tcp = effectiveTcpProbe.probe(ip, request.presencePorts, request.timeoutMs)
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
        var hostname: String? = presence.presenceName ?: if (hostChecker != null) {
            runCatching {
                InetAddress.getByName(ip).canonicalHostName.takeUnless { it == ip }
            }.getOrNull()
        } else {
            var resolved: String? = null
            for (probe in effectiveNameProbes.filterNot { it is PresenceNameProbe }) {
                resolved = try {
                    probe.resolveName(ip, request.timeoutMs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
                    throw permissionDenied
                } catch (_: Exception) {
                    null
                }
                if (!resolved.isNullOrBlank()) {
                    if (probe is ReverseDnsNameProbe) presence.methods += DiscoveryMethod.RDNS
                    break
                }
            }
            resolved
        }
        hostname = hostname?.takeIf { it.isNotBlank() } ?: presence.presenceName

        val mac = try {
            effectiveMacResolver.resolve(ip)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val openPorts = mutableListOf<Int>()
        for (port in QUICK_PORTS) {
            currentCoroutineContext().ensureActive()
            if (effectivePortChecker(ip, port, request.timeoutMs)) openPorts += port
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
