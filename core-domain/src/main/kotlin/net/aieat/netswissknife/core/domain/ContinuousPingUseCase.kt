package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingRequest

class ContinuousPingUseCase(private val repository: PingRepository) {

    operator fun invoke(params: ContinuousPingParams): Flow<PingFlowResult> {
        val trimmedHost = HostValidator.normalize(params.host) ?: params.host.trim()
        val error = validatePingCommon(
            trimmedHost,
            params.timeoutMs,
            params.intervalMs,
            params.payloadBytes,
            params.ttl
        )
        if (error != null) return flow { emit(PingFlowResult.ValidationError(error)) }
        val packets = if (params.intervalMs == 1_000 && params.payloadBytes == 56 && params.ttl == 64) {
            repository.continuousPing(host = trimmedHost, timeoutMs = params.timeoutMs)
        } else {
            repository.continuousPing(
                PingRequest(
                    host = trimmedHost,
                    count = 0,
                    timeoutMs = params.timeoutMs,
                    intervalMs = params.intervalMs,
                    payloadBytes = params.payloadBytes,
                    ttl = params.ttl
                )
            )
        }
        return packets
            .map { PingFlowResult.Packet(it) }
    }
}
