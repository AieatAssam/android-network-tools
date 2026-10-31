package net.aieat.netswissknife.core.network.traceroute

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cross-checks GeoIP positions against measured round-trip times.
 *
 * Light in optical fibre covers roughly 200 km per millisecond. A router that is
 * reached after an earlier hop must therefore answer no sooner than the earlier hop's
 * RTT plus the round-trip propagation between the two locations. A GeoIP position that
 * violates this bound is contradicted by the measurement (typical for anycast addresses
 * and stale registry data).
 */
object GeoPlausibility {
    /** Propagation speed used for the bound: light in fibre. */
    const val FIBRE_KM_PER_MS = 200.0

    /** Tolerance for return-path asymmetry and per-probe noise that minimum RTTs do not remove. */
    const val SLACK_MS = 10.0

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Returns the hop numbers whose geolocation contradicts the RTT of the last hop that
     * was accepted before it. Hops without a geolocation or without any successful probe
     * are not checked and do not serve as anchors. Input order does not matter.
     */
    fun implausibleHops(hops: List<HopResult>): Set<Int> {
        val implausible = mutableSetOf<Int>()
        var anchorLocation: HopGeoLocation? = null
        var anchorRttMs = 0L
        hops
            .sortedBy { it.hopNumber }
            .forEach { hop ->
                val location = hop.geoLocation ?: return@forEach
                val rttMs = hop.rttMinMs ?: hop.rtTimeMs ?: return@forEach
                val previous = anchorLocation
                if (previous != null) {
                    val minimumRttMs = anchorRttMs + roundTripPropagationMs(previous, location)
                    if (rttMs + SLACK_MS < minimumRttMs) {
                        implausible += hop.hopNumber
                        return@forEach
                    }
                }
                anchorLocation = location
                anchorRttMs = rttMs
            }
        return implausible
    }

    /** Great-circle distance between two geolocated points, in kilometres. */
    fun distanceKm(
        a: HopGeoLocation,
        b: HopGeoLocation,
    ): Double {
        val phi1 = Math.toRadians(a.lat)
        val phi2 = Math.toRadians(b.lat)
        val dPhi = Math.toRadians(b.lat - a.lat)
        val dLambda = Math.toRadians(b.lon - a.lon)
        val h = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2.0 * EARTH_RADIUS_KM * atan2(sqrt(h), sqrt(1.0 - h))
    }

    private fun roundTripPropagationMs(
        a: HopGeoLocation,
        b: HopGeoLocation,
    ): Double = 2.0 * distanceKm(a, b) / FIBRE_KM_PER_MS
}
