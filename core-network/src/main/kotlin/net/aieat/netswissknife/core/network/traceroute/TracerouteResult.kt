package net.aieat.netswissknife.core.network.traceroute

data class TracerouteResult(
    val host: String,
    val resolvedIp: String?,
    val hops: List<HopResult>,
    val rawOutput: String,
    val totalTimeMs: Long
) {
    /**
     * True when the highest-numbered hop received a successful response, which is the
     * standard traceroute definition of "reached the destination".
     *
     * The previous implementation compared hopNumber to hops.size (count), which fails
     * when hops arrive out of order or when the list is sparse (e.g. timeouts skipped).
     * Comparing by IP against resolvedIp is also unreliable because resolvedIp is itself
     * derived from the last successful hop, making the predicate a tautology.
     */
    val reachedDestination: Boolean
        get() = hops.maxByOrNull { it.hopNumber }?.status == HopStatus.SUCCESS

    val geoLocatedHops: List<HopResult>
        get() = hops.filter { it.geoLocation != null }
}
