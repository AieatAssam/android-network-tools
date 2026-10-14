package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingRequest
import net.aieat.netswissknife.core.network.ping.PingEngineKind
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PingUseCase(
    private val repository: PingRepository
) {
    val lastEngineUsed: StateFlow<PingEngineKind?>? get() = repository.lastEngineUsed

    operator fun invoke(params: PingParams): Flow<PingFlowResult> = execute(params, session = null)

    operator fun invoke(params: PingParams, session: OperationSession): Flow<PingFlowResult> =
        execute(params, session)

    private fun execute(params: PingParams, session: OperationSession?): Flow<PingFlowResult> {
        val trimmedHost = HostValidator.normalize(params.host) ?: params.host.trim()

        val errorInfo = validatePingCommon(
            trimmedHost,
            params.timeoutMs,
            params.intervalMs,
            params.payloadBytes,
            params.ttl
        )
            ?: if (params.count !in 1..100) validationError(
                ErrorCode.COUNT_OUT_OF_RANGE,
                "Count must be between 1 and 100",
                1,
                100,
            ) else null

        if (errorInfo != null) {
            return flow { emit(PingFlowResult.ValidationError(errorInfo)) }
        }

        val defaultOptions = params.intervalMs == 1_000 && params.payloadBytes == 56 && params.ttl == 64
        val request = PingRequest(
            host = trimmedHost,
            count = params.count,
            timeoutMs = params.timeoutMs,
            intervalMs = params.intervalMs,
            payloadBytes = params.payloadBytes,
            ttl = params.ttl
        )
        val packets = when {
            session != null -> repository.ping(request, session)
            defaultOptions -> {
                // Keep the old call shape for one release so existing integrations
                // and callers compiled against the original repository API continue
                // to work while advanced options use PingRequest below.
                repository.ping(trimmedHost, params.count, params.timeoutMs)
            }
            else -> repository.ping(request)
        }
        return packets
            .map { PingFlowResult.Packet(it) }
    }
}
