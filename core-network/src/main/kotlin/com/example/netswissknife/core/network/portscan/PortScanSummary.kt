package com.example.netswissknife.core.network.portscan

/**
 * Aggregated summary produced after a complete port scan session.
 *
 * @param host            The hostname / IP that was scanned.
 * @param resolvedIp      The resolved IP address, or null if resolution failed.
 * @param scannedPorts    The list of port numbers that were scanned.
 * @param openPorts       Count of ports with [PortStatus.OPEN].
 * @param closedPorts     Count of ports with [PortStatus.CLOSED].
 * @param filteredPorts   Count of ports with [PortStatus.FILTERED].
 * @param scanDurationMs  Total wall-clock time for the scan.
 * @param results         The individual [PortScanResult] for every scanned port.
 */
data class PortScanSummary(
    val host: String,
    val resolvedIp: String?,
    val scannedPorts: List<Int>,
    val openPorts: Int,
    val closedPorts: Int,
    val filteredPorts: Int,
    val scanDurationMs: Long,
    val results: List<PortScanResult>
) {
    val totalPorts: Int get() = scannedPorts.size
}
