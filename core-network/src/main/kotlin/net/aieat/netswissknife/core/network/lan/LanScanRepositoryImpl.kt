package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
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
    private val portChecker: PortChecker = DEFAULT_PORT_CHECKER,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val icmpProbe: IcmpProbe = ReachabilityIcmpProbe(),
    private val tcpProbe: TcpPresenceProbe = SocketTcpPresenceProbe(),
    private val nameProbes: List<NameProbe> = listOf(
        ReverseDnsNameProbe(),
        NetBiosNameProbe(),
        MdnsReverseNameProbe(),
    ),
    macResolver: MacResolver? = null,
) : LanScanRepository {

    private val effectiveMacResolver: MacResolver = macResolver ?: ArpFileMacResolver(arpTableReader)
    private val effectiveIcmpProbe: IcmpProbe = hostChecker?.let { checker ->
        IcmpProbe { ip, timeoutMs -> checker(ip, timeoutMs) }
    } ?: icmpProbe

    companion object {
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
        val effectiveConcurrency = request.concurrency.coerceIn(1, 500)

        data class CompletedHost(val host: LanHost?)

        coroutineScope {
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
                    for (ip in pending) {
                        val host = discoverHost(ip, request)
                        completed.send(CompletedHost(host))
                    }
                }
            }
            launch {
                producer.join()
                workers.joinAll()
                completed.close()
            }

            var scannedCount = 0
            for (completedHost in completed) {
                scannedCount++
                completedHost.host?.let {
                    aliveHosts += it
                    emit(LanScanUpdate.HostFound(it, scannedCount, totalCount))
                } ?: emit(LanScanUpdate.ScanProgress(scannedCount, totalCount))
            }
        }

        // A second read is useful on API levels where the first read happened before ARP
        // resolution. The injected resolver makes this behavior deterministic in tests.
        if (effectiveMacResolver.supported) {
            for (index in aliveHosts.indices) {
                val host = aliveHosts[index]
                if (host.macAddress == null) {
                    val mac = effectiveMacResolver.resolve(host.ip)
                    if (mac != null) {
                        aliveHosts[index] = host.copy(
                            macAddress = mac,
                            vendor = OuiDatabase.lookup(mac),
                            macSource = MacSource.ARP,
                        )
                    }
                }
            }
        }

        val hosts = aliveHosts.sortedBy { SubnetUtils.parseIpToLong(it.ip) }
        emit(
            LanScanUpdate.ScanComplete(
                LanScanSummary(
                    subnet = request.subnet,
                    totalScanned = totalCount,
                    aliveHosts = hosts.size,
                    scanDurationMs = clock.elapsedMillisSince(startTime),
                    hosts = hosts,
                    macResolutionSupported = effectiveMacResolver.supported,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private data class Presence(
        val methods: MutableSet<DiscoveryMethod>,
        val pingMs: Long,
        val presenceName: String? = null,
    )

    private suspend fun discoverHost(ip: String, request: LanScanRequest): LanHost? {
        val presence = discoverPresence(ip, request) ?: return null
        return enrich(ip, presence, request)
    }

    private suspend fun discoverPresence(ip: String, request: LanScanRequest): Presence? {
        val methods = linkedSetOf<DiscoveryMethod>()
        val icmpRtt = effectiveIcmpProbe.echo(ip, request.timeoutMs)
        if (icmpRtt != null) {
            methods += DiscoveryMethod.ICMP
            return Presence(methods, icmpRtt)
        }

        // Old callers supplied a synchronous checker and expect its exact behavior. New
        // callers use the strategy pipeline below.
        if (hostChecker != null) return null

        when (val tcp = tcpProbe.probe(ip, request.presencePorts, request.timeoutMs)) {
            is TcpPresence.Open -> {
                methods += DiscoveryMethod.TCP_OPEN
                return Presence(methods, 0L)
            }
            is TcpPresence.Refused -> {
                methods += DiscoveryMethod.TCP_REFUSED
                return Presence(methods, 0L)
            }
            TcpPresence.None -> Unit
        }

        if (request.enableNameProbes) {
            for (probe in nameProbes.filterNot { it is ReverseDnsNameProbe }) {
                val name = runCatching { probe.resolveName(ip, request.timeoutMs) }.getOrNull()
                if (!name.isNullOrBlank()) {
                    val method = when (probe) {
                        is NetBiosNameProbe -> DiscoveryMethod.NETBIOS
                        is MdnsReverseNameProbe -> DiscoveryMethod.MDNS
                        else -> null
                    } ?: continue
                    methods += method
                    return Presence(methods, 0L, name)
                }
            }
        }
        return null
    }

    private suspend fun enrich(ip: String, presence: Presence, request: LanScanRequest): LanHost {
        var hostname: String? = if (hostChecker != null) {
            runCatching {
                InetAddress.getByName(ip).canonicalHostName.takeUnless { it == ip }
            }.getOrNull()
        } else {
            var resolved: String? = null
            for (probe in nameProbes) {
                resolved = runCatching { probe.resolveName(ip, request.timeoutMs) }.getOrNull()
                if (!resolved.isNullOrBlank()) {
                    if (probe is ReverseDnsNameProbe) presence.methods += DiscoveryMethod.RDNS
                    break
                }
            }
            resolved
        }
        hostname = hostname?.takeIf { it.isNotBlank() } ?: presence.presenceName

        val mac = runCatching { effectiveMacResolver.resolve(ip) }.getOrNull()
        return LanHost(
            ip = ip,
            hostname = hostname,
            macAddress = mac,
            vendor = mac?.let(OuiDatabase::lookup),
            openPorts = QUICK_PORTS.filter { port -> portChecker(ip, port, request.timeoutMs) },
            pingTimeMs = presence.pingMs,
            isGateway = ip == request.gatewayIp,
            discoveredVia = presence.methods.toSet(),
            macSource = if (mac == null) MacSource.NONE else MacSource.ARP,
        )
    }
}
