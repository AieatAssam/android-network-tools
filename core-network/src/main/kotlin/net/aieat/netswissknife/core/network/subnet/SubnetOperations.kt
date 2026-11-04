package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult

/** Pure subnet arithmetic shared by [SubnetCalculatorRepositoryImpl]. */
private const val SUBNET_CLASS_OCTET_BITS = 8
private const val CLASS_B_FIRST_OCTET = 128L
private const val CLASS_C_FIRST_OCTET = 192L
private const val CLASS_D_FIRST_OCTET = 224L
private const val CLASS_E_FIRST_OCTET = 240L

internal fun buildInfo(
    ipStr: String,
    ipLong: Long,
    prefix: Int,
    networkLong: Long,
    broadcastLong: Long,
): SubnetInfo {
    val maskLong = prefixToMask(prefix)
    val scope = ipv4ScopeOf(networkLong, broadcastLong)
    val totalHosts = 1L shl (IPV4_BITS - prefix)
    val noReservedAddresses = prefix >= PREFIX_POINT_TO_POINT
    val networkAddress = formatIpv4(networkLong)
    return SubnetInfo(
        inputIp = ipStr,
        prefixLength = prefix,
        networkAddress = networkAddress,
        broadcastAddress = formatIpv4(broadcastLong),
        firstHost = formatIpv4(if (noReservedAddresses) networkLong else networkLong + 1),
        lastHost = formatIpv4(if (noReservedAddresses) broadcastLong else broadcastLong - 1),
        subnetMask = formatIpv4(maskLong),
        wildcardMask = formatIpv4(maskLong.inv() and IPV4_ALL_ONES),
        hexMask = "0x%08X".format(maskLong),
        binaryMask = formatIpv4Binary(maskLong),
        binaryNetworkAddress = formatIpv4Binary(networkLong),
        binaryIpAddress = formatIpv4Binary(ipLong),
        totalHosts = totalHosts,
        usableHosts = usableHostCount(prefix, totalHosts),
        hostBits = IPV4_BITS - prefix,
        ipClass = classOf(ipLong),
        isPrivate = scope == Ipv4Scope.PRIVATE,
        scope = scope,
        cidrNotation = "$networkAddress/$prefix",
        inputIsAligned = (ipLong == networkLong),
    )
}

/** RFC 3021: /31 is point-to-point, so both addresses are usable; /32 is one host. */
internal fun usableHostCount(
    prefix: Int,
    totalHosts: Long,
): Long =
    when (prefix) {
        PREFIX_HOST -> 1L
        PREFIX_POINT_TO_POINT -> 2L
        else -> totalHosts - 2L
    }

internal fun classOf(ip: Long): String {
    val first = ip shr (IPV4_BITS - SUBNET_CLASS_OCTET_BITS)
    return when {
        first < CLASS_B_FIRST_OCTET -> "A"
        first < CLASS_C_FIRST_OCTET -> "B"
        first < CLASS_D_FIRST_OCTET -> "C"
        first < CLASS_E_FIRST_OCTET -> "D"
        else -> "E"
    }
}

internal fun membershipOf(
    address: String,
    subnet: SubnetInfo,
): SubnetMembership {
    val role =
        membershipRole(
            address = parseIpv4(address),
            network = parseIpv4(subnet.networkAddress),
            broadcast = parseIpv4(subnet.broadcastAddress),
            prefix = subnet.prefixLength,
        )
    return SubnetMembership(
        address = address,
        cidrNotation = subnet.cidrNotation,
        networkAddress = subnet.networkAddress,
        broadcastAddress = subnet.broadcastAddress,
        role = role,
    )
}

internal fun membershipRole(
    address: Long,
    network: Long,
    broadcast: Long,
    prefix: Int,
): SubnetAddressRole {
    val hasReservedAddresses = prefix < PREFIX_POINT_TO_POINT
    return when {
        address < network || address > broadcast -> SubnetAddressRole.OUTSIDE
        hasReservedAddresses && address == network -> SubnetAddressRole.NETWORK_ADDRESS
        hasReservedAddresses && address == broadcast -> SubnetAddressRole.BROADCAST_ADDRESS
        else -> SubnetAddressRole.USABLE_HOST
    }
}

/** Divides [parent] into at least [count] equal subnets; the count rounds up to a power of two. */
internal fun splitByCount(
    parent: SubnetInfo,
    count: Long,
): NetworkResult<SubnetSplit> {
    val available = 1L shl (IPV4_BITS - parent.prefixLength)
    if (count < 1L || count > available) {
        return NetworkResult.error(
            ErrorCode.SUBNET_SPLIT_COUNT_INVALID,
            developerMessage = "Split count must be between 1 and $available",
            args = listOf(available.toString()),
        )
    }
    // Smallest k with 2^k >= count; the subnet count is then 2^k.
    val extraBits = Long.SIZE_BITS - (count - 1).countLeadingZeroBits()
    return splitByPrefix(parent, parent.prefixLength + extraBits, requestedCount = count, allowSamePrefix = true)
}

internal fun splitByPrefix(
    parent: SubnetInfo,
    newPrefix: Int,
    requestedCount: Long?,
    allowSamePrefix: Boolean,
): NetworkResult<SubnetSplit> {
    val tooShort = if (allowSamePrefix) newPrefix < parent.prefixLength else newPrefix <= parent.prefixLength
    if (tooShort || newPrefix > IPV4_BITS) {
        return NetworkResult.error(
            ErrorCode.SUBNET_SPLIT_PREFIX_INVALID,
            developerMessage = "New prefix must be longer than /${parent.prefixLength} and at most /32",
        )
    }
    val subnetCount = 1L shl (newPrefix - parent.prefixLength)
    val rowCount = minOf(subnetCount, SUBNET_SPLIT_ROW_LIMIT.toLong()).toInt()
    return NetworkResult.Success(
        SubnetSplit(
            parentCidr = parent.cidrNotation,
            newPrefix = newPrefix,
            requestedCount = requestedCount,
            subnetCount = subnetCount,
            rows = splitRows(parseIpv4(parent.networkAddress), newPrefix, rowCount),
        ),
    )
}

/** The first [count] networks of size /[newPrefix] starting at [network]. */
internal fun splitRows(
    network: Long,
    newPrefix: Int,
    count: Int,
): List<SubnetRow> {
    val size = 1L shl (IPV4_BITS - newPrefix)
    return (0 until count).map { index ->
        val start = network + index * size
        SubnetRow(
            cidr = "${formatIpv4(start)}/$newPrefix",
            firstAddress = formatIpv4(start),
            lastAddress = formatIpv4(start + size - 1),
        )
    }
}
