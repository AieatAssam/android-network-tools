package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Validates [TracerouteParams], then emits each observed hop immediately and
 * streams optional reverse-DNS and GeoIP enrichment updates under the same operation.
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
    private val geoIpRepository: GeoIpRepository,
    private val reverseDnsRepository: TracerouteReverseDnsRepository? = null,
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

        val errorInfo = when {
            trimmedHost.isBlank()                        -> validationError(ErrorCode.HOST_BLANK, "Host must not be empty")
            !HostValidator.isValidHostname(trimmedHost)  -> validationError(ErrorCode.HOST_INVALID, "Invalid host or IP address")
            params.maxHops !in 1..64                     -> validationError(ErrorCode.MAX_HOPS_OUT_OF_RANGE, "Max hops must be between 1 and 64", 1, 64)
            params.timeoutMs !in 500..30_000             -> validationError(ErrorCode.TIMEOUT_OUT_OF_RANGE, "Timeout must be between 500 ms and 30 000 ms", 500, 30_000)
            params.probesPerHop !in 1..5                 -> validationError(ErrorCode.PROBES_OUT_OF_RANGE, "Probes per hop must be between 1 and 5", 1, 5)
            params.packetSize != 0 &&
                params.packetSize !in 28..1472           -> validationError(ErrorCode.PACKET_SIZE_OUT_OF_RANGE, "Packet size must be 0 (MTU discovery) or between 28 and 1472 bytes", 0, 28, 1472)
            TracerouteOperation.requestedTimeoutMillis(
                params.maxHops,
                params.timeoutMs,
                params.probesPerHop,
                operationSession?.budget?.maxConcurrentProbes ?: TracerouteOperation.MAX_CONCURRENT_PROBES,
            ) == null ->
                validationError(ErrorCode.OPERATION_DEADLINE_EXCEEDED, "Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout")
            else                                         -> null
        }

        if (errorInfo != null) {
            return flow { emit(TracerouteFlowResult.ValidationError(errorInfo)) }
        }

        return channelFlow {
            val output = this
            val session = operationSession ?: TracerouteOperation.newSession(
                params.maxHops,
                params.timeoutMs,
                params.probesPerHop,
            )
            OperationRunner.run(session) {
                coroutineScope {
                    // Bound all in-flight network lookups together, while allowing DNS and
                    // GeoIP for an individual hop to overlap.
                    val lookups = Semaphore(
                        minOf(MAX_ENRICHMENT_CONCURRENCY, session.budget.maxConcurrentProbes),
                    )
                    tracerouteRepository.trace(
                        host = trimmedHost,
                        maxHops = params.maxHops,
                        timeoutMs = params.timeoutMs,
                        probesPerHop = params.probesPerHop,
                        probeType = params.probeType,
                        packetSize = params.packetSize,
                        operationSession = session,
                    ).collect { hop ->
                        output.send(TracerouteFlowResult.Hop(hop))
                        val hopIp = hop.ip ?: return@collect

                        launch {
                            val hostnameLookup = async {
                                if (reverseDnsRepository == null || hop.hostname != null) {
                                    hop.hostname
                                } else {
                                    lookups.withPermit {
                                        optionalEnrichment(
                                            session,
                                            TracerouteOperation.MAX_REVERSE_DNS_WAIT_MILLIS,
                                        ) {
                                            session.concurrencyLimiter.withPermit {
                                                reverseDnsRepository.lookup(hopIp, session)
                                            }
                                        }
                                    }
                                }
                            }
                            val geoLookup = async {
                                lookups.withPermit {
                                    optionalEnrichment(
                                        session,
                                        TracerouteOperation.MAX_GEO_IP_WAIT_MILLIS,
                                    ) {
                                        session.concurrencyLimiter.withPermit {
                                            geoIpRepository.lookup(hopIp, session)
                                        }
                                    }
                                }
                            }
                            output.send(
                                TracerouteFlowResult.HopEnriched(
                                    hopNumber = hop.hopNumber,
                                    hostname = hostnameLookup.await(),
                                    geoLocation = geoLookup.await(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun <T> optionalEnrichment(
        session: OperationSession,
        maximumWaitMillis: Long,
        lookup: suspend () -> T,
    ): T? {
        val remainingMillis = session.budget.remainingTimeoutMillis()
        if (session.budget.hasDeadline && remainingMillis <= 0L) {
            throw OperationDeadlineExceededException()
        }
        val waitMillis = if (session.budget.hasDeadline) {
            minOf(maximumWaitMillis, remainingMillis)
        } else {
            maximumWaitMillis
        }
        val result = try {
            withTimeoutOrNull(waitMillis.coerceAtLeast(1L)) { lookup() }
        } catch (cancelled: OperationCancellationException) {
            throw cancelled
        } catch (deadline: OperationDeadlineExceededException) {
            throw deadline
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        session.budget.throwIfExpired()
        return result
    }

    private companion object {
        const val MAX_ENRICHMENT_CONCURRENCY = 4
    }
}
