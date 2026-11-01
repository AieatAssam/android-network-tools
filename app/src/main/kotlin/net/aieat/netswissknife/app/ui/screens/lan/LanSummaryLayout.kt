package net.aieat.netswissknife.app.ui.screens.lan

/** Minimum width of one stat tile at font scale 1.0, before the font-scale multiplier. */
internal const val LAN_STAT_BASE_TILE_DP = 84f

/** Upper bound on stat tiles per row; the summary never lays out more than three across. */
internal const val LAN_STAT_MAX_COLUMNS = 3

/**
 * Number of stat tiles that fit side by side in [availableWidthDp].
 *
 * The per-tile minimum grows with the user's font scale so that labels still fit
 * without truncation at large text sizes; the result is clamped to 1..min(3, [statCount]).
 */
internal fun lanStatColumnCount(
    availableWidthDp: Float,
    fontScale: Float,
    statCount: Int,
    spacingDp: Float = 8f,
): Int {
    if (statCount <= 1) return 1
    val minTileDp = LAN_STAT_BASE_TILE_DP * fontScale.coerceAtLeast(1f)
    val fit = ((availableWidthDp + spacingDp) / (minTileDp + spacingDp)).toInt()
    return fit.coerceIn(1, minOf(LAN_STAT_MAX_COLUMNS, statCount))
}

/** Splits [stats] into rows of at most [columns] items, preserving order. */
internal fun <T> lanStatRows(
    stats: List<T>,
    columns: Int,
): List<List<T>> = stats.chunked(columns.coerceAtLeast(1))
