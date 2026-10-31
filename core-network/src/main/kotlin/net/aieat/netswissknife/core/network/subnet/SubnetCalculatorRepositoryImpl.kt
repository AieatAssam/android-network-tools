package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult

class SubnetCalculatorRepositoryImpl : SubnetCalculatorRepository {

    override fun calculate(input: String): NetworkResult<SubnetInfo> = guarded { computeSubnet(input) }

    override fun calculateRange(
        minIp: String,
        maxIp: String,
    ): NetworkResult<SubnetInfo> = guarded { computeRange(minIp, maxIp) }

    override fun checkMembership(
        ip: String,
        cidr: String,
    ): NetworkResult<SubnetMembership> =
        when (val parsed = calculate(cidr)) {
            is NetworkResult.Error -> parsed
            is NetworkResult.Success -> guarded { NetworkResult.Success(membershipOf(ip.trim(), parsed.data)) }
        }

    override fun splitIntoPrefix(
        cidr: String,
        newPrefix: Int,
    ): NetworkResult<SubnetSplit> =
        when (val parsed = calculate(cidr)) {
            is NetworkResult.Error -> {
                parsed
            }

            is NetworkResult.Success -> {
                splitByPrefix(
                    parent = parsed.data,
                    newPrefix = newPrefix,
                    requestedCount = null,
                    allowSamePrefix = false,
                )
            }
        }

    override fun splitIntoCount(
        cidr: String,
        count: Long,
    ): NetworkResult<SubnetSplit> =
        when (val parsed = calculate(cidr)) {
            is NetworkResult.Error -> parsed
            is NetworkResult.Success -> splitByCount(parsed.data, count)
        }

    private fun computeSubnet(input: String): NetworkResult<SubnetInfo> {
        val (ipStr, prefix) = parseSubnetInput(input.trim())
        val ipLong = parseIpv4(ipStr)
        val maskLong = prefixToMask(prefix)
        val networkLong = ipLong and maskLong
        val broadcastLong = networkLong or (maskLong.inv() and IPV4_ALL_ONES)
        return NetworkResult.Success(buildInfo(ipStr, ipLong, prefix, networkLong, broadcastLong))
    }

    private fun computeRange(
        minIp: String,
        maxIp: String,
    ): NetworkResult<SubnetInfo> {
        val minLong = parseIpv4(minIp.trim())
        val maxLong = parseIpv4(maxIp.trim())
        require(minLong <= maxLong) { "Minimum IP must be ≤ maximum IP" }
        // Number of bits needed to represent the difference determines host bit count
        val hostBitsNeeded = Long.SIZE_BITS - (minLong xor maxLong).countLeadingZeroBits()
        require(hostBitsNeeded <= IPV4_BITS) { "IP range too large for a valid IPv4 subnet" }
        val prefix = IPV4_BITS - hostBitsNeeded
        // Use the masked minLong so the result is always on the network boundary
        val networkIp = formatIpv4(minLong and prefixToMask(prefix))
        return calculate("$networkIp/$prefix")
    }

    /** Turns input validation failures into typed errors; anything else is a bug and propagates. */
    private inline fun <T> guarded(block: () -> NetworkResult<T>): NetworkResult<T> =
        try {
            block()
        } catch (e: SubnetInputException) {
            NetworkResult.error(e.code, developerMessage = e.message ?: "Invalid input", cause = e, args = e.args)
        } catch (e: IllegalArgumentException) {
            NetworkResult.error(ErrorCode.SUBNET_INVALID, developerMessage = e.message ?: "Invalid input", cause = e)
        }
}
