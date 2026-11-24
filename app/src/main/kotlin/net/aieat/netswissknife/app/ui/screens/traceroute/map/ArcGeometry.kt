package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.hypot

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
 * Every arc bows to the left of its direction of travel by a height proportional to its
 * length, so a hop to a place and the hop straight back are drawn on opposite sides.
 */
object ArcGeometry {
    /** Control point offset as a fraction of the chord; the visible bow is half of this. */
    const val BOW_FRACTION = 0.3f

    fun arc(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): ArcCurve {
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        val midX = (startX + endX) / 2f
        val midY = (startY + endY) / 2f
        // (dy, -dx) is the left-hand normal in a y-down canvas, scaled by the chord length.
        return ArcCurve(
            startX = startX,
            startY = startY,
            controlX = midX + dy * BOW_FRACTION,
            controlY = midY - dx * BOW_FRACTION,
            endX = endX,
            endY = endY,
        ).takeIf { length > 0f } ?: ArcCurve(startX, startY, midX, midY, endX, endY)
    }
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
