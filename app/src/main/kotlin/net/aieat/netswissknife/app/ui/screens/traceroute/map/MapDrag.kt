package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.abs

/** Decides who owns a one-finger drag on the map: the map, or the page that scrolls around it. */
object MapDrag {
    /**
     * True when the map should take a one-finger drag of ([dxPx], [dyPx]): the view is zoomed in past
     * [fitted] and can still move along the drag's main direction. Otherwise the drag is left to the page,
     * so a map at its pan limit, or not zoomed in, never traps the page's scroll.
     */
    fun mapTakesDrag(
        viewport: MapViewport,
        fitted: MapViewport,
        dxPx: Float,
        dyPx: Float,
        widthPx: Float,
        heightPx: Float,
        anchorLon: Double,
    ): Boolean {
        if (!viewport.isZoomedInFrom(fitted) || (dxPx == 0f && dyPx == 0f)) return false
        return if (abs(dyPx) > abs(dxPx)) {
            viewport.pannedBy(0f, dyPx, widthPx, heightPx, anchorLon).centerLat != viewport.centerLat
        } else {
            viewport.pannedBy(dxPx, 0f, widthPx, heightPx, anchorLon).centerLon != viewport.centerLon
        }
    }
}
