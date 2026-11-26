package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.min
import kotlin.random.Random

@DisplayName("MapViewport fit, zoom focus and pan reach")
class MapFitTest {
    private val insets = FitInsets(left = 40f, top = 40f, right = 100f, bottom = 40f)
    private val tolerancePx = 1f

    /**
     * Unwrapped like the route: each longitude within half a world of the one before, often across the
     * antimeridian, and the whole route within one world.
     */
    private fun randomRoute(random: Random): List<Pair<Double, Double>> {
        val count = random.nextInt(1, 12)
        val start = random.nextDouble(-180.0, 180.0)
        var lon = start
        return List(count) {
            val step = random.nextDouble(-179.0, 179.0).let { if (random.nextInt(3) == 0) it else it / 10 }
            lon = (lon + step).coerceIn(start - 170.0, start + 170.0)
            random.nextDouble(-85.0, 85.0) to lon
        }
    }

    private fun assertInside(
        viewport: MapViewport,
        points: List<Pair<Double, Double>>,
        width: Float,
        height: Float,
        area: FitInsets,
        context: String,
    ) {
        points.forEach { (lat, lon) ->
            val x = viewport.xOf(lon, width)
            val y = viewport.yOf(lat, height)
            assertTrue(x >= area.left - tolerancePx && x <= width - area.right + tolerancePx, "x $x of $lon: $context")
            assertTrue(y >= area.top - tolerancePx && y <= height - area.bottom + tolerancePx, "y $y of $lat: $context")
        }
    }

    @Nested
    @DisplayName("default framing")
    inner class Framing {
        @Test
        fun `every marker of a random route lies inside the canvas less the padding, at any aspect ratio`() {
            val random = Random(20261126)
            val sizes = listOf(1080f to 640f, 640f to 640f, 400f to 900f, 1600f to 300f, 360f to 220f)
            repeat(500) { run ->
                val route = randomRoute(random)
                val bounds = GeoBounds.of(route)!!
                val (width, height) = sizes[run % sizes.size]
                val viewport = MapViewport.fit(bounds, width, height, insets)
                assertInside(viewport, route, width, height, insets, "run $run, $width x $height, $route")
            }
        }

        @Test
        fun `London to Seattle keeps London clear of the buttons on the end side`() {
            // Hops 1 to 4 near London, the disputed ones placed in Seattle.
            val route = listOf(51.5 to -0.13, 51.75 to -0.47, 47.6 to -122.3)
            val viewport = MapViewport.fit(GeoBounds.of(route)!!, 1000f, 560f, insets)

            assertInside(viewport, route, 1000f, 560f, insets, "London-Seattle")
        }

        @Test
        fun `a route across the antimeridian is framed the short way`() {
            // Tokyo to Los Angeles, unwrapped east: 220 degrees of longitude rather than 140 the long way.
            val route = listOf(35.7 to 139.7, 34.1 to 241.8)
            val bounds = GeoBounds.of(route)!!
            val viewport = MapViewport.fit(bounds, 1000f, 560f, insets)

            assertInside(viewport, route, 1000f, 560f, insets, "Pacific")
            val shortSpanPx = viewport.xOf(241.8, 1000f) - viewport.xOf(139.7, 1000f)
            assertEquals(102.1 * viewport.pxPerDegree, shortSpanPx.toDouble(), 1.0)
        }

        @Test
        fun `a route at high latitude is not pushed off the top by the pole limit`() {
            val route = listOf(78.2 to 15.6, 64.1 to -21.9, 69.6 to 18.9)
            val viewport = MapViewport.fit(GeoBounds.of(route)!!, 1000f, 300f, insets)

            assertInside(viewport, route, 1000f, 300f, insets, "Arctic")
            assertTrue(viewport.latAt(0f, 300f) <= 90.0 + 1e-6)
        }

        @Test
        fun `without insets the fit is centred as before`() {
            val bounds = GeoBounds(minLon = 0.0, maxLon = 40.0, minLat = 0.0, maxLat = 20.0)
            val viewport = MapViewport.fit(bounds, 800f, 400f)

            assertEquals(bounds.centerLon, viewport.centerLon, 1e-9)
            assertEquals(bounds.centerLat, viewport.centerLat, 1e-9)
        }

        @Test
        fun `a view moved off the fit is not the fit, so reset is offered`() {
            val bounds = GeoBounds(minLon = -122.3, maxLon = -0.1, minLat = 47.6, maxLat = 51.5)
            val fitted = MapViewport.fit(bounds, 1000f, 560f, insets)

            assertTrue(fitted.looksLike(fitted))
            val panned = fitted.pannedBy(30f, 0f, 1000f, 560f, bounds.centerLon)
            val zoomedOut = fitted.zoomedBy(0.5f, 500f, 280f, 1000f, 560f, bounds.centerLon)
            assertFalse(panned.looksLike(fitted))
            assertFalse(zoomedOut.looksLike(fitted))
        }
    }

    @Test
    fun `every arc of a random fitted route stays inside the canvas, apex included`() {
        val bow = 60f
        val roomy = FitInsets(left = 40f, top = 40f + bow, right = 100f, bottom = 40f)
        val random = Random(99)
        repeat(300) { run ->
            val route = randomRoute(random)
            val (width, height) = listOf(1000f to 560f, 400f to 300f, 1200f to 1100f)[run % 3]
            val viewport = MapViewport.fit(GeoBounds.of(route)!!, width, height, roomy)
            val points = route.map { (lat, lon) -> viewport.xOf(lon, width) to viewport.yOf(lat, height) }
            points.zipWithNext().forEach { (a, b) ->
                val arc = ArcGeometry.arc(a.first, a.second, b.first, b.second, maxBowPx = bow)
                assertTrue(arc.topY >= 0f && arc.topY < min(a.second, b.second), "run $run: apex ${arc.topY}")
            }
        }
    }

    @Nested
    @DisplayName("map height")
    inner class MapHeight {
        @Test
        fun `a route wider than the world view gets a canvas the world fills top to bottom`() {
            // London, Los Angeles, Tokyo unwrapped west: about 220 degrees.
            val route = listOf(51.5 to -0.13, 34.1 to -118.2, 35.7 to -220.3)
            val bounds = GeoBounds.of(route)!!
            val width = 1100f
            val offered = 900f

            val height = MapSizing.fittedHeightPx(bounds, width, offered, insets)
            val fitted = MapViewport.fit(bounds, width, height, insets)

            assertTrue(height < offered)
            assertEquals(height, (180.0 * fitted.pxPerDegree).toFloat(), 1f)
            // The world is centred, so neither pole leaves a band.
            assertEquals(0.0, fitted.centerLat, 1e-3)
            assertInside(fitted, route, width, height, insets, "wide route height")
        }

        @Test
        fun `a route inside the world view keeps the offered height`() {
            val bounds = GeoBounds.of(listOf(51.5 to -0.13, 47.6 to -122.3))!!

            assertEquals(560f, MapSizing.fittedHeightPx(bounds, 1000f, 560f, insets))
            assertEquals(560f, MapSizing.fittedHeightPx(null, 1000f, 560f, insets))
        }
    }

    @Nested
    @DisplayName("a route wider than the world view")
    inner class WideRoute {
        // London, Tokyo, Los Angeles unwrapped east: about 242 degrees, on a phone-sized map.
        private val route = listOf(51.5 to -0.13, 35.7 to 139.7, 34.1 to 241.8)
        private val bounds = GeoBounds.of(route)!!
        private val width = 860f
        private val height = 570f
        private val fitted = MapViewport.fit(bounds, width, height, insets)

        @Test
        fun `the fit may show more than the world view so every marker fits`() {
            assertTrue(fitted.pxPerDegree < MapViewport.minPxPerDegree(width, height))
            assertInside(fitted, route, width, height, insets, "wide route")
        }

        @Test
        fun `a pinch that does not zoom, or a zoom out, leaves the fitted scale alone`() {
            val pinched = fitted.zoomedBy(1f, 100f, 100f, width, height, bounds.centerLon)
            val zoomedOut = fitted.zoomedBy(0.5f, 100f, 100f, width, height, bounds.centerLon)

            assertEquals(fitted.pxPerDegree, pinched.pxPerDegree, 1e-9)
            assertEquals(fitted.pxPerDegree, zoomedOut.pxPerDegree, 1e-9)
            assertFalse(pinched.isZoomedInFrom(fitted))
        }
    }

    @Nested
    @DisplayName("following a growing route")
    inner class Following {
        private val width = 1000f
        private val height = 560f
        private val view = MapViewport(centerLon = 0.0, centerLat = 0.0, pxPerDegree = 10.0)

        @Test
        fun `a marker under the zoom buttons is not in view, so the map follows it`() {
            // 30 px from the end edge, inside the 100 px the buttons take.
            val lonUnderButtons = (width / 2f - 30f) / 10.0

            assertFalse(MapViewport.keepsInView(view, 0.0, lonUnderButtons, width, height, insets))
            assertTrue(MapViewport.keepsInView(view, 0.0, 0.0, width, height, insets))
        }

        @Test
        fun `every marker of a fit is in view, so a settled fit does not follow again`() {
            val route = listOf(51.5 to -0.13, 47.6 to -122.3)
            val fitted = MapViewport.fit(GeoBounds.of(route)!!, width, height, insets)

            route.forEach { (lat, lon) -> assertTrue(MapViewport.keepsInView(fitted, lat, lon, width, height, insets)) }
        }
    }

    @Nested
    @DisplayName("zoom buttons")
    inner class ZoomButtons {
        private val bounds = GeoBounds(minLon = -122.3, maxLon = -0.1, minLat = 47.6, maxLat = 51.5)
        private val width = 1000f
        private val height = 560f
        private val fitted = MapViewport.fit(bounds, width, height, insets)

        @Test
        fun `zooming in keeps the route's centre where it was on screen`() {
            val (x, y) = MapZoom.buttonFocus(fitted, bounds, panned = false, width, height)
            val zoomed = fitted.zoomedBy(MapViewport.ZOOM_STEP, x, y, width, height, bounds.centerLon)

            assertEquals(x, zoomed.xOf(bounds.centerLon, width), 0.5f)
            assertEquals(y, zoomed.yOf(bounds.centerLat, height), 0.5f)
        }

        @Test
        fun `after a pan the buttons keep the map's centre`() {
            val panned = fitted.pannedBy(200f, 0f, width, height, bounds.centerLon)

            assertEquals(width / 2f to height / 2f, MapZoom.buttonFocus(panned, bounds, panned = true, width, height))
        }

        @Test
        fun `a route centre off the canvas is brought to its edge, and no route zooms about the centre`() {
            val far = fitted.zoomedBy(8f, 0f, 0f, width, height, bounds.centerLon)
            val (x, y) = MapZoom.buttonFocus(far, bounds, panned = false, width, height)

            assertTrue(x in 0f..width && y in 0f..height)
            assertEquals(width / 2f to height / 2f, MapZoom.buttonFocus(fitted, null, panned = false, width, height))
        }
    }

    @Nested
    @DisplayName("pan reach")
    inner class PanReach {
        @Test
        fun `every marker can be panned onto the canvas at any zoom`() {
            val random = Random(7)
            val width = 1000f
            val height = 560f
            repeat(100) { run ->
                val route = randomRoute(random)
                val bounds = GeoBounds.of(route)!!
                val fitted = MapViewport.fit(bounds, width, height, insets)
                listOf(1f, 4f, 64f, 10_000f).forEach { zoom ->
                    val zoomed = fitted.zoomedBy(zoom, width / 2f, height / 2f, width, height, bounds.centerLon)
                    route.forEach { (lat, lon) ->
                        val dx = width / 2f - zoomed.xOf(lon, width)
                        val dy = height / 2f - zoomed.yOf(lat, height)
                        val moved = zoomed.pannedBy(dx, dy, width, height, bounds.centerLon)
                        val x = moved.xOf(lon, width)
                        val y = moved.yOf(lat, height)
                        assertTrue(x in 0f..width && y in 0f..height, "run $run zoom $zoom: ($lat, $lon) at ($x, $y)")
                    }
                }
            }
        }
    }
}
