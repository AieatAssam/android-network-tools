package net.aieat.netswissknife.core.network.traceroute

data class TracerouteResult(
    val host: String,
    val resolvedIp: String?,
    val hops: List<HopResult>,
    val rawOutput: String,
    val totalTimeMs: Long,
) {
    /** True only when the native traceroute engine identified a response from the destination. */
    val reachedDestination: Boolean
        get() = hops.any { it.destinationReached }

    val geoLocatedHops: List<HopResult>
        get() = hops.filter { it.geoLocation != null }
}
