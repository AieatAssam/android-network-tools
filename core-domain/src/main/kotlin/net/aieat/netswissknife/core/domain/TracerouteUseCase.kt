package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow

/**
 * Validates [TracerouteParams], then streams [TracerouteFlowResult]s by:
 *   1. Running the traceroute via [TracerouteRepository].
 *   2. Enriching each hop with geolocation data from [GeoIpRepository].
 *
 * Validation rules:
 *   - host must not be blank and must be a valid hostname or IPv4 address
 *   - maxHops must be in 1..64
 *   - timeoutMs must be in 500..30_000
 *   - probesPerHop must be in 1..5
 *   - packetSize must be 0 (MTU discovery) or in 28..1472
 */
class TracerouteUseCase(
    private val tracerouteRepository: TracerouteRepository,
    private val geoIpRepository: GeoIpRepository
) {
    operator fun invoke(params: TracerouteParams): Flow<TracerouteFlowResult> =
        invokeInternal(params, operationSession = null)

    /** Runs the full trace and GeoIP enrichment under the caller-owned session. */
    operator fun invoke(
        params: TracerouteParams,
        operationSession: OperationSession,
    ): Flow<TracerouteFlowResult> = invokeInternal(params, operationSession)

    private fun invokeInternal(
        params: TracerouteParams,
        operationSession: OperationSession?,
    ): Flow<TracerouteFlowResult> {
        val trimmedHost = HostValidator.normalize(params.host) ?: params.host.trim()

        val errorMessage: String? = when {
            trimmedHost.isBlank()                        -> "Host must not be empty"
            !HostValidator.isValidHostname(trimmedHost)  -> "Invalid host or IP address"
            params.maxHops !in 1..64                     -> "Max hops must be between 1 and 64"
            params.timeoutMs !in 500..30_000             -> "Timeout must be between 500 ms and 30 000 ms"
            params.probesPerHop !in 1..5                 -> "Probes per hop must be between 1 and 5"
            params.packetSize != 0 &&
                params.packetSize !in 28..1472           -> "Packet size must be 0 (MTU discovery) or between 28 and 1472 bytes"
            else                                         -> null
        }

        if (errorMessage != null) {
            return flow { emit(TracerouteFlowResult.ValidationError(errorMessage)) }
        }

        return channelFlow {
            val session = operationSession ?: TracerouteOperation.newSession()
            OperationRunner.run(session) {
                tracerouteRepository.trace(
                    host = trimmedHost,
                    maxHops = params.maxHops,
                    timeoutMs = params.timeoutMs,
                    probesPerHop = params.probesPerHop,
                    probeType = params.probeType,
                    packetSize = params.packetSize,
                    operationSession = session,
                ).collect { hop ->
                    val hopIp = hop.ip
                    // Geolocation is decorative enrichment, not the payload: a hop must
                    // still be emitted without a location if an ordinary lookup fails.
                    // Typed cancellation and the shared deadline always stop the operation.
                    val enriched = if (hopIp != null) {
                        val geo = try {
                            geoIpRepository.lookup(hopIp, session)
                        } catch (cancelled: OperationCancellationException) {
                            throw cancelled
                        } catch (deadline: OperationDeadlineExceededException) {
                            throw deadline
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        hop.copy(geoLocation = geo)
                    } else hop
                    this@channelFlow.send(TracerouteFlowResult.Hop(enriched))
                }
            }
        }
    }
}
