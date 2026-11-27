package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepository
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import net.aieat.netswissknife.core.network.subnet.SubnetMembership
import net.aieat.netswissknife.core.network.subnet.SubnetSplit

class SubnetCalculatorUseCase(
    private val repository: SubnetCalculatorRepository,
) {
    suspend operator fun invoke(params: String): NetworkResult<SubnetInfo> = repository.calculate(params)

    /**
     * Synchronous check of a CIDR entry for as-you-type validation (V6B-02). Returns the typed input
     * error, or null when the entry calculates.
     */
    fun cidrInputError(params: String): NetworkResult.Error? = repository.calculate(params) as? NetworkResult.Error

    /**
     * Synchronous check of a range entry as the user types. Returns the typed error only when the
     * minimum is above the maximum; other problems are reported when the range is calculated.
     */
    fun rangeOrderError(
        minIp: String,
        maxIp: String,
    ): NetworkResult.Error? =
        (repository.calculateRange(minIp, maxIp) as? NetworkResult.Error)
            ?.takeIf { it.info?.code == ErrorCode.SUBNET_RANGE_INVERTED }

    suspend fun invokeRange(
        minIp: String,
        maxIp: String,
    ): NetworkResult<SubnetInfo> = repository.calculateRange(minIp, maxIp)

    suspend fun checkMembership(
        ip: String,
        cidr: String,
    ): NetworkResult<SubnetMembership> = repository.checkMembership(ip, cidr)

    suspend fun splitIntoPrefix(
        cidr: String,
        newPrefix: Int,
    ): NetworkResult<SubnetSplit> = repository.splitIntoPrefix(cidr, newPrefix)

    suspend fun splitIntoCount(
        cidr: String,
        count: Long,
    ): NetworkResult<SubnetSplit> = repository.splitIntoCount(cidr, count)
}
