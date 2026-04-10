package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepository
import net.aieat.netswissknife.core.network.subnet.SubnetInfo

class SubnetCalculatorUseCase(
    private val repository: SubnetCalculatorRepository
) {
    suspend operator fun invoke(params: String): NetworkResult<SubnetInfo> =
        repository.calculate(params)
}
