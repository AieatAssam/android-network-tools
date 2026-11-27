package net.aieat.netswissknife.app.ui.screens.traceroute.map

/** Where the zoom buttons zoom about. */
object MapZoom {
    /**
     * The canvas point the zoom buttons keep in place: the route's centre, so zooming in closes on the route
     * rather than on whatever the canvas centre shows; or the canvas centre once the user has [panned], so the
     * spot they moved to stays put. Falls back to the canvas centre when there is no route, and a route centre
     * off the canvas is brought to its edge, so a zoom in never moves the visible part away from the route.
     */
    fun buttonFocus(
        viewport: MapViewport,
        bounds: GeoBounds?,
        panned: Boolean,
        widthPx: Float,
        heightPx: Float,
    ): Pair<Float, Float> {
        val centre = widthPx / 2f to heightPx / 2f
        if (panned || bounds == null) return centre
        val x = viewport.xOf(bounds.centerLon, widthPx).coerceIn(0f, widthPx)
        val y = viewport.yOf(bounds.centerLat, heightPx).coerceIn(0f, heightPx)
        return x to y
    }
}
