@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

package net.aieat.netswissknife.app.ui.screens

import android.content.res.AssetManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcCurve
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ArcGeometry
import net.aieat.netswissknife.app.ui.screens.traceroute.map.GeoBounds
import net.aieat.netswissknife.app.ui.screens.traceroute.map.MapViewport
import net.aieat.netswissknife.app.ui.screens.traceroute.map.RouteStop
import net.aieat.netswissknife.app.ui.screens.traceroute.map.ScreenPoint
import net.aieat.netswissknife.app.ui.screens.traceroute.map.SegmentKey
import net.aieat.netswissknife.app.ui.screens.traceroute.map.TraceRouteMap
import net.aieat.netswissknife.app.ui.screens.traceroute.map.WorldLand
import net.aieat.netswissknife.app.ui.screens.traceroute.map.WorldLandParser
import net.aieat.netswissknife.app.ui.screens.traceroute.map.nearestPointIndex
import net.aieat.netswissknife.app.ui.theme.AppMotion
import java.io.IOException

private const val MAP_ASPECT_RATIO = 1.6f
private const val ARC_STEP_MS = 220
private const val ARC_MAX_STAGGER_MS = 1_500
private const val ARC_DURATION_MS = 450
private const val FOLLOW_DURATION_MS = 600
private const val HEAD_PULSE_MS = 1_400

// A stop this close to the canvas edge counts as outside the view.
private const val FOLLOW_MARGIN_FRACTION = 0.06
private val MapMinHeight = 220.dp
private val MapMaxHeight = 440.dp
private val StopTouchRadius = 24.dp

/** The bundled base map, parsed once per process off the main thread. */
private object WorldLandCache {
    private val mutex = Mutex()

    @Volatile
    var land: WorldLand? = null
        private set

    suspend fun load(assets: AssetManager): WorldLand? =
        land ?: mutex.withLock {
            land ?: withContext(Dispatchers.IO) {
                try {
                    assets.open(WorldLandParser.ASSET_PATH).use { WorldLandParser.parse(it.readBytes()) }
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }?.also { land = it }
        }
}

@Composable
private fun rememberWorldLandPath(): Path? {
    val assets = LocalContext.current.assets
    val land by produceState(WorldLandCache.land, assets) { value = WorldLandCache.load(assets) }
    // Built once in degree space (x = longitude, y = -latitude); the viewport is applied as a
    // canvas transform, so panning and zooming never rebuild it.
    return remember(land) { land?.let(::landPath) }
}

/** Everything the canvas draws for one viewport, rebuilt only when the viewport or size changes. */
internal class MapGeometry(
    val arcs: List<ArcCurve>,
    val stopPoints: List<ScreenPoint>,
    val disputedPoints: List<ScreenPoint>,
)

private fun geometryOf(
    map: TraceRouteMap,
    viewport: MapViewport,
    size: IntSize,
): MapGeometry {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val point = { stop: RouteStop -> ScreenPoint(viewport.xOf(stop.lon, w), viewport.yOf(stop.lat, h)) }
    val stopPoints = map.stops.map(point)
    val arcs =
        map.segments.map { segment ->
            val from = stopPoints[segment.fromStop]
            val to = stopPoints[segment.toStop]
            ArcGeometry.arc(from.x, from.y, to.x, to.y)
        }
    return MapGeometry(arcs, stopPoints, map.disputedStops.map(point))
}

/**
 * Viewport of a route that may still be growing. It follows the route, moving smoothly when a new stop
 * falls outside the view, until the user pans or zooms; then the view stays where they put it.
 */
@Stable
private class RouteMapViewState(
    initialMap: TraceRouteMap,
) {
    var map by mutableStateOf(initialMap)
    private val stops by derivedStateOf { map.stops + map.disputedStops }
    private val bounds by derivedStateOf { GeoBounds.of(stops.map { it.lat to it.lon }) }
    private val anchorLon: Double get() = bounds?.centerLon ?: 0.0
    private var canvasSize by mutableStateOf(IntSize.Zero)
    private val width: Float get() = canvasSize.width.toFloat()
    private val height: Float get() = canvasSize.height.toFloat()
    val fitted by derivedStateOf { MapViewport.fit(bounds, width, height) }

    /** The view following the route, or null until the canvas has a size. */
    private var autoViewport by mutableStateOf<MapViewport?>(null)
    private var followedEmptyRoute = true

    var userViewport by mutableStateOf<MapViewport?>(null)
        private set
    val viewport: MapViewport get() = userViewport ?: autoViewport ?: fitted
    val geometry by derivedStateOf { geometryOf(map, viewport, canvasSize) }

    fun resize(size: IntSize) {
        if (size == canvasSize) return
        canvasSize = size
        userViewport = null
        autoViewport = null
    }

    fun resetView() {
        userViewport = null
        autoViewport = fitted
    }

    fun isZoomedIn(): Boolean = viewport.isZoomedInFrom(fitted)

    /** Move the automatic view to [target] when the route has outgrown it, unless the user has moved the map. */
    suspend fun follow(target: MapViewport) {
        if (canvasSize == IntSize.Zero || userViewport != null) return
        val current = autoViewport
        val routeEmpty = stops.isEmpty()
        val outgrown = stops.any { !current.showsStop(it) }
        val firstStops = followedEmptyRoute && !routeEmpty
        followedEmptyRoute = routeEmpty
        when {
            current == null -> {
                autoViewport = target
            }

            firstStops || outgrown -> {
                val move = Animatable(0f)
                move.animateTo(1f, AppMotion.enter(FOLLOW_DURATION_MS)) {
                    if (userViewport == null) autoViewport = current.lerp(target, value)
                }
            }
        }
    }

    private fun MapViewport?.showsStop(stop: RouteStop): Boolean {
        val viewport = this ?: return false
        return viewport.shows(stop.lat, stop.lon, width, height, FOLLOW_MARGIN_FRACTION)
    }

    fun transform(
        zoom: Float,
        pan: Offset,
        centroid: Offset,
    ) {
        userViewport =
            viewport
                .zoomedBy(zoom, centroid.x, centroid.y, width, height, anchorLon)
                .pannedBy(pan.x, pan.y, width, height, anchorLon)
    }

    /** First hop number of the stop within [radiusPx] of [tap], or null. */
    fun hopAt(
        tap: Offset,
        radiusPx: Float,
    ): Int? {
        val points = geometry.stopPoints + geometry.disputedPoints
        val index = nearestPointIndex(points, tap.x, tap.y, radiusPx) ?: return null
        return stops
            .getOrNull(index)
            ?.hops
            ?.first()
            ?.hopNumber
    }
}

/**
 * Draw-in progress of each arc, kept by [SegmentKey]: an arc that has been drawn stays drawn while the
 * route grows, and each new arc draws in once. The animations run in [scope], which outlives any one map.
 */
@Stable
internal class ArcReveal(
    private val scope: CoroutineScope,
) {
    private val progress = mutableStateMapOf<SegmentKey, Animatable<Float, AnimationVector1D>>()

    fun sync(map: TraceRouteMap) {
        val keys = map.segments.map { it.key }
        progress.keys.retainAll(keys.toSet())
        val added = keys.filter { it !in progress }
        if (added.isEmpty()) return
        val step = (ARC_MAX_STAGGER_MS / added.size).coerceAtMost(ARC_STEP_MS)
        added.forEachIndexed { index, key ->
            val arc = Animatable(0f)
            progress[key] = arc
            scope.launch {
                delay((index * step).toLong())
                arc.animateTo(1f, AppMotion.enter(ARC_DURATION_MS))
            }
        }
    }

    /** 0 before the arc starts drawing, 1 once it is complete. */
    fun shown(key: SegmentKey): Float = progress[key]?.value ?: 0f
}

/** 0 to 1 and back to 0, repeating while [live]; the ring around the newest stop of a running trace. */
@Composable
private fun rememberHeadPulse(live: Boolean): Animatable<Float, AnimationVector1D> {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(live) {
        if (!live) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(HEAD_PULSE_MS, easing = LinearEasing))
        }
    }
    return pulse
}

@Composable
internal fun RouteMapCanvas(
    map: TraceRouteMap,
    live: Boolean,
    colorFor: (Long?) -> Color,
    selectedHop: Int?,
    onSelect: (Int?) -> Unit,
) {
    val landPath = rememberWorldLandPath()
    val state = remember { RouteMapViewState(map) }
    state.map = map
    val drawing = rememberRouteDrawing(map, colorFor)
    val scope = rememberCoroutineScope()
    val reveal = remember(scope) { ArcReveal(scope) }
    LaunchedEffect(map) { reveal.sync(map) }
    LaunchedEffect(state) { snapshotFlow { state.fitted }.collectLatest { state.follow(it) } }
    val shown: (SegmentKey) -> Float = remember(reveal) { reveal::shown }
    val pulse = rememberHeadPulse(live)
    val description = mapDescription(map, live)
    val currentSelected by rememberUpdatedState(selectedHop)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val touchRadius = with(LocalDensity.current) { StopTouchRadius.toPx() }
    Box {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MapMinHeight, max = MapMaxHeight)
                    .aspectRatio(MAP_ASPECT_RATIO)
                    .clipToBounds()
                    .onSizeChanged(state::resize)
                    .testTag("traceroute_map_canvas")
                    .semantics { contentDescription = description }
                    .pointerInput(state) {
                        mapGestures(
                            isZoomedIn = state::isZoomedIn,
                            onTransform = state::transform,
                            onTap = { tap ->
                                val hit = state.hopAt(tap, touchRadius)
                                currentOnSelect(if (hit == currentSelected) null else hit)
                            },
                        )
                    },
        ) {
            drawRect(drawing.colors.ocean)
            landPath?.let { drawLand(it, state.viewport, drawing.colors.land) }
            val head = if (live) pulse.value else null
            drawRoute(drawing, state.geometry, shown, selectedHop, head)
        }
        if (state.userViewport != null) {
            FilledTonalIconButton(
                onClick = state::resetView,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("traceroute_map_reset"),
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.traceroute_map_reset),
                )
            }
        }
    }
}

/**
 * Pan and zoom without fighting the screen's vertical scroll: two fingers always transform the
 * map; one finger moves it only once zoomed in past the automatic fit, otherwise the drag is
 * left to the page. A press that never passes touch slop is a tap.
 */
private suspend fun PointerInputScope.mapGestures(
    isZoomedIn: () -> Boolean,
    onTransform: (zoom: Float, pan: Offset, centroid: Offset) -> Unit,
    onTap: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isTap = true
        var travel = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            val pan = event.calculatePan()
            travel += pan
            val pastSlop = travel.getDistance() > viewConfiguration.touchSlop
            if (pressed >= 2 || pastSlop) isTap = false
            val transform = pressed >= 2 || (pressed == 1 && !isTap && isZoomedIn())
            if (transform) {
                onTransform(event.calculateZoom(), pan, event.calculateCentroid(useCurrent = true))
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (isTap) onTap(down.position)
    }
}
