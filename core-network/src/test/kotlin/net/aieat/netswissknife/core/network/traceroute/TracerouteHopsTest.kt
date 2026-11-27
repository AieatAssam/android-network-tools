package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Traceroute hop rows")
class TracerouteHopsTest {
    private val paris = HopGeoLocation("192.0.2.4", "France", "FR", "Paris", 48.85, 2.35)

    @Nested
    @DisplayName("merging probe updates")
    inner class Merge {
        @Test
        fun `a later report for the same TTL replaces its row instead of adding one`() {
            val first = reply(4, probes = listOf(10L))
            val second = reply(4, probes = listOf(10L, 12L))

            val hops = TracerouteHops.merge(TracerouteHops.merge(emptyList(), first), second)

            assertEquals(listOf(second), hops)
        }

        @Test
        fun `lookups already made for the same address survive a later probe report`() {
            val enriched = reply(4, probes = listOf(10L)).copy(hostname = "edge.example", geoLocation = paris)

            val hops = TracerouteHops.merge(listOf(enriched), reply(4, probes = listOf(10L, 12L)))

            assertEquals("edge.example", hops.single().hostname)
            assertEquals(paris, hops.single().geoLocation)
            assertEquals(listOf(10L, 12L), hops.single().probeRttsMs)
        }

        @Test
        fun `lookups for a different address are not carried over`() {
            val enriched = reply(4).copy(hostname = "edge.example", geoLocation = paris)

            val hops = TracerouteHops.merge(listOf(enriched), reply(4).copy(ip = "192.0.2.99"))

            assertEquals(null, hops.single().hostname)
            assertEquals(null, hops.single().geoLocation)
        }

        @Test
        fun `rows stay in TTL order whatever order the replies arrive in`() {
            val hops = listOf(3, 1, 2).fold(emptyList<HopResult>()) { acc, n -> TracerouteHops.merge(acc, reply(n)) }

            assertEquals(listOf(1, 2, 3), hops.map { it.hopNumber })
        }
    }

    @Nested
    @DisplayName("the route up to the destination")
    inner class Ordered {
        @Test
        fun `hops past the first TTL the destination answered are not part of the route`() {
            val hops =
                listOf(
                    reply(1),
                    reply(3, destination = true),
                    reply(2, destination = true),
                    reply(4, destination = true),
                )

            assertEquals(listOf(1, 2), TracerouteHops.ordered(hops).map { it.hopNumber })
        }

        @Test
        fun `a pending TTL is not invented while the trace runs`() {
            val hops = listOf(reply(1), reply(3))

            assertEquals(listOf(1, 3), TracerouteHops.ordered(hops).map { it.hopNumber })
        }

        @Test
        fun `a finished trace has a row for every TTL up to the destination`() {
            // Seen on a device: hop 7 timed out after the destination at hop 11 had already answered.
            val hops = (1..6).map { reply(it) } + (8..10).map { reply(it) } + reply(11, destination = true)

            val complete = TracerouteHops.complete(hops)

            assertEquals((1..11).toList(), complete.map { it.hopNumber })
            assertEquals(null, complete[6].ip)
            assertEquals(HopStatus.TIMEOUT, complete[6].status)
            assertEquals(11, TracerouteHops.probedHopCount(complete))
        }

        @Test
        fun `the hop count is the destination's TTL, not the number of rows received`() {
            val hops = listOf(reply(1), reply(2), reply(4, destination = true))

            assertEquals(4, TracerouteHops.probedHopCount(hops))
            assertEquals(0, TracerouteHops.probedHopCount(emptyList()))
        }
    }

    @Nested
    @DisplayName("deciding when the trace is finished")
    inner class Completion {
        @Test
        fun `a reply for an earlier TTL that arrives after the destination is still waited for`() {
            val completion = TraceCompletion(probesPerHop = 1, localAddresses = emptySet())

            listOf(1, 2, 3).forEach { assertTrue(completion.accept(reply(it))) }
            assertTrue(completion.accept(reply(5, destination = true)))
            assertFalse(completion.isFinished)

            assertTrue(completion.accept(timeout(4)))
            assertTrue(completion.isFinished)
        }

        @Test
        fun `probes for TTLs past the destination are dropped`() {
            val completion = TraceCompletion(probesPerHop = 1, localAddresses = emptySet())
            completion.accept(reply(1))
            completion.accept(reply(2, destination = true))

            assertFalse(completion.accept(reply(3, destination = true)))
        }

        @Test
        fun `a destination reply on a lower TTL than the first one seen moves the end of the route`() {
            val completion = TraceCompletion(probesPerHop = 1, localAddresses = emptySet())
            completion.accept(reply(1))
            completion.accept(reply(3, destination = true))
            assertFalse(completion.isFinished)

            completion.accept(reply(2, destination = true))

            assertTrue(completion.isFinished)
        }

        @Test
        fun `every probe of an earlier TTL is waited for`() {
            val completion = TraceCompletion(probesPerHop = 3, localAddresses = emptySet())
            completion.accept(reply(1, probes = listOf(1L, 1L, 1L)))
            completion.accept(reply(2, probes = listOf(5L)))
            completion.accept(reply(3, probes = listOf(9L), destination = true))
            assertFalse(completion.isFinished)

            completion.accept(reply(2, probes = listOf(5L, null, 6L)))

            assertTrue(completion.isFinished)
        }

        @Test
        fun `replies that never leave this device still end the trace early`() {
            val device = "10.0.0.5"
            val completion = TraceCompletion(probesPerHop = 1, localAddresses = setOf(device))

            completion.accept(reply(1).copy(ip = device))
            assertFalse(completion.isFinished)
            completion.accept(reply(2).copy(ip = device))

            assertTrue(completion.isFinished)
        }

        @Test
        fun `a trace without a destination reply is not finished by the policy`() {
            val completion = TraceCompletion(probesPerHop = 1, localAddresses = emptySet())
            (1..5).forEach { completion.accept(reply(it)) }

            assertFalse(completion.isFinished)
        }
    }

    private fun reply(
        number: Int,
        probes: List<Long?> = listOf(5L),
        destination: Boolean = false,
    ) = HopResult(
        hopNumber = number,
        ip = "192.0.2.$number",
        hostname = null,
        rtTimeMs = probes.firstOrNull { it != null },
        status = HopStatus.SUCCESS,
        probeRttsMs = probes,
        destinationReached = destination,
    )

    private fun timeout(number: Int): HopResult {
        val noReply: Long? = null
        return HopResult(number, null, null, null, HopStatus.TIMEOUT, probeRttsMs = listOf(noReply))
    }
}
