package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.brisbane
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.hop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.london
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapFixtures.paris
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MapSelection")
class MapSelectionTest {
    private val map =
        TraceRouteMap.from(
            listOf(
                hop(3, london, listOf(10L, 11L)),
                hop(4, paris, listOf(14L, 15L)),
                hop(5, brisbane, listOf(16L, 17L)),
            ),
        )

    @Test
    fun `a cluster selection finds every stop holding its hops, disputed ones included`() {
        val stops = MapSelection.stopsOf(map, MapSelection.Stops(listOf(4, 5)))

        assertEquals(listOf(listOf(4), listOf(5)), stops.map { it.hopNumbers })
        assertEquals(listOf(false, true), stops.map { it.disputed })
    }

    @Test
    fun `a segment selection finds its segment until the route loses it`() {
        val key = SegmentKey(fromHop = 3, toHop = 4)

        assertEquals(key, MapSelection.segmentOf(map, MapSelection.Segment(key))?.key)
        assertNull(MapSelection.segmentOf(map, MapSelection.Segment(SegmentKey(fromHop = 3, toHop = 9))))
    }

    @Test
    fun `tapping the selected thing again clears it, anything else selects it`() {
        val stops = MapSelection.Stops(listOf(3))
        val segment = MapSelection.Segment(SegmentKey(3, 4))

        assertNull(MapSelection.toggle(stops, stops))
        assertEquals(segment, MapSelection.toggle(stops, segment))
        assertNull(MapSelection.toggle(segment, null))
        assertEquals(stops, MapSelection.toggle(null, stops))
    }
}
