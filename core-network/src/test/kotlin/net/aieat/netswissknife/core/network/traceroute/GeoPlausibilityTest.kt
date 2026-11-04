package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GeoPlausibility")
class GeoPlausibilityTest {
    private val london = geo("192.0.2.1", 51.5074, -0.1278)
    private val paris = geo("192.0.2.2", 48.8566, 2.3522)
    private val brisbane = geo("1.1.1.1", -27.4698, 153.0251)

    private fun geo(
        ip: String,
        lat: Double,
        lon: Double,
    ): HopGeoLocation =
        HopGeoLocation(
            ip = ip,
            country = "Country",
            countryCode = "XX",
            city = "City",
            lat = lat,
            lon = lon,
        )

    private fun hop(
        number: Int,
        rttMs: Long?,
        location: HopGeoLocation?,
    ): HopResult =
        HopResult(
            hopNumber = number,
            ip = location?.ip ?: "10.0.0.$number",
            hostname = null,
            rtTimeMs = rttMs,
            status = if (rttMs == null) HopStatus.TIMEOUT else HopStatus.SUCCESS,
            geoLocation = location,
            probeRttsMs = listOf(rttMs),
        )

    @Test
    fun `flags a location that is farther than the measured round trip allows`() {
        // London at 15 ms, then a hop placed in Brisbane (about 17,000 km away) at 13 ms.
        val hops = listOf(hop(10, 15L, london), hop(11, 13L, brisbane))

        assertEquals(setOf(11), GeoPlausibility.implausibleHops(hops))
    }

    @Test
    fun `accepts a nearby location whose round trip covers the propagation delay`() {
        val hops = listOf(hop(1, 15L, london), hop(2, 20L, paris))

        assertTrue(GeoPlausibility.implausibleHops(hops).isEmpty())
    }

    @Test
    fun `ignores hops without a geolocation or without an RTT`() {
        val hops =
            listOf(
                hop(1, 15L, london),
                hop(2, null, brisbane),
                hop(3, 13L, null),
                hop(4, 20L, paris),
            )

        assertTrue(GeoPlausibility.implausibleHops(hops).isEmpty())
    }

    @Test
    fun `compares later hops with the last plausible hop rather than an implausible one`() {
        // Hop 2 is implausible and must not become the anchor for hop 3.
        val hops = listOf(hop(1, 15L, london), hop(2, 13L, brisbane), hop(3, 20L, paris))

        assertEquals(setOf(2), GeoPlausibility.implausibleHops(hops))
    }

    @Test
    fun `result does not depend on the order of the input list`() {
        val hops = listOf(hop(1, 15L, london), hop(2, 13L, brisbane), hop(3, 20L, paris))

        assertEquals(GeoPlausibility.implausibleHops(hops), GeoPlausibility.implausibleHops(hops.reversed()))
    }

    @Test
    fun `great circle distance between London and Brisbane is about 16,500 km`() {
        val km = GeoPlausibility.distanceKm(london, brisbane)

        assertTrue(km in 16_400.0..16_700.0, "was $km")
    }
}
