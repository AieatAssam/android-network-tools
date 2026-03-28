package net.aieat.netswissknife.core.network.lan

/**
 * Represents a single host discovered during a LAN scan.
 *
 * @param ip          IPv4 address of the host.
 * @param hostname    Reverse-DNS hostname, or null if unresolvable.
 * @param macAddress  MAC address from the ARP table (uppercase colon-separated), or null.
 * @param vendor      OUI vendor name derived from [macAddress], or null.
 * @param openPorts   List of TCP ports that responded during quick scan.
 * @param pingTimeMs  Round-trip time in milliseconds for the reachability probe.
 * @param isGateway   True if this host is likely the default gateway (lowest host IP).
 */
data class LanHost(
    val ip: String,
    val hostname: String?,
    val macAddress: String?,
    val vendor: String?,
    val openPorts: List<Int>,
    val pingTimeMs: Long,
    val isGateway: Boolean = false,
)
