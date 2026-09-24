package net.aieat.netswissknife.core.network.portscan

import net.aieat.netswissknife.core.network.operation.OperationBudget

/** Conservative deadline for a port scan's bounded connect and open-port banner waves. */
object PortScanOperationBudget {
    const val HARD_CEILING_MILLIS = 15 * 60 * 1_000L
    const val BANNER_READ_ALLOWANCE_MILLIS = 300L
    const val SETUP_AND_RESOLUTION_ALLOWANCE_MILLIS = 10_000L
    const val MAX_CONCURRENCY = 500
    const val OVER_CEILING_MESSAGE =
        "This scan may exceed the 15-minute operation limit; increase concurrency or reduce the port range or timeout."

    data class Estimate(
        val portCount: Int,
        val effectiveConcurrency: Int,
        val timeoutMillis: Long,
    ) {
        val exceedsHardCeiling: Boolean get() = timeoutMillis > HARD_CEILING_MILLIS
    }

    fun estimate(
        portCount: Int,
        timeoutMs: Int,
        requestedConcurrency: Int,
        sessionConcurrency: Int = MAX_CONCURRENCY,
    ): Estimate {
        require(portCount > 0) { "Port count must be positive" }
        require(timeoutMs > 0) { "Per-port timeout must be positive" }
        require(requestedConcurrency > 0) { "Requested concurrency must be positive" }
        require(sessionConcurrency > 0) { "Session concurrency must be positive" }

        val effectiveConcurrency = minOf(requestedConcurrency, sessionConcurrency, MAX_CONCURRENCY)
        val waves = (portCount.toLong() + effectiveConcurrency - 1) / effectiveConcurrency
        val perPortAllowance = timeoutMs.toLong() + BANNER_READ_ALLOWANCE_MILLIS
        val timeoutMillis = SETUP_AND_RESOLUTION_ALLOWANCE_MILLIS + waves * perPortAllowance
        return Estimate(portCount, effectiveConcurrency, timeoutMillis)
    }

    /** Malformed/oversized requests get finite sessions until the use case rejects them. */
    fun sessionTimeoutMillis(
        portCount: Int,
        timeoutMs: Int,
        requestedConcurrency: Int,
        sessionConcurrency: Int = MAX_CONCURRENCY,
    ): Long = estimate(
        portCount = portCount.coerceAtLeast(1),
        timeoutMs = timeoutMs.coerceAtLeast(1),
        requestedConcurrency = requestedConcurrency.coerceIn(1, MAX_CONCURRENCY),
        sessionConcurrency = sessionConcurrency.coerceIn(1, MAX_CONCURRENCY),
    ).timeoutMillis.coerceAtMost(HARD_CEILING_MILLIS)

    fun requireWithinCeiling(
        portCount: Int,
        timeoutMs: Int,
        requestedConcurrency: Int,
        sessionConcurrency: Int = MAX_CONCURRENCY,
    ): Estimate = estimate(portCount, timeoutMs, requestedConcurrency, sessionConcurrency).also {
        require(!it.exceedsHardCeiling) { OVER_CEILING_MESSAGE }
    }
}
