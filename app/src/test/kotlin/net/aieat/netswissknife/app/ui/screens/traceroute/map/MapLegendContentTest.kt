package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.app.ui.screens.TraceLatencyBand
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.brisbane
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.frankfurt
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.hop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.london
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.newYork
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.paris
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.timeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MapLegendContent")
class MapLegendContentTest {
    private val routes =
        listOf(
            emptyList(),
            listOf(hop(3, london)),
            listOf(hop(3, london, listOf(10L, 11L)), hop(4, paris, listOf(14L, 15L))),
            listOf(hop(3, london, listOf(10L)), hop(4, paris, listOf(14L)), hop(5, frankfurt, listOf(20L))),
            listOf(hop(3, london, listOf(10L, null)), timeout(4), hop(5, newYork, listOf(80L, 81L))),
            listOf(
                hop(2, london, listOf(5L, 6L, 5L)),
                hop(3, paris, listOf(9L, 40L, 9L)),
                hop(4, frankfurt, listOf(15L, null, null)),
                hop(5, newYork, listOf(90L, 91L, 92L)),
                hop(6, brisbane, listOf(95L, 96L, 97L), destination = true),
            ),
        )

    @Test
    fun `the legend lists exactly the line patterns and widths the map draws, on every route`() {
        listOf(false, true).forEach { stopped ->
            routes.forEach { hops ->
                val map = TraceRouteMap.from(hops, stopped)
                val legend = MapLegendContent.of(map, live = false)

                assertEquals(
                    map.segments
                        .filterNot { it.joinsDisputed }
                        .map { it.stability }
                        .toSet(),
                    legend.linePatterns.toSet(),
                )
                assertEquals(map.segments.any { it.joinsDisputed }, legend.disputedJoins)
                assertEquals(
                    map.segments
                        .filterNot { it.isUnknown }
                        .map { MapLegendContent.widthOf(it.speed) }
                        .toSet(),
                    legend.widths.toSet(),
                )
            }
        }
    }

    @Test
    fun `colours are only the latency bands of measured arcs and route markers`() {
        val map =
            TraceRouteMap.from(listOf(hop(3, london, listOf(10L, 11L)), hop(4, newYork, listOf(80L, 81L))))

        val legend = MapLegendContent.of(map, live = false)

        assertEquals(listOf(TraceLatencyBand.GOOD, TraceLatencyBand.FAIR), legend.latencyBands)
    }

    @Test
    fun `patterns come in a fixed order, and an unknown path adds no width`() {
        val map =
            TraceRouteMap.from(listOf(hop(3, london, listOf(10L, null)), timeout(4), hop(5, newYork, listOf(80L, 81L))))

        val legend = MapLegendContent.of(map, live = false)

        assertEquals(listOf(Stability.UNKNOWN), legend.linePatterns)
        assertTrue(legend.widths.isEmpty())
    }

    @Test
    fun `a single-probe trace is flagged only when its arcs show stability not measured`() {
        val single = TraceRouteMap.from(listOf(hop(3, london), hop(4, paris)))
        val partial = TraceRouteMap.from(listOf(hop(3, london, listOf(1L, 2L)), hop(4, paris, listOf(5L))))

        assertTrue(MapLegendContent.of(single, live = false).singleProbeTrace)
        assertFalse(MapLegendContent.of(partial, live = false).singleProbeTrace)
    }

    @Test
    fun `markers are listed only when drawn, the head only while live`() {
        val hops =
            listOf(
                hop(9, london, listOf(31L, 33L, 32L)),
                hop(10, london, listOf(33L, 31L, 35L)),
                hop(11, brisbane, listOf(25L, 57L, 21L), destination = true),
            )
        val map = TraceRouteMap.from(hops)

        assertEquals(
            listOf(MarkerNote.HEAD, MarkerNote.DESTINATION, MarkerNote.DISPUTED),
            MapLegendContent.of(map, live = true).markers,
        )
        assertEquals(
            listOf(MarkerNote.DESTINATION, MarkerNote.DISPUTED),
            MapLegendContent.of(map, live = false).markers,
        )
    }

    @Test
    fun `dashes to a disputed location get their own entry, only when the map draws one`() {
        val disputed =
            TraceRouteMap.from(
                listOf(hop(9, london, listOf(31L, 33L)), hop(11, brisbane, listOf(25L, 21L), destination = true)),
            )
        val plain =
            TraceRouteMap.from(listOf(hop(3, london, listOf(10L, null)), timeout(4), hop(5, newYork, listOf(80L, 81L))))

        val disputedLegend = MapLegendContent.of(disputed, live = false)
        assertTrue(disputedLegend.disputedJoins)
        // The join to the disputed stop is not listed as the generic unknown-path pattern.
        assertTrue(disputedLegend.linePatterns.isEmpty())
        assertFalse(MapLegendContent.of(plain, live = false).disputedJoins)
        assertEquals(listOf(Stability.UNKNOWN), MapLegendContent.of(plain, live = false).linePatterns)
    }

    @Test
    fun `an empty map has an empty legend`() {
        assertTrue(MapLegendContent.of(TraceRouteMap.from(emptyList()), live = true).isEmpty)
        assertFalse(MapLegendContent.of(TraceRouteMap.from(listOf(hop(3, london))), live = false).isEmpty)
    }

    @Test
    fun `speed bands map to the three drawn widths`() {
        assertEquals(ArcWidth.THICK, MapLegendContent.widthOf(SpeedBand.FAST))
        assertEquals(ArcWidth.MEDIUM, MapLegendContent.widthOf(SpeedBand.MEDIUM))
        assertEquals(ArcWidth.MEDIUM, MapLegendContent.widthOf(SpeedBand.UNKNOWN))
        assertEquals(ArcWidth.THIN, MapLegendContent.widthOf(SpeedBand.SLOW))
    }
}
