package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock

/** Shared bounded policy for a traceroute and all per-hop enrichment it performs. */
object TracerouteOperation {
    /** A deliberately generous ceiling that still bounds a single interactive trace. */
    const val MAX_TIMEOUT_MILLIS = 20 * 60_000L
    const val MAX_CONCURRENT_PROBES = 5
    const val MAX_REVERSE_DNS_WAIT_MILLIS = 1_000L
    const val MAX_GEO_IP_WAIT_MILLIS = 5_000L
    const val MAX_HOST_RESOLUTION_WAIT_MILLIS = 5_000L

    /** Includes probe waves, bounded name resolution, reverse DNS, and optional GeoIP enrichment. */
    fun requestedTimeoutMillis(
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int = 1,
        maxConcurrentProbes: Int = MAX_CONCURRENT_PROBES,
    ): Long? {
        if (maxHops !in 1..64 || timeoutMs !in 500..30_000 || probesPerHop !in 1..5 ||
            maxConcurrentProbes <= 0
        ) return null
        val effectiveConcurrency = minOf(probesPerHop, maxConcurrentProbes, MAX_CONCURRENT_PROBES)
        val probeWaves = (probesPerHop + effectiveConcurrency - 1) / effectiveConcurrency
        val perHop = timeoutMs.toLong() * probeWaves +
            MAX_REVERSE_DNS_WAIT_MILLIS + MAX_GEO_IP_WAIT_MILLIS
        val requested = MAX_HOST_RESOLUTION_WAIT_MILLIS + maxHops.toLong() * perHop
        return requested.takeIf { it <= MAX_TIMEOUT_MILLIS }
    }

    fun newSession(
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int = 1,
        maxConcurrentProbes: Int = MAX_CONCURRENT_PROBES,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession {
        val budgetMillis = requireNotNull(
            requestedTimeoutMillis(maxHops, timeoutMs, probesPerHop, maxConcurrentProbes),
        ) {
            "Traceroute request exceeds the 20-minute operation limit"
        }
        val concurrencyLimit = minOf(maxConcurrentProbes, MAX_CONCURRENT_PROBES)
        return OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = budgetMillis,
                maxConcurrentProbes = concurrencyLimit,
                clock = clock,
            ),
        )
    }
}
