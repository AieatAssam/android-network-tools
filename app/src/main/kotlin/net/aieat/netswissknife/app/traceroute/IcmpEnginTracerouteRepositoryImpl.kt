package net.aieat.netswissknife.app.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import me.impa.icmpenguin.ProbeType
import me.impa.icmpenguin.trace.PortStrategy
import me.impa.icmpenguin.trace.ProbeSize
import me.impa.icmpenguin.trace.Response
import me.impa.icmpenguin.trace.SimpleTracer

/**
 * [TracerouteRepository] implementation powered by the **icmpenguin** library.
 *
 * Unlike the legacy binary-based approach, icmpenguin uses low-level ICMP/UDP sockets
 * via JNI and does not depend on the `traceroute` or `tracepath` system binaries.
 * This makes it work reliably on Android 16+ where those binaries have been removed.
 *
 * Coroutine integration: [SimpleTracer.trace] returns a cold [Flow] that emits one
 * [me.impa.icmpenguin.trace.HopStatus] per TTL level. We map each to our own [HopResult]
 * and enrich it with a bounded, cancellable reverse-DNS lookup on the IO dispatcher.
 */
class IcmpEnginTracerouteRepositoryImpl(
    private val nativeTraceFactory: (
        String,
        Int,
        Int,
        Int,
        TracerouteProbeType,
        Int,
        Int,
    ) -> Flow<HopResult> = ::nativeTrace,
    private val reverseDnsLookup: TracerouteReverseDnsLookup = BoundedTracerouteReverseDnsLookup(),
) : TracerouteRepository {

    override fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int,
        probeType: TracerouteProbeType,
        packetSize: Int
    ): Flow<HopResult> = flow {
        emitAll(
            trace(
                host,
                maxHops,
                timeoutMs,
                probesPerHop,
                probeType,
                packetSize,
                TracerouteOperation.newSession(maxHops, timeoutMs),
            )
        )
    }.flowOn(Dispatchers.IO)

    override fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int,
        probeType: TracerouteProbeType,
        packetSize: Int,
        operationSession: OperationSession,
    ): Flow<HopResult> = channelFlow {
        OperationRunner.runOrJoin(operationSession) {
            val nativeFlow = try {
                nativeTraceFactory(
                    host,
                    maxHops,
                    timeoutMs,
                    probesPerHop,
                    probeType,
                    packetSize,
                    nativeTraceConcurrency(probesPerHop, operationSession.budget.maxConcurrentProbes),
                )
            } catch (_: LinkageError) {
                throw NativeTracerouteUnavailableException()
            }
            nativeFlow.catch { failure ->
                    if (failure is LinkageError) throw NativeTracerouteUnavailableException()
                    throw failure
                }
                .collect { hop ->
                    val enriched = hop.ip?.let { ip ->
                        val hostname = try {
                            val remainingMillis = operationSession.budget.remainingTimeoutMillis()
                            if (operationSession.budget.hasDeadline && remainingMillis <= 0L) {
                                throw OperationDeadlineExceededException()
                            }
                            val lookupBudgetMillis = if (operationSession.budget.hasDeadline) {
                                minOf(MAX_REVERSE_DNS_WAIT_MILLIS, remainingMillis)
                            } else {
                                MAX_REVERSE_DNS_WAIT_MILLIS
                            }
                            withTimeoutOrNull(lookupBudgetMillis) {
                                reverseDnsLookup.lookup(ip, operationSession)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (deadline: OperationDeadlineExceededException) {
                            throw deadline
                        } catch (_: Exception) {
                            null
                        }
                        currentCoroutineContext().ensureActive()
                        operationSession.budget.throwIfExpired()
                        hop.copy(hostname = hostname)
                    } ?: hop
                    this@channelFlow.send(enriched)
                }
        }
    }.flowOn(Dispatchers.IO)

}

private fun nativeTrace(
    host: String,
    maxHops: Int,
    timeoutMs: Int,
    probesPerHop: Int,
    probeType: TracerouteProbeType,
    packetSize: Int,
    concurrency: Int,
): Flow<HopResult> {
    val icmpProbeType = when (probeType) {
        TracerouteProbeType.ICMP -> ProbeType.ICMP
        TracerouteProbeType.UDP -> ProbeType.UDP
    }
    val probeSize = if (packetSize == 0) ProbeSize.MtuDiscovery else ProbeSize.Static(packetSize)
    val tracer = SimpleTracer(
        host = host,
        probeType = icmpProbeType,
        timeout = timeoutMs,
        maxHops = maxHops,
        probesPerHop = probesPerHop,
        concurrency = concurrency,
        portStrategy = PortStrategy.Sequential(),
        probeSize = probeSize,
    )
    return tracer.trace().map { icmpHop ->
        val ip = icmpHop.ips.firstOrNull()
        val rttMs = icmpHop.probes
            .filterIsInstance<Response.Success>()
            .firstOrNull()
            ?.timeUsec
            ?.let { it.toLong() / 1_000L }
        val status = if (ip != null) HopStatus.SUCCESS else HopStatus.TIMEOUT
        HopResult(
            hopNumber = icmpHop.num,
            ip = ip,
            hostname = null,
            rtTimeMs = rttMs,
            status = status,
        )
    }
}

/** Keep the native worker count within requested probes, caller budget, and tool ceiling. */
internal fun nativeTraceConcurrency(probesPerHop: Int, sessionLimit: Int): Int =
    minOf(
        probesPerHop.coerceAtLeast(1),
        sessionLimit.coerceAtLeast(1),
        TracerouteOperation.MAX_CONCURRENT_PROBES,
    )

internal const val MAX_REVERSE_DNS_WAIT_MILLIS = TracerouteOperation.MAX_REVERSE_DNS_WAIT_MILLIS

/** A stable, user-displayable failure when the optional JNI traceroute engine cannot load. */
class NativeTracerouteUnavailableException : Exception(
    "Native traceroute engine is unavailable on this device."
)
