package net.aieat.netswissknife.app.ui.components

/**
 * Room kept above a modal bottom sheet for its drag handle (the handle sits outside the
 * capped content, so the cap must leave space for it).
 */
internal const val SHEET_HANDLE_ALLOWANCE_DP = 48f

/** Largest share of the window a sheet's content may occupy. */
private const val SHEET_MAX_WINDOW_FRACTION = 0.88f

/**
 * Maximum height of a modal sheet's content, in dp.
 *
 * Content taller than this is scrolled inside the sheet instead of growing it. Without a cap,
 * tall content (such as Help) drives the sheet to the top of the window, where it has no room
 * to move down and where the system status bar pushes back on it, so a drag never moves the
 * sheet. The result never reaches above the status bar, and it is never negative.
 */
internal fun maxSheetHeightDp(
    windowHeightDp: Float,
    statusBarTopDp: Float,
): Float {
    val byFraction = windowHeightDp * SHEET_MAX_WINDOW_FRACTION
    val byStatusBar = windowHeightDp - statusBarTopDp - SHEET_HANDLE_ALLOWANCE_DP
    return minOf(byFraction, byStatusBar).coerceAtLeast(0f)
}
