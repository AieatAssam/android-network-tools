package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.aieat.netswissknife.core.network.topology.*

class TopologyDiscoveryUseCase(
    private val repository: TopologyDiscoveryRepository
) {
    fun invoke(params: TopologyParams): Flow<TopologyDiscoveryEvent> {
        val validation = TopologyParamsValidator.validate(params)
        if (!validation.isValid) {
            return flow {
                emit(TopologyDiscoveryEvent.Error(validation.errors.joinToString("; ")))
            }
        }
        return repository.discover(params)
    }
}
