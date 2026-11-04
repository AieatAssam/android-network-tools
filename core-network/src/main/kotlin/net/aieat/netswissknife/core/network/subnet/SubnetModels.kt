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
    /** First usable host address; the network address for /31 and /32 (no reserved addresses). */
    val firstHost: String,
    /** Last usable host address; the broadcast address for /31 and the address itself for /32. */
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
    /**
     * Usable host count. 2 for /31 (RFC 3021 point-to-point, both addresses usable),
     * 1 for /32, and (totalHosts - 2) otherwise.
     */
    val usableHosts: Long,
    /** Number of host bits (32 - prefixLength). */
    val hostBits: Int,
    /** Traditional IP class: "A", "B", "C", "D", or "E". */
    val ipClass: String,
    /** True if the whole network sits inside RFC 1918 private space. Same as scope == PRIVATE. */
    val isPrivate: Boolean,
    /** Scope of the whole network range, see [Ipv4Scope]. */
    val scope: Ipv4Scope,
    /** Canonical CIDR notation of the network, e.g. "192.168.1.0/24". */
    val cidrNotation: String,
    /**
     * True if the original input IP is already on the network boundary (host bits all zero).
     * False when the user typed e.g. "10.0.71.0/23" but the real network is "10.0.70.0/23".
     * Cloud providers like Azure require subnet addresses to be on the network boundary.
     */
    val inputIsAligned: Boolean,
)

/** Where an address sits relative to a subnet. */
enum class SubnetAddressRole { OUTSIDE, NETWORK_ADDRESS, BROADCAST_ADDRESS, USABLE_HOST }

/** Answer to "is this address inside this subnet?" with the subnet bounds for context. */
data class SubnetMembership(
    val address: String,
    val cidrNotation: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val role: SubnetAddressRole,
) {
    val inSubnet: Boolean get() = role != SubnetAddressRole.OUTSIDE
}

/** One network produced by an equal split. */
data class SubnetRow(
    val cidr: String,
    val firstAddress: String,
    val lastAddress: String,
)

/**
 * Result of dividing a network into equal subnets. [rows] holds at most
 * [SUBNET_SPLIT_ROW_LIMIT] entries; [subnetCount] is the full count.
 */
data class SubnetSplit(
    val parentCidr: String,
    val newPrefix: Int,
    /** The count the user asked for in count mode, or null when a prefix was chosen. */
    val requestedCount: Long?,
    val subnetCount: Long,
    val rows: List<SubnetRow>,
) {
    val truncated: Boolean get() = subnetCount > rows.size
}

const val SUBNET_SPLIT_ROW_LIMIT = 256
