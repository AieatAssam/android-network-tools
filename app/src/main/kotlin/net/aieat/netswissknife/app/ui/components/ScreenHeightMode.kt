package net.aieat.netswissknife.app.ui.components

/**
 * Window height (dp) below which hero headers collapse to a compact form. Phone landscape is about
 * 353 to 400dp high; portrait phones and tablets are well above this.
 */
const val COMPACT_HERO_HEIGHT_DP = 480

/** True when a screen this short should use the compact hero header (PHA-21, PHA-23, PHB-25). */
fun isCompactHeroHeight(heightDp: Int): Boolean = heightDp < COMPACT_HERO_HEIGHT_DP

/** Window width (dp) below which the hero subtitle wraps to three lines at font 1.3 (PHA-21). */
const val COMPACT_HERO_WIDTH_DP = 380

/** True when a window this narrow should use the compact hero, whatever its height (PHA-21). */
fun isCompactHeroWidth(widthDp: Int): Boolean = widthDp < COMPACT_HERO_WIDTH_DP

/** Compact hero when the window is short or narrow (PHA-21). */
fun isCompactHero(
    heightDp: Int,
    widthDp: Int,
): Boolean = isCompactHeroHeight(heightDp) || isCompactHeroWidth(widthDp)

/**
 * Number of tile columns that fit [availableWidthDp] when each tile is at least [tileMinDp] wide
 * with [gapDp] between tiles. Matches `GridCells.Adaptive`, so the Home rows keep the same count
 * they had before the rows were laid out by hand. Never less than one.
 */
fun homeGridColumns(
    availableWidthDp: Int,
    tileMinDp: Int = 160,
    gapDp: Int = 12,
): Int = ((availableWidthDp + gapDp) / (tileMinDp + gapDp)).coerceAtLeast(1)
