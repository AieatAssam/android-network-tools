package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.NetworkResult

/**
 * Computes subnet information from various input notations.
 *
 * Supported input formats:
 * - CIDR:            "192.168.1.0/24"
 * - IP + prefix:     "192.168.1.0/255.255.255.0"
 * - IP + space mask: "192.168.1.0 255.255.255.0"
 * - Bare IP:         "192.168.1.1"  (treated as /32)
 *
 * IPv4 only. IPv6 input is rejected with [net.aieat.netswissknife.core.network.ErrorCode.SUBNET_IPV6_UNSUPPORTED].
 */
interface SubnetCalculatorRepository {
    fun calculate(input: String): NetworkResult<SubnetInfo>

    /**
     * Finds the smallest subnet that contains both [minIp] and [maxIp].
     * Both arguments should be bare IPv4 addresses (no prefix).
     */
    fun calculateRange(
        minIp: String,
        maxIp: String,
    ): NetworkResult<SubnetInfo>

    /** Checks whether [ip] is inside [cidr] (for example "192.168.1.0/24"). */
    fun checkMembership(
        ip: String,
        cidr: String,
    ): NetworkResult<SubnetMembership>

    /** Divides [cidr] into subnets with the given [newPrefix], listing at most [SUBNET_SPLIT_ROW_LIMIT]. */
    fun splitIntoPrefix(
        cidr: String,
        newPrefix: Int,
    ): NetworkResult<SubnetSplit>

    /**
     * Divides [cidr] into at least [count] equal subnets. The count is rounded up to a power of
     * two, which is reported as [SubnetSplit.subnetCount].
     */
    fun splitIntoCount(
        cidr: String,
        count: Long,
    ): NetworkResult<SubnetSplit>
}
