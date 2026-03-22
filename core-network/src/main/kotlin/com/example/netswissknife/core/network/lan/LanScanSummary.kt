package com.example.netswissknife.core.network.lan

/**
 * Final summary produced after a complete LAN scan.
 *
 * @param subnet          The scanned subnet in CIDR notation.
 * @param totalScanned    Total number of IP addresses probed.
 * @param aliveHosts      Number of responsive hosts found.
 * @param scanDurationMs  Wall-clock duration of the scan in milliseconds.
 * @param hosts           Discovered hosts sorted by IP address.
 */
data class LanScanSummary(
    val subnet: String,
    val totalScanned: Int,
    val aliveHosts: Int,
    val scanDurationMs: Long,
    val hosts: List<LanHost>,
)
