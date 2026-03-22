package com.example.netswissknife.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

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
 *  2. Probes each IP for reachability using [hostChecker] (concurrent batches).
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
) : LanScanRepository {

    companion object {
        val DEFAULT_HOST_CHECKER: HostChecker = { ip, timeoutMs ->
            try {
                val addr = InetAddress.getByName(ip)
                val start = System.currentTimeMillis()
                if (addr.isReachable(timeoutMs)) System.currentTimeMillis() - start else null
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
        val startTime = System.currentTimeMillis()
        val ips = SubnetUtils.parseSubnet(subnet)
        val totalCount = ips.size
        val mutex = Mutex()
        var scannedCount = 0
        val aliveHosts = mutableListOf<LanHost>()

        // Read the ARP table once upfront for MAC resolution
        val arpMap = parseArpTable(arpTableReader())

        // Detect gateway: the lowest host IP is typically the router
        val gatewayIp = ips.firstOrNull()

        val effectiveConcurrency = concurrency.coerceIn(1, 500)

        for (chunk in ips.chunked(effectiveConcurrency)) {
            coroutineScope {
                val deferreds = chunk.map { ip ->
                    async(Dispatchers.IO) {
                        val pingMs = hostChecker(ip, timeoutMs)
                        val currentCount: Int
                        mutex.withLock {
                            scannedCount++
                            currentCount = scannedCount
                        }
                        if (pingMs != null) {
                            val host = buildHost(ip, pingMs, arpMap, gatewayIp, timeoutMs)
                            Pair(host, currentCount)
                        } else {
                            Pair(null, currentCount)
                        }
                    }
                }

                for (deferred in deferreds) {
                    val (host, count) = deferred.await()
                    if (host != null) {
                        mutex.withLock { aliveHosts.add(host) }
                        emit(LanScanUpdate.HostFound(host, count, totalCount))
                    } else {
                        emit(LanScanUpdate.ScanProgress(count, totalCount))
                    }
                }
            }
        }

        val summary = LanScanSummary(
            subnet = subnet,
            totalScanned = totalCount,
            aliveHosts = aliveHosts.size,
            scanDurationMs = System.currentTimeMillis() - startTime,
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
