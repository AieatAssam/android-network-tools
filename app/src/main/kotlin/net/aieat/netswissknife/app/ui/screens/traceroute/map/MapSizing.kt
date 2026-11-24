package net.aieat.netswissknife.app.ui.screens.traceroute.map

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

    fun legendColumns(widthDp: Float): Int = if (widthDp >= TWO_COLUMN_LEGEND_FROM_DP) 2 else 1
}
