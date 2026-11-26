package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.hypot
import kotlin.math.min

/** A point in canvas pixels. */
data class ScreenPoint(
    val x: Float,
    val y: Float,
)

/** A quadratic Bezier curve in canvas pixels. */
data class ArcCurve(
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val endX: Float,
    val endY: Float,
) {
    /** Point at parameter [t] in 0..1. */
    fun pointAt(t: Float): ScreenPoint {
        val u = 1f - t
        return ScreenPoint(
            x = u * u * startX + 2f * u * t * controlX + t * t * endX,
            y = u * u * startY + 2f * u * t * controlY + t * t * endY,
        )
    }

    /** The smallest y the curve reaches: its highest point on screen. */
    val topY: Float
        get() {
            val denominator = startY - 2f * controlY + endY
            val t = if (denominator == 0f) 0f else ((startY - controlY) / denominator).coerceIn(0f, 1f)
            return minOf(startY, endY, pointAt(t).y)
        }

    /** The part of this curve from its start to parameter [t] (de Casteljau split). */
    fun truncated(t: Float): ArcCurve {
        if (t >= 1f) return this
        val end = pointAt(t)
        return ArcCurve(
            startX = startX,
            startY = startY,
            controlX = startX + (controlX - startX) * t,
            controlY = startY + (controlY - startY) * t,
            endX = end.x,
            endY = end.y,
        )
    }
}

/**
 * Arc shapes for the route map, kept free of Compose so they are unit-testable.
 *
 * Every arc bows upward on screen, like a flight route, whichever way it runs: its control point sits
 * above the higher of its two ends by a bow proportional to the chord, capped so a long arc stays on the
 * map. A hop and the hop straight back share one shape.
 */
object ArcGeometry {
    /** Control point height above the higher end, as a fraction of the chord. */
    const val BOW_FRACTION = 0.3f

    /**
     * The arc from ([startX], [startY]) to ([endX], [endY]). Its highest point rises at most [maxBowPx]
     * above the higher end, so a fit that leaves that much room above the route never clips an arc.
     */
    fun arc(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        maxBowPx: Float = Float.MAX_VALUE,
    ): ArcCurve {
        val length = hypot(endX - startX, endY - startY)
        val bow = min(length * BOW_FRACTION, maxBowPx)
        // y grows downward, so "above" is the smaller y.
        return ArcCurve(
            startX = startX,
            startY = startY,
            controlX = (startX + endX) / 2f,
            controlY = min(startY, endY) - bow,
            endX = endX,
            endY = endY,
        )
    }
}

/** Points each arc is cut into for hit testing; plenty for a tap target a finger wide. */
private const val ARC_HIT_SAMPLES = 32

/**
 * Index of the arc that passes nearest to ([x], [y]) within [radiusPx], or null when none is that close.
 * Each arc is checked as a polyline of [ARC_HIT_SAMPLES] straight pieces.
 */
fun nearestArcIndex(
    arcs: List<ArcCurve>,
    x: Float,
    y: Float,
    radiusPx: Float,
): Int? {
    var best: Int? = null
    var bestDistance = radiusPx
    arcs.forEachIndexed { index, arc ->
        val points = (0..ARC_HIT_SAMPLES).map { arc.pointAt(it.toFloat() / ARC_HIT_SAMPLES) }
        val distance = points.zipWithNext().minOf { (a, b) -> distanceToSegment(x, y, a, b) }
        if (distance <= bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

private fun distanceToSegment(
    x: Float,
    y: Float,
    a: ScreenPoint,
    b: ScreenPoint,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(a.x + t * dx - x, a.y + t * dy - y)
}

/** Index of the point nearest to ([x], [y]) within [radiusPx], or null when none is that close. */
fun nearestPointIndex(
    points: List<ScreenPoint>,
    x: Float,
    y: Float,
    radiusPx: Float,
): Int? {
    var best: Int? = null
    var bestDistance = radiusPx
    points.forEachIndexed { index, point ->
        val distance = hypot(point.x - x, point.y - y)
        if (distance <= bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}
