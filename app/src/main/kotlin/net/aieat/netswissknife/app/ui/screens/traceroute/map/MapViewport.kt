package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Bounding box of map points; longitudes may lie outside ±180 after antimeridian unwrapping. */
data class GeoBounds(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
) {
    val centerLon: Double get() = (minLon + maxLon) / 2.0
    val centerLat: Double get() = (minLat + maxLat) / 2.0

    companion object {
        /** Bounds of [latLons] given as (latitude, longitude) pairs, or null when empty. */
        fun of(latLons: List<Pair<Double, Double>>): GeoBounds? {
            if (latLons.isEmpty()) return null
            return GeoBounds(
                minLon = latLons.minOf { it.second },
                maxLon = latLons.maxOf { it.second },
                minLat = latLons.minOf { it.first },
                maxLat = latLons.maxOf { it.first },
            )
        }
    }
}

/**
 * Equirectangular view of the map: one degree is [pxPerDegree] pixels both across and
 * down, so shapes keep their aspect ratio at any zoom. North is up.
 */
data class MapViewport(
    val centerLon: Double,
    val centerLat: Double,
    val pxPerDegree: Double,
) {
    fun xOf(
        lon: Double,
        widthPx: Float,
    ): Float = (widthPx / 2.0 + (lon - centerLon) * pxPerDegree).toFloat()

    fun yOf(
        lat: Double,
        heightPx: Float,
    ): Float = (heightPx / 2.0 - (lat - centerLat) * pxPerDegree).toFloat()

    fun lonAt(
        x: Float,
        widthPx: Float,
    ): Double = centerLon + (x - widthPx / 2.0) / pxPerDegree

    fun latAt(
        y: Float,
        heightPx: Float,
    ): Double = centerLat - (y - heightPx / 2.0) / pxPerDegree

    /** Longitude offsets (multiples of 360) at which a copy of the world is at least partly visible. */
    fun worldCopyOffsets(widthPx: Float): List<Double> {
        val halfSpan = widthPx / 2.0 / pxPerDegree
        val first = ceil((centerLon - halfSpan - HALF_WORLD) / FULL_WORLD).toInt()
        val last = floor((centerLon + halfSpan + HALF_WORLD) / FULL_WORLD).toInt()
        return (first..last)
            .map { it * FULL_WORLD }
            .filter { offset ->
                centerLon + halfSpan > offset - HALF_WORLD && centerLon - halfSpan < offset + HALF_WORLD
            }
    }

    /** True when this view is closer than [fitted], the automatic fit of the route. */
    fun isZoomedInFrom(fitted: MapViewport): Boolean = pxPerDegree > fitted.pxPerDegree * ZOOMED_IN_TOLERANCE

    /** Zoom by [factor] about the canvas point ([focusX], [focusY]), which stays under the fingers. */
    fun zoomedBy(
        factor: Float,
        focusX: Float,
        focusY: Float,
        widthPx: Float,
        heightPx: Float,
        anchorLon: Double,
    ): MapViewport {
        val scale = (pxPerDegree * factor).coerceIn(minPxPerDegree(widthPx, heightPx), maxPxPerDegree(widthPx))
        val focusLon = lonAt(focusX, widthPx)
        val focusLat = latAt(focusY, heightPx)
        val zoomed =
            MapViewport(
                centerLon = focusLon - (focusX - widthPx / 2.0) / scale,
                centerLat = focusLat + (focusY - heightPx / 2.0) / scale,
                pxPerDegree = scale,
            )
        return zoomed.clamped(heightPx, anchorLon)
    }

    /** Move the map with a finger drag of ([dxPx], [dyPx]). */
    fun pannedBy(
        dxPx: Float,
        dyPx: Float,
        widthPx: Float,
        heightPx: Float,
        anchorLon: Double,
    ): MapViewport {
        if (widthPx <= 0f) return this
        return copy(centerLon = centerLon - dxPx / pxPerDegree, centerLat = centerLat + dyPx / pxPerDegree)
            .clamped(heightPx, anchorLon)
    }

    /** Keep the view on the globe vertically and within half a world of [anchorLon]. */
    fun clamped(
        heightPx: Float,
        anchorLon: Double,
    ): MapViewport {
        val halfHeight = heightPx / 2.0 / pxPerDegree
        val lat = if (halfHeight >= MAX_LAT) 0.0 else centerLat.coerceIn(-MAX_LAT + halfHeight, MAX_LAT - halfHeight)
        val lon = centerLon.coerceIn(anchorLon - HALF_WORLD, anchorLon + HALF_WORLD)
        return copy(centerLon = lon, centerLat = lat)
    }

    companion object {
        /** Padding added on each side of the route's bounding box, as a fraction of its span. */
        const val FIT_PADDING_FRACTION = 0.15

        /** Smallest span the automatic fit shows, so a one-city route is not zoomed to a dot. */
        const val MIN_FIT_SPAN_DEGREES = 20.0

        /** Smallest span the user can zoom in to, across the canvas width. */
        const val MIN_VISIBLE_SPAN_DEGREES = 2.0

        private const val FULL_WORLD = 360.0
        private const val HALF_WORLD = 180.0
        private const val MAX_LAT = 90.0
        private const val ZOOMED_IN_TOLERANCE = 1.01

        /** Widest zoom: the whole world fits the canvas. */
        fun minPxPerDegree(
            widthPx: Float,
            heightPx: Float,
        ): Double = min(widthPx / FULL_WORLD, heightPx / (2 * MAX_LAT))

        fun maxPxPerDegree(widthPx: Float): Double = widthPx / MIN_VISIBLE_SPAN_DEGREES

        /** View that shows [bounds] with padding, or the whole world when there is nothing to show. */
        fun fit(
            bounds: GeoBounds?,
            widthPx: Float,
            heightPx: Float,
        ): MapViewport {
            if (bounds == null) return MapViewport(0.0, 0.0, minPxPerDegree(widthPx, heightPx))
            val padding = 1.0 + 2.0 * FIT_PADDING_FRACTION
            val spanLon = max((bounds.maxLon - bounds.minLon) * padding, MIN_FIT_SPAN_DEGREES)
            val spanLat = max((bounds.maxLat - bounds.minLat) * padding, MIN_FIT_SPAN_DEGREES)
            val scale = min(widthPx / spanLon, heightPx / spanLat).coerceAtLeast(minPxPerDegree(widthPx, heightPx))
            return MapViewport(bounds.centerLon, bounds.centerLat, scale).clamped(heightPx, bounds.centerLon)
        }
    }
}
