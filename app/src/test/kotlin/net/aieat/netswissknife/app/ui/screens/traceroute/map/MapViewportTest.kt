package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MapViewport")
class MapViewportTest {
    private val eps = 1e-6

    @Nested
    @DisplayName("fitting the route")
    inner class Fit {
        @Test
        fun `a route's bounding box is padded on every side and centred`() {
            val bounds = GeoBounds(minLon = 0.0, maxLon = 100.0, minLat = 0.0, maxLat = 50.0)

            val viewport = MapViewport.fit(bounds, widthPx = 1_300f, heightPx = 650f)

            assertEquals(50.0, viewport.centerLon, eps)
            assertEquals(25.0, viewport.centerLat, eps)
            // 100 degrees plus 15% padding on each side is 130 degrees across 1300 px.
            assertEquals(10.0, viewport.pxPerDegree, eps)
        }

        @Test
        fun `the tighter axis decides the scale so the whole route stays visible`() {
            val bounds = GeoBounds(minLon = 0.0, maxLon = 100.0, minLat = 0.0, maxLat = 50.0)

            val viewport = MapViewport.fit(bounds, widthPx = 1_300f, heightPx = 300f)

            assertEquals(300.0 / 65.0, viewport.pxPerDegree, eps)
        }

        @Test
        fun `a single city is shown with a minimum span, not zoomed to a dot`() {
            val bounds = GeoBounds(minLon = 13.4, maxLon = 13.4, minLat = 52.5, maxLat = 52.5)

            val viewport = MapViewport.fit(bounds, widthPx = 400f, heightPx = 200f)

            assertEquals(200.0 / MapViewport.MIN_FIT_SPAN_DEGREES, viewport.pxPerDegree, eps)
            assertEquals(13.4, viewport.centerLon, eps)
            assertEquals(52.5, viewport.centerLat, eps)
        }

        @Test
        fun `no route shows the whole world`() {
            val viewport = MapViewport.fit(null, widthPx = 720f, heightPx = 360f)

            assertEquals(0.0, viewport.centerLon, eps)
            assertEquals(0.0, viewport.centerLat, eps)
            assertEquals(2.0, viewport.pxPerDegree, eps)
        }

        @Test
        fun `a fit near a pole keeps the view on the globe`() {
            val bounds = GeoBounds(minLon = 10.0, maxLon = 12.0, minLat = 85.0, maxLat = 85.0)

            val viewport = MapViewport.fit(bounds, widthPx = 400f, heightPx = 200f)

            val topLat = viewport.latAt(0f, 200f)
            assertEquals(90.0, topLat, 1e-3)
        }

        @Test
        fun `bounds come from the stops, and none without stops`() {
            assertNull(GeoBounds.of(emptyList()))
            val bounds = GeoBounds.of(listOf(10.0 to 20.0, -5.0 to 241.0, 30.0 to 100.0))
            assertEquals(GeoBounds(minLon = 20.0, maxLon = 241.0, minLat = -5.0, maxLat = 30.0), bounds)
            assertEquals(130.5, bounds!!.centerLon, eps)
        }
    }

    @Nested
    @DisplayName("projection")
    inner class Projection {
        private val viewport = MapViewport(centerLon = 10.0, centerLat = 20.0, pxPerDegree = 4.0)

        @Test
        fun `the centre lands in the middle of the canvas`() {
            assertEquals(150f, viewport.xOf(10.0, 300f), 1e-4f)
            assertEquals(100f, viewport.yOf(20.0, 200f), 1e-4f)
        }

        @Test
        fun `a degree is the same number of pixels across and down, so shapes keep their aspect`() {
            val dx = viewport.xOf(11.0, 300f) - viewport.xOf(10.0, 300f)
            val dy = viewport.yOf(20.0, 200f) - viewport.yOf(21.0, 200f)
            assertEquals(dx, dy, 1e-4f)
            assertEquals(4f, dx, 1e-4f)
        }

        @Test
        fun `north is up`() {
            assertTrue(viewport.yOf(30.0, 200f) < viewport.yOf(10.0, 200f))
        }

        @Test
        fun `screen positions map back to coordinates`() {
            assertEquals(35.0, viewport.lonAt(viewport.xOf(35.0, 300f), 300f), 1e-4)
            assertEquals(-12.0, viewport.latAt(viewport.yOf(-12.0, 200f), 200f), 1e-4)
        }

        @Test
        fun `the land is repeated one world to each side only where it is visible`() {
            val wide = MapViewport(centerLon = 170.0, centerLat = 0.0, pxPerDegree = 1.0)
            assertEquals(listOf(0.0, 360.0), wide.worldCopyOffsets(widthPx = 100f))
            val west = MapViewport(centerLon = -175.0, centerLat = 0.0, pxPerDegree = 1.0)
            assertEquals(listOf(-360.0, 0.0), west.worldCopyOffsets(widthPx = 100f))
            val middle = MapViewport(centerLon = 0.0, centerLat = 0.0, pxPerDegree = 1.0)
            assertEquals(listOf(0.0), middle.worldCopyOffsets(widthPx = 100f))
        }
    }

    @Nested
    @DisplayName("gestures")
    inner class Gestures {
        private val width = 400f
        private val height = 200f
        private val fitted = MapViewport.fit(GeoBounds(0.0, 40.0, 0.0, 20.0), width, height)

        @Test
        fun `zooming keeps the point under the fingers in place`() {
            val focusX = 300f
            val focusY = 50f
            val lonBefore = fitted.lonAt(focusX, width)
            val latBefore = fitted.latAt(focusY, height)

            val zoomed = fitted.zoomedBy(2f, focusX, focusY, width, height, anchorLon = 20.0)

            assertEquals(fitted.pxPerDegree * 2, zoomed.pxPerDegree, eps)
            assertEquals(lonBefore, zoomed.lonAt(focusX, width), 1e-4)
            assertEquals(latBefore, zoomed.latAt(focusY, height), 1e-4)
        }

        @Test
        fun `zoom stops at the closest and widest allowed views`() {
            val closest = fitted.zoomedBy(1_000f, 200f, 100f, width, height, anchorLon = 20.0)
            assertEquals(width / MapViewport.MIN_VISIBLE_SPAN_DEGREES, closest.pxPerDegree, eps)

            val widest = fitted.zoomedBy(0.001f, 200f, 100f, width, height, anchorLon = 20.0)
            assertEquals(MapViewport.minPxPerDegree(width, height), widest.pxPerDegree, eps)
        }

        @Test
        fun `dragging moves the map with the finger`() {
            val zoomed = fitted.zoomedBy(4f, 200f, 100f, width, height, anchorLon = 20.0)

            val panned = zoomed.pannedBy(dxPx = 40f, dyPx = -20f, widthPx = width, heightPx = height, anchorLon = 20.0)

            assertEquals(zoomed.centerLon - 40.0 / zoomed.pxPerDegree, panned.centerLon, eps)
            assertEquals(zoomed.centerLat - 20.0 / zoomed.pxPerDegree, panned.centerLat, eps)
        }

        @Test
        fun `panning cannot leave the globe vertically`() {
            val zoomed = fitted.zoomedBy(4f, 200f, 100f, width, height, anchorLon = 20.0)

            val panned =
                zoomed.pannedBy(dxPx = 0f, dyPx = 1_000_000f, widthPx = width, heightPx = height, anchorLon = 20.0)

            assertEquals(90.0, panned.latAt(0f, height), 1e-4)
        }

        @Test
        fun `panning cannot wander more than half a world from the route`() {
            val panned =
                fitted.pannedBy(dxPx = -1_000_000f, dyPx = 0f, widthPx = width, heightPx = height, anchorLon = 20.0)

            assertEquals(200.0, panned.centerLon, eps)
        }

        @Test
        fun `only a view closer than the fitted one counts as zoomed in`() {
            assertFalse(fitted.isZoomedInFrom(fitted))
            assertFalse(fitted.zoomedBy(0.5f, 200f, 100f, width, height, 20.0).isZoomedInFrom(fitted))
            assertTrue(fitted.zoomedBy(1.5f, 200f, 100f, width, height, 20.0).isZoomedInFrom(fitted))
        }
    }

    @Nested
    @DisplayName("tapping a stop")
    inner class HitTest {
        private val points = listOf(ScreenPoint(10f, 10f), ScreenPoint(100f, 100f), ScreenPoint(110f, 100f))

        @Test
        fun `the nearest stop within reach is picked`() {
            assertEquals(2, nearestPointIndex(points, 108f, 101f, radiusPx = 24f))
            assertEquals(0, nearestPointIndex(points, 30f, 20f, radiusPx = 24f))
        }

        @Test
        fun `a tap away from every stop picks nothing`() {
            assertNull(nearestPointIndex(points, 300f, 300f, radiusPx = 24f))
            assertNull(nearestPointIndex(emptyList(), 0f, 0f, radiusPx = 24f))
        }
    }
}
