package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MapDrag")
class MapDragTest {
    private val width = 400f
    private val height = 200f
    private val fitted = MapViewport(centerLon = 0.0, centerLat = 0.0, pxPerDegree = 2.0)

    // 20 px per degree on a 200 px tall canvas shows 10 degrees of latitude, so the centre stops at 85.
    private fun zoomedAt(
        lat: Double,
        lon: Double,
    ) = MapViewport(centerLon = lon, centerLat = lat, pxPerDegree = 20.0)

    private fun takes(
        viewport: MapViewport,
        dx: Float,
        dy: Float,
    ) = MapDrag.mapTakesDrag(viewport, fitted, dx, dy, width, height, anchorLon = 0.0)

    @Test
    fun `a map that is not zoomed in leaves every drag to the page`() {
        assertFalse(takes(fitted, 0f, 40f))
        assertFalse(takes(fitted, 40f, 0f))
        assertFalse(takes(fitted.copy(pxPerDegree = 2.01), 0f, -40f))
    }

    @Test
    fun `a zoomed map away from its limits takes drags in any direction`() {
        val viewport = zoomedAt(lat = 0.0, lon = 0.0)

        assertTrue(takes(viewport, 0f, 40f))
        assertTrue(takes(viewport, 0f, -40f))
        assertTrue(takes(viewport, 40f, 0f))
        assertTrue(takes(viewport, -40f, 0f))
    }

    @Test
    fun `at the north edge a downward drag goes to the page and an upward one moves the map`() {
        val viewport = zoomedAt(lat = 85.0, lon = 0.0)

        assertFalse(takes(viewport, 0f, 40f))
        assertTrue(takes(viewport, 0f, -40f))
    }

    @Test
    fun `at the south edge an upward drag goes to the page and a downward one moves the map`() {
        val viewport = zoomedAt(lat = -85.0, lon = 0.0)

        assertFalse(takes(viewport, 0f, -40f))
        assertTrue(takes(viewport, 0f, 40f))
    }

    @Test
    fun `at the east limit a leftward drag goes to the page`() {
        val viewport = zoomedAt(lat = 0.0, lon = 180.0)

        assertFalse(takes(viewport, -40f, 0f))
        assertTrue(takes(viewport, 40f, 0f))
    }

    @Test
    fun `at the west limit a rightward drag goes to the page`() {
        val viewport = zoomedAt(lat = 0.0, lon = -180.0)

        assertFalse(takes(viewport, 40f, 0f))
        assertTrue(takes(viewport, -40f, 0f))
    }

    @Test
    fun `the drag's main direction decides, and a tie counts as horizontal`() {
        val atNorthEdge = zoomedAt(lat = 85.0, lon = 0.0)

        assertFalse(takes(atNorthEdge, 10f, 40f))
        assertTrue(takes(atNorthEdge, 40f, 10f))
        assertTrue(takes(atNorthEdge, 20f, 20f))
    }

    @Test
    fun `no movement is not taken`() {
        assertFalse(takes(zoomedAt(lat = 0.0, lon = 0.0), 0f, 0f))
    }
}
