package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.aieat.netswissknife.core.network.ping.PingRepository

class ContinuousPingUseCase(private val repository: PingRepository) {

    operator fun invoke(params: ContinuousPingParams): Flow<PingFlowResult> {
        val trimmedHost = params.host.trim()
        val error = validatePingCommon(trimmedHost, params.timeoutMs, params.packetSize)
        if (error != null) return flow { emit(PingFlowResult.ValidationError(error)) }
        return repository
            .continuousPing(host = trimmedHost, timeoutMs = params.timeoutMs, packetSize = params.packetSize)
            .map { PingFlowResult.Packet(it) }
    }
}
