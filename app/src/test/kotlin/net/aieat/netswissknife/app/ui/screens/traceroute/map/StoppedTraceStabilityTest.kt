package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.brisbane
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.frankfurt
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.hop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.london
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.paris
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TraceRouteMap on stopped and partial traces")
class StoppedTraceStabilityTest {
    @Nested
    @DisplayName("line pattern")
    inner class Pattern {
        private fun stabilityOf(
            probes: List<Long?>,
            expected: Int = probes.size,
            cutShort: Boolean = false,
        ) = RouteClassification.stability(ProbeStats.of(hop(9, probes = probes)), 0, expected, cutShort)

        @Test
        fun `a hop that has reported fewer probes than the trace sends is not measured yet`() {
            assertEquals(Stability.NOT_MEASURED, stabilityOf(listOf(40L, 41L), expected = 3))
            assertEquals(Stability.NOT_MEASURED, stabilityOf(listOf(40L, null), expected = 3))
        }

        @Test
        fun `a reply that may have been cut short is not counted as lost`() {
            assertEquals(Stability.NOT_MEASURED, stabilityOf(listOf(40L, null, null), cutShort = true))
            // The same replies in a finished trace are a real loss.
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(40L, null, null)))
        }

        @Test
        fun `high jitter among the replies that came is still unstable when the rest were cut short`() {
            assertEquals(Stability.UNSTABLE, stabilityOf(listOf(40L, 90L, null), cutShort = true))
        }

        @Test
        fun `a hop with every reply is judged as usual`() {
            assertEquals(Stability.STABLE, stabilityOf(listOf(40L, 41L, 42L), cutShort = true))
        }
    }

    @Nested
    @DisplayName("whole route")
    inner class Route {
        private fun TraceRouteMap.lastStability() = segments.last().stability

        private val finished =
            listOf(
                hop(2, london, listOf(5L, 6L, 5L)),
                hop(3, paris, listOf(9L, 10L, 9L)),
                hop(4, frankfurt, listOf(15L, null, null)),
            )

        @Test
        fun `stopping the trace turns the last hop's dotted arc into stability not measured`() {
            assertEquals(Stability.UNSTABLE, TraceRouteMap.from(finished).lastStability())

            val stopped = TraceRouteMap.from(finished, stopped = true)

            assertEquals(listOf(Stability.STABLE, Stability.NOT_MEASURED), stopped.segments.map { it.stability })
        }

        @Test
        fun `in a stopped trace a lost probe followed by a reply is still a loss`() {
            val earlyLoss =
                listOf(
                    hop(2, london, listOf(5L, 6L, 5L)),
                    hop(3, paris, listOf(9L, null, 9L)),
                    hop(4, frankfurt, listOf(15L, 16L, 15L)),
                )

            val stopped = TraceRouteMap.from(earlyLoss, stopped = true)

            assertEquals(listOf(Stability.UNSTABLE, Stability.STABLE), stopped.segments.map { it.stability })
        }

        @Test
        fun `a hop still collecting probes while the trace runs is drawn as not measured`() {
            val running = finished.dropLast(1) + hop(4, frankfurt, listOf(15L))

            assertEquals(Stability.NOT_MEASURED, TraceRouteMap.from(running).lastStability())
            assertTrue(TraceRouteMap.from(running).stabilityMeasurable)
        }
    }

    @Nested
    @DisplayName("anycast destination")
    inner class Anycast {
        @Test
        fun `a destination placed in Brisbane 25 ms after London is disputed, hollow and joined by no arc`() {
            val hops =
                listOf(
                    hop(9, london, listOf(31L, 33L, 32L)),
                    hop(10, london, listOf(33L, 31L, 35L)),
                    hop(11, brisbane, listOf(25L, 57L, 21L), destination = true),
                )

            val map = TraceRouteMap.from(hops)

            assertEquals(listOf(listOf(9, 10)), map.stops.map { it.hopNumbers })
            assertTrue(map.segments.isEmpty())
            val disputed = map.disputedStops.single()
            assertEquals(listOf(11), disputed.hopNumbers)
            assertTrue(disputed.disputed)
            assertTrue(disputed.isDestination)
            assertEquals(listOf(11), map.offRoute.disputed)
            assertTrue(map.offRoute.notLocated.isEmpty())
        }
    }
}
