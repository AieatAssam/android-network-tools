package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
 * Canvas margins, in pixels, that the automatic fit keeps the route out of: room for marker labels on every
 * side, and for the zoom buttons on the end side.
 */
data class FitInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        val NONE = FitInsets(0f, 0f, 0f, 0f)
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

    /** True when ([lat], [lon]) is on screen, at least [marginFraction] of the canvas size from each edge. */
    fun shows(
        lat: Double,
        lon: Double,
        widthPx: Float,
        heightPx: Float,
        marginFraction: Double,
    ): Boolean {
        val x = xOf(lon, widthPx)
        val y = yOf(lat, heightPx)
        val marginX = widthPx * marginFraction
        val marginY = heightPx * marginFraction
        return x >= marginX && x <= widthPx - marginX && y >= marginY && y <= heightPx - marginY
    }

    /** The view [t] of the way from this one to [to]: the centre moves evenly and the zoom changes evenly. */
    fun lerp(
        to: MapViewport,
        t: Float,
    ): MapViewport {
        val f = t.coerceIn(0f, 1f).toDouble()
        if (f == 0.0 || f == 1.0) return if (f == 0.0) this else to
        return MapViewport(
            centerLon = centerLon + (to.centerLon - centerLon) * f,
            centerLat = centerLat + (to.centerLat - centerLat) * f,
            pxPerDegree = pxPerDegree * (to.pxPerDegree / pxPerDegree).pow(f),
        )
    }

    /** True when this view is closer than [fitted], the automatic fit of the route. */
    fun isZoomedInFrom(fitted: MapViewport): Boolean = pxPerDegree > fitted.pxPerDegree * ZOOMED_IN_TOLERANCE

    /**
     * Zoom by [factor] about the canvas point ([focusX], [focusY]), which stays under the fingers. A fit of a
     * route wider than the world view is below the widest zoom; zooming out from it, or a pinch that does not
     * zoom, keeps its scale rather than snapping in.
     */
    fun zoomedBy(
        factor: Float,
        focusX: Float,
        focusY: Float,
        widthPx: Float,
        heightPx: Float,
        anchorLon: Double,
    ): MapViewport {
        val widest = min(minPxPerDegree(widthPx, heightPx), pxPerDegree)
        val scale = (pxPerDegree * factor).coerceIn(widest, maxPxPerDegree(widthPx))
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
        private const val MAX_INSET_FRACTION = 0.6f
        private const val FIT_SHRINK = 0.9
        private const val MAX_FIT_SHRINK_STEPS = 40
        private const val EDGE_TOLERANCE_PX = 0.5

        /** Zoom factor of the zoom in and zoom out buttons and of a double tap. */
        const val ZOOM_STEP = 2f

        /** Widest zoom: the whole world fits the canvas. */
        fun minPxPerDegree(
            widthPx: Float,
            heightPx: Float,
        ): Double = min(widthPx / FULL_WORLD, heightPx / (2 * MAX_LAT))

        fun maxPxPerDegree(widthPx: Float): Double = widthPx / MIN_VISIBLE_SPAN_DEGREES

        /**
         * View that shows every point of [bounds] inside the canvas less [insets], with padding, or the whole
         * world when there is nothing to show. The route's centre sits at the centre of the inset area, unless
         * that would show beyond a pole; then the view zooms out until the route fits again.
         */
        fun fit(
            bounds: GeoBounds?,
            widthPx: Float,
            heightPx: Float,
            insets: FitInsets = FitInsets.NONE,
        ): MapViewport {
            if (bounds == null || widthPx <= 0f || heightPx <= 0f) {
                return MapViewport(0.0, 0.0, minPxPerDegree(widthPx, heightPx))
            }
            val inner = innerArea(widthPx, heightPx, insets)
            val padding = 1.0 + 2.0 * FIT_PADDING_FRACTION
            val spanLon = max((bounds.maxLon - bounds.minLon) * padding, MIN_FIT_SPAN_DEGREES)
            val spanLat = max((bounds.maxLat - bounds.minLat) * padding, MIN_FIT_SPAN_DEGREES)
            val innerWidth = (inner.right - inner.left).toDouble()
            val innerHeight = (inner.bottom - inner.top).toDouble()
            var scale = min(min(innerWidth / spanLon, innerHeight / spanLat), maxPxPerDegree(widthPx))
            var view = centredFit(bounds, scale, widthPx, heightPx, inner)
            var shrinks = 0
            // The pole limit can push the route off the inset area; zoom out until it fits again.
            while (!view.contains(bounds, widthPx, heightPx, inner) && shrinks < MAX_FIT_SHRINK_STEPS) {
                scale *= FIT_SHRINK
                view = centredFit(bounds, scale, widthPx, heightPx, inner)
                shrinks++
            }
            return view
        }

        /**
         * True when ([lat], [lon]) shows inside the area [fit] keeps the route in, so a growing route is
         * followed as soon as a new marker would sit under the zoom buttons or leave no room for its label.
         */
        fun keepsInView(
            viewport: MapViewport,
            lat: Double,
            lon: Double,
            widthPx: Float,
            heightPx: Float,
            insets: FitInsets,
        ): Boolean {
            val inner = innerArea(widthPx, heightPx, insets)
            val x = viewport.xOf(lon, widthPx)
            val y = viewport.yOf(lat, heightPx)
            val tolerance = 1f
            return x >= inner.left - tolerance &&
                x <= inner.right + tolerance &&
                y >= inner.top - tolerance &&
                y <= inner.bottom + tolerance
        }

        private fun innerArea(
            widthPx: Float,
            heightPx: Float,
            insets: FitInsets,
        ): PixelBox {
            // Insets never take more than most of a small canvas, so a route always has room.
            val maxX = widthPx * MAX_INSET_FRACTION
            val maxY = heightPx * MAX_INSET_FRACTION
            // Opposite insets that together take more than that shrink in proportion, so each keeps its share.
            val scaleX = min(1f, maxX / (insets.left + insets.right).coerceAtLeast(Float.MIN_VALUE))
            val scaleY = min(1f, maxY / (insets.top + insets.bottom).coerceAtLeast(Float.MIN_VALUE))
            return PixelBox(
                left = insets.left * scaleX,
                top = insets.top * scaleY,
                right = widthPx - insets.right * scaleX,
                bottom = heightPx - insets.bottom * scaleY,
            )
        }

        private fun centredFit(
            bounds: GeoBounds,
            scale: Double,
            widthPx: Float,
            heightPx: Float,
            inner: PixelBox,
        ): MapViewport {
            // Puts the route's centre at the centre of the inset area rather than of the canvas.
            return MapViewport(
                centerLon = bounds.centerLon - (inner.centerX - widthPx / 2.0) / scale,
                centerLat = bounds.centerLat + (inner.centerY - heightPx / 2.0) / scale,
                pxPerDegree = scale,
            ).clamped(heightPx, bounds.centerLon)
        }

        private fun MapViewport.contains(
            bounds: GeoBounds,
            widthPx: Float,
            heightPx: Float,
            inner: PixelBox,
        ): Boolean {
            val left = xOf(bounds.minLon, widthPx)
            val right = xOf(bounds.maxLon, widthPx)
            val top = yOf(bounds.maxLat, heightPx)
            val bottom = yOf(bounds.minLat, heightPx)
            return left >= inner.left - EDGE_TOLERANCE_PX &&
                right <= inner.right + EDGE_TOLERANCE_PX &&
                top >= inner.top - EDGE_TOLERANCE_PX &&
                bottom <= inner.bottom + EDGE_TOLERANCE_PX
        }
    }
}
