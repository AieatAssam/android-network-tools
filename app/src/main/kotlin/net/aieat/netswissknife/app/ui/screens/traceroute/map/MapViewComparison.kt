package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.abs

/** Centre offset, in pixels, under which two views count as the same. */
private const val SAME_VIEW_PX = 1.0

/** Zoom ratio difference under which two views count as the same. */
const val SAME_VIEW_ZOOM_TOLERANCE = 0.01

/**
 * True when this view and [other] look the same on screen: the centres are within a pixel of each other
 * and the zooms within [SAME_VIEW_ZOOM_TOLERANCE]. The map's reset button is enabled only when the view
 * does not look like the automatic fit.
 */
fun MapViewport.looksLike(other: MapViewport): Boolean {
    val dx = (other.centerLon - centerLon) * pxPerDegree
    val dy = (other.centerLat - centerLat) * pxPerDegree
    val sameCentre = abs(dx) < SAME_VIEW_PX && abs(dy) < SAME_VIEW_PX
    return sameCentre && abs(other.pxPerDegree / pxPerDegree - 1.0) < SAME_VIEW_ZOOM_TOLERANCE
}
