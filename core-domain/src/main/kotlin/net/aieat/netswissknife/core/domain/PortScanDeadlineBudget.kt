package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.portscan.PortScanOperationBudget

/**
 * Derives a finite caller-owned deadline from the amount of work a port scan can perform.
 *
 * The estimate assumes every port consumes its full per-port timeout, including any banner read.
 * Work is divided into waves using the actual session concurrency. A fixed allowance covers
 * target resolution, worker setup, and flow/result delivery. The operation is rejected instead of
 * silently shortened when this conservative estimate exceeds the hard cap.
 */
object PortScanDeadlineBudget {
    const val MAX_OPERATION_TIMEOUT_MILLIS = PortScanOperationBudget.HARD_CEILING_MILLIS
    const val SETUP_AND_RESOLUTION_ALLOWANCE_MILLIS = PortScanOperationBudget.SETUP_AND_RESOLUTION_ALLOWANCE_MILLIS

    fun estimate(
        portCount: Int,
        timeoutMs: Int,
        requestedConcurrency: Int,
        sessionConcurrency: Int = PortScanOperationBudget.MAX_CONCURRENCY,
    ): PortScanOperationBudget.Estimate = PortScanOperationBudget.estimate(
        portCount, timeoutMs, requestedConcurrency, sessionConcurrency,
    )

    fun portCount(params: PortScanParams): Int = when (params.preset) {
        PortScanPreset.CUSTOM -> {
            if (params.startPort in 1..65_535 && params.endPort in params.startPort..65_535) {
                params.endPort - params.startPort + 1
            } else 0
        }
        else -> params.preset.ports.size
    }

    /** The session is still bounded for malformed input; validation rejects it before probes. */
    fun sessionTimeoutMillis(params: PortScanParams): Long {
        val count = portCount(params).coerceAtLeast(1)
        return estimate(
            portCount = count,
            timeoutMs = params.timeoutMs.coerceAtLeast(1),
            requestedConcurrency = params.concurrency.coerceIn(1, PortScanOperationBudget.MAX_CONCURRENCY),
        ).timeoutMillis.coerceAtMost(MAX_OPERATION_TIMEOUT_MILLIS)
    }
}
