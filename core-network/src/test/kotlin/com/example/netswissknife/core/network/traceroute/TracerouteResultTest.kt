package com.example.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TracerouteResult")
class TracerouteResultTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hop(
        number: Int,
        ip: String? = "1.2.3.$number",
        status: HopStatus = HopStatus.SUCCESS,
        geo: HopGeoLocation? = null
    ) = HopResult(
        hopNumber   = number,
        ip          = ip,
        hostname    = null,
        rtTimeMs    = if (status == HopStatus.SUCCESS) 10L else null,
        status      = status,
        geoLocation = geo
    )

    private fun result(vararg hops: HopResult) = TracerouteResult(
        host        = "example.com",
        resolvedIp  = hops.lastOrNull { it.status == HopStatus.SUCCESS }?.ip,
        hops        = hops.toList(),
        rawOutput   = "",
        totalTimeMs = 0L
    )

    private val sampleGeo = HopGeoLocation(
        ip          = "8.8.8.8",
        country     = "United States",
        countryCode = "US",
        city        = "Mountain View",
        lat         = 37.39,
        lon         = -122.08
    )

    // ── reachedDestination ────────────────────────────────────────────────────

    @Nested
    @DisplayName("reachedDestination")
    inner class ReachedDestination {

        @Test
        fun `returns true when last hop responded successfully`() {
            val r = result(hop(1), hop(2), hop(3))
            assertTrue(r.reachedDestination)
        }

        @Test
        fun `returns false when last hop timed out`() {
            val r = result(
                hop(1),
                hop(2),
                hop(3, ip = null, status = HopStatus.TIMEOUT)
            )
            assertFalse(r.reachedDestination)
        }

        @Test
        fun `returns false when all hops timed out`() {
            val r = result(
                hop(1, ip = null, status = HopStatus.TIMEOUT),
                hop(2, ip = null, status = HopStatus.TIMEOUT)
            )
            assertFalse(r.reachedDestination)
        }

        @Test
        fun `returns false when hop list is empty`() {
            val r = result()
            assertFalse(r.reachedDestination)
        }

        @Test
        fun `uses highest hop number not list position to find last hop`() {
            // Hops provided out of insertion order; hop 5 is the last by hopNumber
            // and it timed out, so destination was not reached.
            val r = result(
                hop(3),
                hop(1),
                hop(5, ip = null, status = HopStatus.TIMEOUT),
                hop(2),
                hop(4)
            )
            assertFalse(r.reachedDestination)
        }

        @Test
        fun `returns true when intermediate hops time out but final hop succeeds`() {
            val r = result(
                hop(1),
                hop(2, ip = null, status = HopStatus.TIMEOUT),
                hop(3, ip = null, status = HopStatus.TIMEOUT),
                hop(4)
            )
            assertTrue(r.reachedDestination)
        }

        @Test
        fun `single successful hop is considered reached`() {
            val r = result(hop(1))
            assertTrue(r.reachedDestination)
        }

        @Test
        fun `single timeout hop is not considered reached`() {
            val r = result(hop(1, ip = null, status = HopStatus.TIMEOUT))
            assertFalse(r.reachedDestination)
        }
    }

    // ── geoLocatedHops ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("geoLocatedHops")
    inner class GeoLocatedHops {

        @Test
        fun `returns only hops that have a geoLocation`() {
            val withGeo    = hop(2, ip = "8.8.8.8", geo = sampleGeo)
            val withoutGeo = hop(1)
            val r = result(withoutGeo, withGeo)
            assertEquals(listOf(withGeo), r.geoLocatedHops)
        }

        @Test
        fun `returns empty list when no hops have geoLocation`() {
            val r = result(hop(1), hop(2))
            assertTrue(r.geoLocatedHops.isEmpty())
        }

        @Test
        fun `returns empty list when hop list is empty`() {
            val r = result()
            assertTrue(r.geoLocatedHops.isEmpty())
        }

        @Test
        fun `returns all hops when every hop has geoLocation`() {
            val h1 = hop(1, geo = sampleGeo.copy(ip = "1.1.1.1"))
            val h2 = hop(2, geo = sampleGeo.copy(ip = "2.2.2.2"))
            val r = result(h1, h2)
            assertEquals(2, r.geoLocatedHops.size)
        }
    }
}
