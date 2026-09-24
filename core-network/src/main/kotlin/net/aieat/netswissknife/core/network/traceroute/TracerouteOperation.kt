package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock

/** Shared bounded policy for a traceroute and all per-hop enrichment it performs. */
object TracerouteOperation {
    /** A deliberately generous ceiling that still bounds a single interactive trace. */
    const val MAX_TIMEOUT_MILLIS = 15 * 60_000L
    const val MAX_CONCURRENT_PROBES = 5
    const val MAX_REVERSE_DNS_WAIT_MILLIS = 1_000L
    const val MAX_GEO_IP_WAIT_MILLIS = 5_000L

    /** Includes each hop's bounded DNS and optional GeoIP enrichment in the interactive budget. */
    fun requestedTimeoutMillis(maxHops: Int, timeoutMs: Int): Long? {
        if (maxHops !in 1..64 || timeoutMs !in 1..30_000) return null
        val perHop = timeoutMs.toLong() + MAX_REVERSE_DNS_WAIT_MILLIS + MAX_GEO_IP_WAIT_MILLIS
        val requested = maxHops.toLong() * perHop
        return requested.takeIf { it <= MAX_TIMEOUT_MILLIS }
    }

    fun newSession(
        maxHops: Int,
        timeoutMs: Int,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession {
        val budgetMillis = requireNotNull(requestedTimeoutMillis(maxHops, timeoutMs)) {
            "Traceroute request exceeds the 15-minute operation limit"
        }
        return OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = budgetMillis,
            maxConcurrentProbes = MAX_CONCURRENT_PROBES,
            clock = clock,
        ),
    )
    }
}
