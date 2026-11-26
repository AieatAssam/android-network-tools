package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.min

/** Height of the route map for the space it has, in dp. */
object MapSizing {
    /** Width to height of the map on a short window. */
    const val WIDE_ASPECT = 1.6f

    /** The squarest the map gets on a tall window. */
    const val TALL_ASPECT = 1.1f

    /** Share of the window height the map may take on a tall window. */
    const val WINDOW_SHARE = 0.45f

    const val MIN_HEIGHT_DP = 220f
    const val MAX_HEIGHT_DP = 640f

    /** Legend columns: two once the card is at least this wide. */
    const val TWO_COLUMN_LEGEND_FROM_DP = 440f

    /**
     * Map height for a [widthDp] wide card in a window [windowHeightDp] tall: [WIDE_ASPECT] at least, and
     * up to [WINDOW_SHARE] of a tall window, never squarer than [TALL_ASPECT].
     */
    fun heightDp(
        widthDp: Float,
        windowHeightDp: Float,
    ): Float {
        val wide = widthDp / WIDE_ASPECT
        val tall = minOf(widthDp / TALL_ASPECT, windowHeightDp * WINDOW_SHARE)
        return maxOf(wide, tall).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }

    /**
     * Canvas height for a map [widthPx] wide that [heightDp] would make [heightPx] tall: that height, or less
     * when the fit of [bounds] is wider than the world view, so the world fills the canvas top to bottom
     * with no band beyond the poles. Each pass fits again at the smaller height, which can only zoom out.
     */
    fun fittedHeightPx(
        bounds: GeoBounds?,
        widthPx: Float,
        heightPx: Float,
        insets: FitInsets,
    ): Float {
        if (bounds == null || widthPx <= 0f || heightPx <= 0f) return heightPx
        var height = heightPx
        repeat(FIT_HEIGHT_PASSES) {
            val world = (WORLD_LAT_SPAN * MapViewport.fit(bounds, widthPx, height, insets).pxPerDegree).toFloat()
            height = min(height, world)
        }
        return height
    }

    private const val FIT_HEIGHT_PASSES = 4
    private const val WORLD_LAT_SPAN = 180.0

    fun legendColumns(widthDp: Float): Int = if (widthDp >= TWO_COLUMN_LEGEND_FROM_DP) 2 else 1
}
