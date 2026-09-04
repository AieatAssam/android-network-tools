package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince

// ── Functional types injected for testability ─────────────────────────────────

/** Probes [ip] for reachability. Returns response time ms if alive, null otherwise. */
typealias HostChecker = (ip: String, timeoutMs: Int) -> Long?

/** Returns the raw content of the ARP cache (e.g. from /proc/net/arp). */
typealias ArpTableReader = () -> String

/** Returns true if [port] on [ip] is open (TCP connect succeeded). */
typealias PortChecker = (ip: String, port: Int, timeoutMs: Int) -> Boolean

// ── Common ports to quick-scan on every discovered host ────────────────────────

private val QUICK_PORTS = listOf(21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 3306, 3389, 5900, 8080, 8443)

/**
 * Production [LanScanRepository] that:
 *  1. Generates all host IPs from the given CIDR subnet.
 *  2. Probes each IP for reachability using [hostChecker] (bounded concurrent workers).
 *  3. Reads the ARP table via [arpTableReader] to resolve MAC addresses.
 *  4. Looks up the vendor name from [OuiDatabase].
 *  5. Performs a quick TCP port scan on every live host using [portChecker].
 *  6. Attempts reverse DNS for the hostname.
 *
 * All three lambdas default to real-world implementations and can be
 * replaced with fakes in tests.
 */
class LanScanRepositoryImpl(
    private val hostChecker: HostChecker = DEFAULT_HOST_CHECKER,
    private val arpTableReader: ArpTableReader = DEFAULT_ARP_READER,
    private val portChecker: PortChecker = DEFAULT_PORT_CHECKER,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : LanScanRepository {

    companion object {
        val DEFAULT_HOST_CHECKER: HostChecker = { ip, timeoutMs ->
            try {
                val addr = InetAddress.getByName(ip)
                val start = System.nanoTime()
                if (addr.isReachable(timeoutMs)) (System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000L else null
            } catch (_: Exception) {
                null
            }
        }

        val DEFAULT_ARP_READER: ArpTableReader = {
            try {
                java.io.File("/proc/net/arp").readText()
            } catch (_: Exception) {
                ""
            }
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
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    override fun scan(subnet: String, timeoutMs: Int, concurrency: Int): Flow<LanScanUpdate> = flow {
        val startTime = clock.nowNanos()
        val ips = SubnetUtils.parseSubnet(subnet)
        val totalCount = ips.size
        val aliveHosts = mutableListOf<LanHost>()

        // Read the ARP table once upfront for MAC resolution
        val arpMap = parseArpTable(arpTableReader())

        // Detect gateway: the lowest host IP is typically the router
        val gatewayIp = ips.firstOrNull()

        val effectiveConcurrency = concurrency.coerceIn(1, 500)

        data class CompletedHost(val host: LanHost?)

        // Feed a bounded work queue to a fixed worker set. Each worker sends
        // only after the complete host enrichment finishes, so consuming the
        // result channel exposes actual completion order. The bounded channel
        // also prevents an unbounded /16 or /8 scan from retaining every
        // result while a UI collector is busy rendering.
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
            val workerCount = minOf(effectiveConcurrency, ips.size)
            val workers = List(workerCount) {
                launch(Dispatchers.IO) {
                    for (ip in pending) {
                        val pingMs = hostChecker(ip, timeoutMs)
                        completed.send(
                            CompletedHost(
                                pingMs?.let { buildHost(ip, it, arpMap, gatewayIp, timeoutMs) }
                            )
                        )
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
                val host = completedHost.host
                scannedCount++
                if (host != null) {
                    aliveHosts += host
                    emit(LanScanUpdate.HostFound(host, scannedCount, totalCount))
                } else {
                    emit(LanScanUpdate.ScanProgress(scannedCount, totalCount))
                }
            }
        }

        val summary = LanScanSummary(
            subnet = subnet,
            totalScanned = totalCount,
            aliveHosts = aliveHosts.size,
            scanDurationMs = clock.elapsedMillisSince(startTime),
            hosts = aliveHosts.sortedBy { SubnetUtils.parseIpToLong(it.ip) },
        )
        emit(LanScanUpdate.ScanComplete(summary))
    }.flowOn(Dispatchers.IO)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildHost(
        ip: String,
        pingMs: Long,
        arpMap: Map<String, String>,
        gatewayIp: String?,
        timeoutMs: Int,
    ): LanHost {
        val macAddress = arpMap[ip]
        val vendor = macAddress?.let { OuiDatabase.lookup(it) }
        val hostname = resolveHostname(ip)
        val openPorts = QUICK_PORTS.filter { port -> portChecker(ip, port, timeoutMs) }
        return LanHost(
            ip = ip,
            hostname = hostname,
            macAddress = macAddress,
            vendor = vendor,
            openPorts = openPorts,
            pingTimeMs = pingMs,
            isGateway = ip == gatewayIp,
        )
    }

    private fun resolveHostname(ip: String): String? = try {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name == ip) null else name
    } catch (_: Exception) {
        null
    }

    /**
     * Parses the Linux /proc/net/arp format:
     * ```
     * IP address       HW type Flags HW address            Mask     Device
     * 192.168.1.1      0x1     0x2   aa:bb:cc:dd:ee:ff     *        wlan0
     * ```
     */
    private fun parseArpTable(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = content.lines().drop(1) // skip header
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val ip = parts[0]
                val mac = parts[3]
                if (mac.contains(":") && mac != "00:00:00:00:00:00") {
                    result[ip] = mac.uppercase()
                }
            }
        }
        return result
    }
}
