package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ping.PingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Use case that validates [PingParams] and streams [PingFlowResult]s from the
 * [PingRepository].
 *
 * Validation rules:
 * - Host must not be blank
 * - Host must be a valid hostname or IPv4 address
 * - count must be in 1..100
 * - timeoutMs must be in 100..30_000
 * - packetSize must be in 1..65_507
 */
class PingUseCase(
    private val repository: PingRepository
) {
    operator fun invoke(params: PingParams): Flow<PingFlowResult> {
        val trimmedHost = params.host.trim()

        val errorMessage: String? = when {
            trimmedHost.isBlank() ->
                "Host must not be empty"
            !HostValidator.isValidHostname(trimmedHost) ->
                "Invalid host or IP address"
            params.count !in 1..100 ->
                "Count must be between 1 and 100"
            params.timeoutMs !in 100..30_000 ->
                "Timeout must be between 100 ms and 30 000 ms"
            params.packetSize !in 1..65_507 ->
                "Packet size must be between 1 and 65 507 bytes"
            else -> null
        }

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
