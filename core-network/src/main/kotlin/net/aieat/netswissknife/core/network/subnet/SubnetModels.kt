package net.aieat.netswissknife.core.network.subnet

/**
 * All computed information about a subnet derived from a single user input.
 */
data class SubnetInfo(
    /** The IP portion of the original input (before mask/prefix). */
    val inputIp: String,
    /** CIDR prefix length, 0–32. */
    val prefixLength: Int,
    /** Network address (host bits zeroed). */
    val networkAddress: String,
    /** Broadcast address (host bits set to 1). */
    val broadcastAddress: String,
    /** First usable host address, or network address for /32. */
    val firstHost: String,
    /** Last usable host address, or network address for /32. */
    val lastHost: String,
    /** Dotted-decimal subnet mask, e.g. "255.255.255.0". */
    val subnetMask: String,
    /** Wildcard mask (inverse of subnet mask), e.g. "0.0.0.255". */
    val wildcardMask: String,
    /** Hex representation of the subnet mask, e.g. "0xFFFFFF00". */
    val hexMask: String,
    /** Binary representation of the mask with octet dots, e.g. "11111111.11111111.11111111.00000000". */
    val binaryMask: String,
    /** Binary representation of the network address with octet dots. */
    val binaryNetworkAddress: String,
    /** Binary representation of the original input IP with octet dots. */
    val binaryIpAddress: String,
    /** Total number of addresses in the subnet (2^hostBits). */
    val totalHosts: Long,
    /** Usable host count. 0 for /31, 1 for /32, (totalHosts - 2) otherwise. */
    val usableHosts: Long,
    /** Number of host bits (32 - prefixLength). */
    val hostBits: Int,
    /** Traditional IP class: "A", "B", "C", "D", or "E". */
    val ipClass: String,
    /** True if the network falls within RFC 1918 private ranges. */
    val isPrivate: Boolean,
    /** Canonical CIDR notation of the network, e.g. "192.168.1.0/24". */
    val cidrNotation: String,
)
