package net.aieat.netswissknife.core.network.lan

import net.aieat.netswissknife.core.network.operation.OperationBudget

/** Request-derived deadline for the sequential per-host phases of a LAN scan. */
object LanScanOperationBudget {
    const val HARD_CEILING_MILLIS = 10 * 60 * 1_000L
    const val OVER_CEILING_MESSAGE =
        "Estimated scan time exceeds the 10-minute limit. Use a smaller subnet, increase concurrency, or lower the probe timeout."

    private const val PRESENCE_PORT_COUNT = 10
    private const val QUICK_PORT_COUNT = 16
    private const val TCP_PRESENCE_TIMEOUT_CAP_MS = 400
    private const val LOCAL_NAME_TIMEOUT_CAP_MS = 300
    private const val QUICK_PORT_TIMEOUT_CAP_MS = 500
    private const val SCHEDULING_MARGIN_PERCENT = 25
    private const val FINAL_ARP_ALLOWANCE_MS = 1_000L

    data class Estimate(val targetCount: Int, val timeoutMillis: Long) {
        val exceedsHardCeiling: Boolean get() = timeoutMillis > HARD_CEILING_MILLIS
    }

    fun estimate(
        targetCount: Int,
        timeoutMs: Int,
        concurrency: Int,
        enableNameProbes: Boolean = true,
        presencePortCount: Int = PRESENCE_PORT_COUNT,
    ): Estimate {
        require(targetCount > 0)
        require(timeoutMs in 100..10_000)
        require(concurrency in 1..500)
        require(presencePortCount >= 0)
        val timeout = timeoutMs.toLong()
        val tcpPresence = minOf(timeoutMs, TCP_PRESENCE_TIMEOUT_CAP_MS).toLong() * presencePortCount
        val localNamePresence = if (enableNameProbes) {
            2L * minOf(timeoutMs, LOCAL_NAME_TIMEOUT_CAP_MS)
        } else {
            0L
        }
        val reverseDns = if (enableNameProbes) timeout else 0L
        val quickPorts = minOf(timeoutMs, QUICK_PORT_TIMEOUT_CAP_MS).toLong() * QUICK_PORT_COUNT
        val perHostAllowance = timeout + tcpPresence + localNamePresence + reverseDns + quickPorts
        val workerWaves = (targetCount.toLong() + concurrency - 1L) / concurrency
        val raw = workerWaves * perHostAllowance
        val withMargin = raw + (raw * SCHEDULING_MARGIN_PERCENT + 99L) / 100L
        return Estimate(targetCount, withMargin + FINAL_ARP_ALLOWANCE_MS)
    }

    fun estimate(
        request: LanScanRequest,
        sessionConcurrency: Int = request.concurrency,
    ): Estimate {
        require(SubnetUtils.isValidCidr(request.subnet))
        return estimate(
            targetCount = SubnetUtils.parseSubnet(request.subnet).size,
            timeoutMs = request.timeoutMs,
            concurrency = minOf(request.concurrency, sessionConcurrency),
            enableNameProbes = request.enableNameProbes,
            presencePortCount = request.presencePorts.size,
        )
    }

    fun requireWithinCeiling(
        request: LanScanRequest,
        sessionConcurrency: Int = request.concurrency,
    ): Estimate = estimate(request, sessionConcurrency).also {
        require(!it.exceedsHardCeiling) { OVER_CEILING_MESSAGE }
    }

    /** Malformed requests still get a finite session and reach the existing validation path. */
    fun sessionTimeoutMillis(request: LanScanRequest): Long {
        if (!SubnetUtils.isValidCidr(request.subnet) ||
            request.timeoutMs !in 100..10_000 || request.concurrency !in 1..500
        ) {
            return OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS
        }
        return requireWithinCeiling(request).timeoutMillis
    }
}
