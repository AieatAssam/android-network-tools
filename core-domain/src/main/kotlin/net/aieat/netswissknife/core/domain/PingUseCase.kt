package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingRequest
import net.aieat.netswissknife.core.network.ping.PingEngineKind
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PingUseCase(
    private val repository: PingRepository
) {
    val lastEngineUsed: StateFlow<PingEngineKind?>? get() = repository.lastEngineUsed
    operator fun invoke(params: PingParams): Flow<PingFlowResult> {
        val trimmedHost = HostValidator.normalize(params.host) ?: params.host.trim()

        val errorMessage: String? = validatePingCommon(
            trimmedHost,
            params.timeoutMs,
            params.intervalMs,
            params.payloadBytes,
            params.ttl
        )
            ?: if (params.count !in 1..100) "Count must be between 1 and 100" else null

        if (errorMessage != null) {
            return flow { emit(PingFlowResult.ValidationError(errorMessage)) }
        }

        val packets = if (params.intervalMs == 1_000 && params.payloadBytes == 56 && params.ttl == 64) {
            // Keep the old call shape for one release so existing integrations
            // and callers compiled against the original repository API continue
            // to work while advanced options use PingRequest below.
            repository.ping(trimmedHost, params.count, params.timeoutMs)
        } else {
            repository.ping(
                PingRequest(
                    host = trimmedHost,
                    count = params.count,
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
