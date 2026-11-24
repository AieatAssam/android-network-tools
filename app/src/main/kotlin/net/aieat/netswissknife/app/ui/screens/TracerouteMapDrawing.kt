package net.aieat.netswissknife.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcCurve
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapViewport
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteSegment
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ScreenPoint
import net.aieat.netswissknife.app.ui.screens.traceroute.map.SpeedBand
import net.aieat.netswissknife.app.ui.screens.traceroute.map.Stability
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.app.ui.screens.traceroute.map.WorldLand

private const val LABEL_BACKGROUND_ALPHA = 0.85f
private const val CASING_ALPHA = 0.75f

// A near-zero dash with a round cap draws a dot of the stroke width.
private const val DOT_DASH_PX = 0.1f
internal val ArcWidthFast = 5.dp
internal val ArcWidthMedium = 3.dp
internal val ArcWidthSlow = 1.5.dp
private val ArcCasingExtra = 2.dp
private val DashOn = 10.dp
private val DashOff = 6.dp
private val DotGap = 6.dp
private val MarkerRadius = 6.dp
private val MarkerRingWidth = 1.5.dp
private val DestinationRingRadius = 10.dp
private val SelectedRingRadius = 13.dp
private val SelectedRingWidth = 2.dp
private val LabelOffset = 8.dp
private val LabelPadding = 3.dp

/** Theme colours of the map, read once per composition so the draw pass reads no theme. */
internal data class MapColors(
    val ocean: Color,
    val land: Color,
    val surface: Color,
    val outline: Color,
    val selected: Color,
    val disputed: Color,
)

internal class ArcStyle(
    val color: Color,
    val stroke: Stroke,
    val casing: Stroke,
)

/** Per-route drawing inputs, prepared once per route so drawing allocates almost nothing. */
internal class RouteDrawing(
    val map: TraceRouteMap,
    val styles: List<ArcStyle>,
    val labels: List<TextLayoutResult>,
    val stopColors: List<Color>,
    val colors: MapColors,
) {
    val arcPath = Path()
}

@Composable
internal fun rememberRouteDrawing(
    map: TraceRouteMap,
    colorFor: (Long?) -> Color,
): RouteDrawing {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = scheme.onSurface)
    val colors =
        MapColors(
            ocean = scheme.surfaceContainerHigh,
            land = scheme.outlineVariant,
            surface = scheme.surface,
            outline = scheme.onSurface,
            selected = scheme.primary,
            disputed = scheme.error,
        )
    return remember(map, density, colorFor, labelStyle, colors) {
        RouteDrawing(
            map = map,
            styles = map.segments.map { arcStyle(density, it, colorFor(it.rttMs)) },
            labels = (map.stops + map.disputedStops).map { textMeasurer.measure(it.label, labelStyle) },
            stopColors =
                map.stops.map { colorFor(it.hops.first().rtTimeMs) } + map.disputedStops.map { colors.disputed },
            colors = colors,
        )
    }
}

internal fun landPath(land: WorldLand): Path =
    Path().apply {
        fillType = PathFillType.EvenOdd
        land.rings.forEach { ring ->
            moveTo(ring[0], -ring[1])
            for (i in 2 until ring.size step 2) lineTo(ring[i], -ring[i + 1])
            close()
        }
    }

internal fun arcWidth(speed: SpeedBand) =
    when (speed) {
        SpeedBand.FAST -> ArcWidthFast
        SpeedBand.MEDIUM, SpeedBand.UNKNOWN -> ArcWidthMedium
        SpeedBand.SLOW -> ArcWidthSlow
    }

internal fun arcStroke(
    density: Density,
    widthPx: Float,
    stability: Stability,
): Stroke =
    with(density) {
        val effect =
            when (stability) {
                Stability.STABLE, Stability.NOT_MEASURED -> null
                Stability.UNSTABLE -> PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))
                Stability.INFERRED -> PathEffect.dashPathEffect(floatArrayOf(DOT_DASH_PX, widthPx + DotGap.toPx()))
            }
        val cap = if (stability == Stability.INFERRED) StrokeCap.Round else StrokeCap.Butt
        Stroke(width = widthPx, cap = cap, pathEffect = effect)
    }

private fun arcStyle(
    density: Density,
    segment: RouteSegment,
    color: Color,
): ArcStyle {
    val width = with(density) { arcWidth(segment.speed).toPx() }
    val casingWidth = width + with(density) { ArcCasingExtra.toPx() }
    return ArcStyle(
        color = color,
        stroke = arcStroke(density, width, segment.stability),
        casing = arcStroke(density, casingWidth, segment.stability),
    )
}

internal fun DrawScope.drawLand(
    landPath: Path,
    viewport: MapViewport,
    color: Color,
) {
    val scale = viewport.pxPerDegree.toFloat()
    viewport.worldCopyOffsets(size.width).forEach { offset ->
        withTransform({
            translate(
                left = (size.width / 2.0 - (viewport.centerLon - offset) * viewport.pxPerDegree).toFloat(),
                top = (size.height / 2.0 + viewport.centerLat * viewport.pxPerDegree).toFloat(),
            )
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(landPath, color)
        }
    }
}

/** Arcs up to [shown] (one unit per segment), then the stops they have reached. */
internal fun DrawScope.drawRoute(
    drawing: RouteDrawing,
    geometry: MapGeometry,
    shown: Float,
    selectedHop: Int?,
) {
    drawArcs(drawing, geometry.arcs, shown)
    val map = drawing.map
    map.stops.forEachIndexed { index, stop ->
        // A stop appears once the arc arriving at it has been drawn.
        if (index > 0 && shown < index) return@forEachIndexed
        drawStop(stop, geometry.stopPoints[index], index, drawing, selectedHop)
    }
    map.disputedStops.forEachIndexed { index, stop ->
        drawStop(stop, geometry.disputedPoints[index], map.stops.size + index, drawing, selectedHop)
    }
}

private fun DrawScope.drawArcs(
    drawing: RouteDrawing,
    arcs: List<ArcCurve>,
    shown: Float,
) {
    val path = drawing.arcPath
    val casingColor = drawing.colors.surface.copy(alpha = CASING_ALPHA)
    arcs.forEachIndexed { index, arc ->
        val fraction = (shown - index).coerceIn(0f, 1f)
        if (fraction <= 0f) return@forEachIndexed
        val curve = if (fraction < 1f) arc.truncated(fraction) else arc
        path.reset()
        path.moveTo(curve.startX, curve.startY)
        path.quadraticTo(curve.controlX, curve.controlY, curve.endX, curve.endY)
        val style = drawing.styles[index]
        drawPath(path, casingColor, style = style.casing)
        drawPath(path, style.color, style = style.stroke)
    }
}

private fun DrawScope.drawStop(
    stop: RouteStop,
    point: ScreenPoint,
    index: Int,
    drawing: RouteDrawing,
    selectedHop: Int?,
) {
    val colors = drawing.colors
    val fill = drawing.stopColors[index]
    val center = Offset(point.x, point.y)
    val radius = MarkerRadius.toPx()
    val ringWidth = MarkerRingWidth.toPx()
    if (stop.hops.any { it.hopNumber == selectedHop }) {
        drawCircle(colors.selected, SelectedRingRadius.toPx(), center, style = Stroke(SelectedRingWidth.toPx()))
    }
    if (stop.isDestination) drawCircle(colors.outline, DestinationRingRadius.toPx(), center, style = Stroke(ringWidth))
    if (stop.disputed) {
        drawCircle(colors.surface, radius, center)
        drawCircle(fill, radius, center, style = Stroke(ringWidth * 2))
    } else {
        drawCircle(fill, radius, center)
        drawCircle(colors.surface, radius, center, style = Stroke(ringWidth))
    }
    val label = drawing.labels[index]
    val pad = LabelPadding.toPx()
    val topLeft = Offset(point.x + LabelOffset.toPx(), point.y - LabelOffset.toPx() - label.size.height)
    drawRoundRect(
        color = colors.surface.copy(alpha = LABEL_BACKGROUND_ALPHA),
        topLeft = Offset(topLeft.x - pad, topLeft.y - pad),
        size = Size(label.size.width + 2 * pad, label.size.height + 2 * pad),
        cornerRadius = CornerRadius(pad * 2),
    )
    drawText(label, topLeft = topLeft)
}
