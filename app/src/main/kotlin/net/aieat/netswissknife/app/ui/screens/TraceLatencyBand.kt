package net.aieat.netswissknife.app.ui.screens

private const val FAIR_FROM_MS = 50L
private const val SLOW_FROM_MS = 150L
private const val CRITICAL_FROM_MS = 300L

/**
 * Latency bands for a traceroute hop's RTT. This is the only place the thresholds are
 * defined: the hop list badge, the route map arcs and the map legend all derive from it,
 * so the three can never disagree.
 *
 * @property lowerBoundMs smallest RTT in the band, or null for [UNKNOWN].
 * @property upperBoundMs first RTT above the band, or null when the band is open-ended.
 */
enum class TraceLatencyBand(
    val lowerBoundMs: Long?,
    val upperBoundMs: Long?,
) {
    UNKNOWN(null, null),
    GOOD(0L, FAIR_FROM_MS),
    FAIR(FAIR_FROM_MS, SLOW_FROM_MS),
    SLOW(SLOW_FROM_MS, CRITICAL_FROM_MS),
    CRITICAL(CRITICAL_FROM_MS, null),
    ;

    companion object {
        /** Band for [rttMs]; null (no reply) is [UNKNOWN]. */
        fun of(rttMs: Long?): TraceLatencyBand =
            when {
                rttMs == null -> UNKNOWN
                rttMs < FAIR_FROM_MS -> GOOD
                rttMs < SLOW_FROM_MS -> FAIR
                rttMs < CRITICAL_FROM_MS -> SLOW
                else -> CRITICAL
            }
    }
}
