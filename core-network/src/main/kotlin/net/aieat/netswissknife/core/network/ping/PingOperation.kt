package net.aieat.netswissknife.core.network.ping

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Shared operation limits for finite and user-stopped continuous ping sessions. */
object PingOperation {
    const val DEFAULT_FINITE_DURATION_MILLIS = OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS
    const val MAX_CONCURRENT_PROBES = 1
    const val MAX_ENGINE_ATTEMPTS = 2
    private const val RESOLUTION_AND_FALLBACK_GRACE_MILLIS = 10_000L

    fun newSession(continuous: Boolean = false): OperationSession {
        val budget = if (continuous) {
            OperationBudget.startUnbounded(
                requirement = OperationRequirement.ANY_NETWORK,
                maxConcurrentProbes = MAX_CONCURRENT_PROBES,
            )
        } else {
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = DEFAULT_FINITE_DURATION_MILLIS,
                maxConcurrentProbes = MAX_CONCURRENT_PROBES,
            )
        }
        return OperationSession(budget)
    }

    /** Creates a finite operation budget that covers the caller's selected packet count. */
    fun newSession(request: PingRequest): OperationSession {
        if (request.count == 0) return newSession(continuous = true)

        val perProbeMillis = saturatingAdd(
            request.timeoutMs.coerceAtLeast(1).toLong(),
            request.intervalMs.coerceAtLeast(0).toLong(),
        )
        val probeWindowMillis = saturatingMultiply(
            perProbeMillis,
            request.count.coerceAtLeast(1).toLong(),
        )
        val fallbackMillis = request.timeoutMs.coerceAtLeast(1).toLong()
        val perEngineMillis = saturatingAdd(probeWindowMillis, fallbackMillis)
        val requestedDurationMillis = saturatingAdd(
            saturatingMultiply(perEngineMillis, MAX_ENGINE_ATTEMPTS.toLong()),
            RESOLUTION_AND_FALLBACK_GRACE_MILLIS,
        )

        val budget = OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = requestedDurationMillis.coerceAtLeast(DEFAULT_FINITE_DURATION_MILLIS),
            maxConcurrentProbes = MAX_CONCURRENT_PROBES,
        )
        return OperationSession(budget)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

    private fun saturatingMultiply(left: Long, right: Long): Long =
        if (left != 0L && right > Long.MAX_VALUE / left) Long.MAX_VALUE else left * right
}
