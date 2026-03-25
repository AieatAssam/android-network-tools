package com.example.netswissknife.core.network.traceroute

data class TracerouteResult(
    val host: String,
    val resolvedIp: String?,
    val hops: List<HopResult>,
    val rawOutput: String,
    val totalTimeMs: Long
) {
    val reachedDestination: Boolean
        get() = hops.any { it.ip != null && (it.ip == resolvedIp || it.hopNumber == hops.size) && it.status == HopStatus.SUCCESS }

    val geoLocatedHops: List<HopResult>
        get() = hops.filter { it.geoLocation != null }
}
