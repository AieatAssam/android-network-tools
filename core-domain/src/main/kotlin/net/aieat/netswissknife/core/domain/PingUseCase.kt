package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ping.PingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PingUseCase(
    private val repository: PingRepository
) {
    operator fun invoke(params: PingParams): Flow<PingFlowResult> {
        val trimmedHost = params.host.trim()

        val errorMessage: String? = validatePingCommon(trimmedHost, params.timeoutMs, params.packetSize)
            ?: if (params.count !in 1..100) "Count must be between 1 and 100" else null

        if (errorMessage != null) {
            return flow { emit(PingFlowResult.ValidationError(errorMessage)) }
        }

        return repository
            .ping(
                host = trimmedHost,
                count = params.count,
                timeoutMs = params.timeoutMs,
                packetSize = params.packetSize
            )
            .map { PingFlowResult.Packet(it) }
    }
}
