package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.topology.*
import net.aieat.netswissknife.core.network.operation.OperationSession

class TopologyDiscoveryUseCase(
    private val repository: TopologyDiscoveryRepository
) {
    operator fun invoke(params: TopologyParams): Flow<TopologyDiscoveryEvent> {
        val validation = TopologyParamsValidator.validate(params)
        if (!validation.isValid) {
            return flow {
                emit(TopologyDiscoveryEvent.Error(validation.errors.joinToString("; ")))
            }
        }
        val normalizedTarget = HostValidator.normalize(params.targetIp)
            ?: return flow {
                emit(TopologyDiscoveryEvent.Error("Target IP or hostname must be valid"))
            }
        return repository.discover(params.copy(targetIp = normalizedTarget))
    }

    operator fun invoke(params: TopologyParams, session: OperationSession): Flow<TopologyDiscoveryEvent> {
        val validation = TopologyParamsValidator.validate(params)
        if (!validation.isValid) {
            return flow {
                emit(TopologyDiscoveryEvent.Error(validation.errors.joinToString("; ")))
            }
        }
        val normalizedTarget = HostValidator.normalize(params.targetIp)
            ?: return flow {
                emit(TopologyDiscoveryEvent.Error("Target IP or hostname must be valid"))
            }
        return repository.discover(params.copy(targetIp = normalizedTarget), session)
    }
}
