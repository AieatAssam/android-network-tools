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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcCurve
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcWidth
import net.aieat.netswissknife.app.ui.screens.traceroute.map.LabelSize
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapLegendContent
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapSelection
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapViewport
import net.aieat.netswissknife.app.ui.screens.traceroute.map.PlacedLabel
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteSegment
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ScreenPoint
import net.aieat.netswissknife.app.ui.screens.traceroute.map.SegmentKey
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
internal val MarkerRadius = 6.dp
private val MarkerRingWidth = 1.5.dp
private val DestinationRingRadius = 10.dp
private val SelectedRingRadius = 13.dp
private val SelectedRingWidth = 2.dp
private val HeadRingGrowth = 10.dp
private val SelectedArcHalo = 6.dp
internal val LabelOffset = 8.dp
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

/**
 * Per-route drawing inputs, prepared once per route so drawing allocates almost nothing. Label text is
 * laid out on first use and kept, since the clusters, and so the labels, change with the zoom.
 */
internal class RouteDrawing(
    val map: TraceRouteMap,
    val styles: List<ArcStyle>,
    /** One colour per stop, in [TraceRouteMap.allStops] order. */
    val stopColors: List<Color>,
    val colors: MapColors,
    private val layoutLabel: (String) -> TextLayoutResult,
    private val labelPaddingPx: Float,
) {
    val arcPath = Path()
    private val labelCache = HashMap<String, TextLayoutResult>()

    fun label(text: String): TextLayoutResult = labelCache.getOrPut(text) { layoutLabel(text) }

    /** The box a label needs, background padding included. */
    val labelSize: (String) -> LabelSize = { text ->
        val size = label(text).size
        LabelSize(size.width + 2 * labelPaddingPx, size.height + 2 * labelPaddingPx)
    }
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
            stopColors = map.stops.map { colorFor(it.rttMs) } + map.disputedStops.map { colors.disputed },
            colors = colors,
            layoutLabel = { textMeasurer.measure(it, labelStyle) },
            labelPaddingPx = with(density) { LabelPadding.toPx() },
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

/** Stroke width of [width]; arcs get theirs from [MapLegendContent.widthOf], as the legend does. */
internal fun arcWidthDp(width: ArcWidth): Dp =
    when (width) {
        ArcWidth.THICK -> ArcWidthFast
        ArcWidth.MEDIUM -> ArcWidthMedium
        ArcWidth.THIN -> ArcWidthSlow
    }

/**
 * The line pattern of [stability], one meaning each: solid for stable, dotted for unstable, dash-dot for
 * stability not measured, dashed for an unknown path. [patternWidthPx] sets the gaps, so an arc's casing, drawn
 * wider underneath it, keeps the same rhythm.
 */
internal fun arcStroke(
    density: Density,
    widthPx: Float,
    stability: Stability,
    patternWidthPx: Float = widthPx,
): Stroke =
    with(density) {
        val gap = patternWidthPx + DotGap.toPx()
        val intervals =
            when (stability) {
                Stability.STABLE -> null
                Stability.UNSTABLE -> floatArrayOf(DOT_DASH_PX, gap)
                Stability.NOT_MEASURED -> floatArrayOf(DashOn.toPx(), gap, DOT_DASH_PX, gap)
                Stability.UNKNOWN -> floatArrayOf(DashOn.toPx(), DashOff.toPx())
            }
        val round = stability == Stability.UNSTABLE || stability == Stability.NOT_MEASURED
        Stroke(
            width = widthPx,
            cap = if (round) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = intervals?.let { PathEffect.dashPathEffect(it) },
        )
    }

private fun arcStyle(
    density: Density,
    segment: RouteSegment,
    color: Color,
): ArcStyle {
    val width = with(density) { arcWidthDp(MapLegendContent.widthOf(segment.speed)).toPx() }
    val casingWidth = width + with(density) { ArcCasingExtra.toPx() }
    return ArcStyle(
        color = color,
        stroke = arcStroke(density, width, segment.stability),
        casing = arcStroke(density, casingWidth, segment.stability, patternWidthPx = width),
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

/**
 * Each arc as far as [shown] has drawn it, then the stops the arcs have reached and their labels.
 * [headPulse] is the phase of the ring around the newest stop while the trace runs, or null once it
 * has finished.
 */
internal fun DrawScope.drawRoute(
    drawing: RouteDrawing,
    geometry: MapGeometry,
    shown: (SegmentKey) -> Float,
    selection: MapSelection?,
    headPulse: Float?,
) {
    val map = drawing.map
    // The drawing and geometry of one map are replaced together; skip a frame that pairs them across maps.
    if (geometry.arcs.size != map.segments.size || geometry.stopPoints.size != map.allStops.size) return
    drawArcs(drawing, geometry.arcs, shown, (selection as? MapSelection.Segment)?.key)
    // A route stop appears once the arc arriving at it has been drawn; disputed stops are off the route.
    val visible =
        map.allStops.indices.map { index ->
            val arriving = map.segments.getOrNull(index - 1)
            index >= map.stops.size || arriving == null || shown(arriving.key) >= 1f
        }
    val selectedHops = (selection as? MapSelection.Stops)?.hopNumbers.orEmpty()
    map.allStops.forEachIndexed { index, stop ->
        if (visible[index]) drawStop(stop, geometry.stopPoints[index], drawing.stopColors[index], drawing, selectedHops)
    }
    geometry.labels.forEach { label ->
        if (geometry.clusters[label.cluster].members.any { visible[it] }) drawLabel(label, drawing)
    }
    val head = map.stops.lastIndex
    val headDrawn = map.segments.getOrNull(head - 1)?.let { shown(it.key) >= 1f } ?: true
    if (headPulse != null && head >= 0 && headDrawn) {
        drawHeadRing(geometry.stopPoints[head], headPulse, drawing.colors.selected)
    }
}

private fun DrawScope.drawHeadRing(
    point: ScreenPoint,
    pulse: Float,
    color: Color,
) {
    val radius = MarkerRadius.toPx() + HeadRingGrowth.toPx() * pulse
    drawCircle(
        color = color.copy(alpha = 1f - pulse),
        radius = radius,
        center = Offset(point.x, point.y),
        style = Stroke(SelectedRingWidth.toPx()),
    )
}

private fun DrawScope.drawArcs(
    drawing: RouteDrawing,
    arcs: List<ArcCurve>,
    shown: (SegmentKey) -> Float,
    selected: SegmentKey?,
) {
    val path = drawing.arcPath
    val casingColor = drawing.colors.surface.copy(alpha = CASING_ALPHA)
    arcs.forEachIndexed { index, arc ->
        val key = drawing.map.segments[index].key
        val fraction = shown(key).coerceIn(0f, 1f)
        if (fraction <= 0f) return@forEachIndexed
        val curve = if (fraction < 1f) arc.truncated(fraction) else arc
        path.reset()
        path.moveTo(curve.startX, curve.startY)
        path.quadraticTo(curve.controlX, curve.controlY, curve.endX, curve.endY)
        val style = drawing.styles[index]
        if (key == selected) {
            val halo = style.stroke.width + SelectedArcHalo.toPx()
            drawPath(path, drawing.colors.selected, style = Stroke(halo, cap = StrokeCap.Round))
        }
        drawPath(path, casingColor, style = style.casing)
        drawPath(path, style.color, style = style.stroke)
    }
}

private fun DrawScope.drawStop(
    stop: RouteStop,
    point: ScreenPoint,
    fill: Color,
    drawing: RouteDrawing,
    selectedHops: List<Int>,
) {
    val colors = drawing.colors
    val center = Offset(point.x, point.y)
    val radius = MarkerRadius.toPx()
    val ringWidth = MarkerRingWidth.toPx()
    if (stop.hops.any { it.hopNumber in selectedHops }) {
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
}

private fun DrawScope.drawLabel(
    label: PlacedLabel,
    drawing: RouteDrawing,
) {
    val pad = LabelPadding.toPx()
    val box = label.box
    drawRoundRect(
        color = drawing.colors.surface.copy(alpha = LABEL_BACKGROUND_ALPHA),
        topLeft = Offset(box.left, box.top),
        size = Size(box.right - box.left, box.bottom - box.top),
        cornerRadius = CornerRadius(pad * 2),
    )
    drawText(drawing.label(label.text), topLeft = Offset(box.left + pad, box.top + pad))
}
