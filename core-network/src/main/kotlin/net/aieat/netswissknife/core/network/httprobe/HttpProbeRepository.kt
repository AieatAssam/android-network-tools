package net.aieat.netswissknife.core.network.httprobe

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.SystemMonotonicClock

interface HttpProbeRepository {
    suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult>

    /** Caller-owned operation variant; old test and external implementations remain compatible. */
    suspend fun probe(
        request: HttpProbeRequest,
        operationSession: OperationSession,
    ): NetworkResult<HttpProbeResult> = probe(request)
}

/** Shared HTTP operation bounds and session factory. */
object HttpProbeOperation {
    const val DEFAULT_TIMEOUT_MILLIS = 15_000
    const val DEFAULT_MAX_RESPONSE_BYTES = 512_000L

    fun newSession(
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
        maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
        clock: net.aieat.netswissknife.core.network.MonotonicClock = SystemMonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = timeoutMillis.toLong(),
            maxConcurrentProbes = 1,
            maxResponseBytes = maxResponseBytes.coerceAtLeast(1L),
            clock = clock,
        )
    )

    fun newSession(
        request: HttpProbeRequest,
        clock: net.aieat.netswissknife.core.network.MonotonicClock = SystemMonotonicClock,
    ): OperationSession = newSession(request.timeoutMs, request.maxResponseBodyBytes, clock)
}
