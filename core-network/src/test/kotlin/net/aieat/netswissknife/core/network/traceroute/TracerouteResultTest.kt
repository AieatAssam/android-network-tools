package net.aieat.netswissknife.core.network.traceroute

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
        geo: HopGeoLocation? = null,
        destinationReached: Boolean = false,
    ) = HopResult(
        hopNumber = number,
        ip = ip,
        hostname = null,
        rtTimeMs = if (status == HopStatus.SUCCESS) 10L else null,
        status = status,
        geoLocation = geo,
        destinationReached = destinationReached,
    )

    private fun result(
        vararg hops: HopResult,
        resolvedIp: String? = null,
    ) = TracerouteResult(
        host = "example.com",
        resolvedIp = resolvedIp,
        hops = hops.toList(),
        rawOutput = "",
        totalTimeMs = 0L,
    )

    private val sampleGeo =
        HopGeoLocation(
            ip = "8.8.8.8",
            country = "United States",
            countryCode = "US",
            city = "Mountain View",
            lat = 37.39,
            lon = -122.08,
        )

    // ── reachedDestination ────────────────────────────────────────────────────

    @Nested
    @DisplayName("reachedDestination")
    inner class ReachedDestination {
        @Test
        fun `responding max hop does not prove the destination responded`() {
            val r =
                result(
                    hop(1),
                    hop(2),
                    hop(3),
                    resolvedIp = "203.0.113.9",
                )
            assertFalse(r.reachedDestination)
            assertEquals("203.0.113.9", r.resolvedIp)
        }

        @Test
        fun `returns true when native destination evidence exists in a partial out of order trace`() {
            val r =
                result(
                    hop(5, ip = null, status = HopStatus.TIMEOUT),
                    hop(3, destinationReached = true),
                    hop(1),
                )
            assertTrue(r.reachedDestination)
        }

        @Test
        fun `does not infer destination from a successful router when no target was resolved`() {
            val r =
                result(
                    hop(1),
                    hop(2),
                )
            assertFalse(r.reachedDestination)
            assertEquals(null, r.resolvedIp)
        }

        @Test
        fun `returns false when hop list is empty`() {
            val r = result()
            assertFalse(r.reachedDestination)
        }

        @Test
        fun `native destination refusal evidence counts without a successful hop`() {
            val r =
                result(
                    hop(2, ip = null, status = HopStatus.ERROR, destinationReached = true),
                    resolvedIp = "203.0.113.5",
                )
            assertTrue(r.reachedDestination)
            assertEquals("203.0.113.5", r.resolvedIp)
        }

        @Test
        fun `all timed out hops without destination evidence remain unreached`() {
            val r =
                result(
                    hop(1, ip = null, status = HopStatus.TIMEOUT),
                    hop(2, ip = null, status = HopStatus.TIMEOUT),
                )
            assertFalse(r.reachedDestination)
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
            val withGeo = hop(2, ip = "8.8.8.8", geo = sampleGeo)
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
