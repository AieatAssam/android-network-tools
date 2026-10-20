package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

interface TlsInspectorRepository {
    suspend fun inspect(host: String, port: Int, timeoutMs: Int): NetworkResult<TlsInspectorResult>

    /** Optional inspector features; legacy repository implementations may ignore them. */
    suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        options: TlsInspectorOptions,
    ): NetworkResult<TlsInspectorResult> = inspect(host, port, timeoutMs)

    /** Caller-owned operation variant; legacy implementations keep working by delegating. */
    suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): NetworkResult<TlsInspectorResult> = inspect(host, port, timeoutMs)

    /** Caller-owned operation plus optional features; defaults preserve older implementations. */
    suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
        options: TlsInspectorOptions,
    ): NetworkResult<TlsInspectorResult> = inspect(host, port, timeoutMs, operationSession)
}

data class TlsInspectorOptions(
    val probeProtocols: Boolean = false,
    /** Already validated, normalized 64-character uppercase hexadecimal fingerprint. */
    val expectedPinSha256: String? = null,
)

/** Shared TLS operation limits and per-inspection session factory. */
object TlsInspectorOperation {
    const val MIN_TIMEOUT_MILLIS = 500
    const val MAX_TIMEOUT_MILLIS = 30_000

    fun newSession(
        timeoutMs: Int,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = timeoutMs.coerceIn(MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS).toLong(),
            maxConcurrentProbes = 1,
            maxResponseBytes = OperationBudget.DEFAULT_MAX_RESPONSE_BYTES,
            clock = clock,
        )
    )
}
