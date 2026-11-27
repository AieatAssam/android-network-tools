package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

@DisplayName("ArcGeometry")
class ArcGeometryTest {
    private val eps = 1e-3f

    @Test
    fun `the control point sits on the perpendicular through the midpoint`() {
        val arc = ArcGeometry.arc(0f, 0f, 100f, 0f)

        assertEquals(50f, arc.controlX, eps)
        assertEquals(-100f * ArcGeometry.BOW_FRACTION, arc.controlY, eps)
    }

    @Test
    fun `bow height is proportional to the segment length`() {
        val short = ArcGeometry.arc(0f, 0f, 10f, 0f)
        val long = ArcGeometry.arc(0f, 0f, 200f, 0f)

        val shortBow = hypot(short.controlX - 5f, short.controlY)
        val longBow = hypot(long.controlX - 100f, long.controlY)
        assertEquals(20f, longBow / shortBow, eps)
    }

    @Test
    fun `a hop and the hop straight back share one upward shape`() {
        val out = ArcGeometry.arc(0f, 0f, 100f, 40f)
        val back = ArcGeometry.arc(100f, 40f, 0f, 0f)

        assertEquals(out.controlX, back.controlX, eps)
        assertEquals(out.controlY, back.controlY, eps)
        assertTrue(out.controlY < 0f)
    }

    @Test
    fun `every arc bows upward, above both its ends, whichever way it runs`() {
        val random = Random(42)
        repeat(1_000) {
            val sx = random.nextFloat() * 2_000f - 500f
            val sy = random.nextFloat() * 1_000f
            val ex = random.nextFloat() * 2_000f - 500f
            val ey = random.nextFloat() * 1_000f
            if (hypot(ex - sx, ey - sy) < 1f) return@repeat
            val arc = ArcGeometry.arc(sx, sy, ex, ey, maxBowPx = 80f)
            val apexY = arc.topY
            assertTrue((0..64).all { arc.pointAt(it / 64f).y >= apexY - eps })

            assertTrue(apexY < min(sy, ey), "apex $apexY for ($sx, $sy) to ($ex, $ey)")
            assertTrue(apexY >= min(sy, ey) - 80f - eps, "bow capped for ($sx, $sy) to ($ex, $ey)")
        }
    }

    @Test
    fun `a long arc's bow is capped`() {
        val arc = ArcGeometry.arc(0f, 500f, 2_000f, 500f, maxBowPx = 60f)

        assertEquals(440f, arc.controlY, eps)
    }

    @Test
    fun `the curve starts and ends at the stops`() {
        val arc = ArcGeometry.arc(10f, 20f, 70f, -30f)

        assertEquals(ScreenPoint(10f, 20f), arc.pointAt(0f))
        val end = arc.pointAt(1f)
        assertEquals(70f, end.x, eps)
        assertEquals(-30f, end.y, eps)
    }

    @Test
    fun `truncating draws the first part of the same curve`() {
        val arc = ArcGeometry.arc(0f, 0f, 120f, 60f)

        val half = arc.truncated(0.5f)
        val expectedEnd = arc.pointAt(0.5f)
        assertEquals(arc.startX, half.startX, eps)
        assertEquals(arc.startY, half.startY, eps)
        assertEquals(expectedEnd.x, half.endX, eps)
        assertEquals(expectedEnd.y, half.endY, eps)
        // A point a quarter along the whole curve is half way along the truncated one.
        val quarter = arc.pointAt(0.25f)
        val halfOfHalf = half.pointAt(0.5f)
        assertEquals(quarter.x, halfOfHalf.x, eps)
        assertEquals(quarter.y, halfOfHalf.y, eps)
        assertEquals(arc, arc.truncated(1f))
        assertEquals(arc, arc.truncated(2f))
    }

    @Test
    fun `a zero-length segment has its control point on the stop`() {
        val arc = ArcGeometry.arc(5f, 5f, 5f, 5f)

        assertEquals(5f, arc.controlX, eps)
        assertEquals(5f, arc.controlY, eps)
    }

    @Test
    fun `a tap on an arc's curve finds it, a tap on the straight chord of a tall arc does not`() {
        val arc = ArcGeometry.arc(0f, 100f, 200f, 100f)
        val top = arc.pointAt(0.5f)

        assertEquals(0, nearestArcIndex(listOf(arc), top.x, top.y + 5f, radiusPx = 24f))
        // A 200 px chord bows 30 px away, so the chord's midpoint is out of a 24 px reach.
        assertEquals(30f, abs(top.y - 100f), eps)
        assertNull(nearestArcIndex(listOf(arc), 100f, 100f, radiusPx = 24f))
    }

    @Test
    fun `the nearest of two arcs within reach wins, and nothing out of reach is found`() {
        val upper = ArcGeometry.arc(0f, 0f, 200f, 0f)
        val lower = ArcGeometry.arc(0f, 300f, 200f, 300f)
        val nearLower = lower.pointAt(0.3f)

        assertEquals(1, nearestArcIndex(listOf(upper, lower), nearLower.x, nearLower.y - 3f, radiusPx = 24f))
        assertNull(nearestArcIndex(listOf(upper, lower), 100f, 1_000f, radiusPx = 24f))
        assertNull(nearestArcIndex(emptyList(), 0f, 0f, radiusPx = 24f))
    }

    @Test
    fun `a zero-length arc is hit at its point`() {
        val dot = ArcGeometry.arc(50f, 50f, 50f, 50f)

        assertEquals(0, nearestArcIndex(listOf(dot), 60f, 50f, radiusPx = 24f))
    }
}
