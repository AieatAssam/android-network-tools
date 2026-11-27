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
 * with [gapDp] between tiles. Home tiles use a 150dp minimum so a 520dp-wide tablet content area
 * (488dp after the gutters) gets three columns. Never less than one.
 */
fun homeGridColumns(
    availableWidthDp: Int,
    tileMinDp: Int = 150,
    gapDp: Int = 12,
): Int = ((availableWidthDp + gapDp) / (tileMinDp + gapDp)).coerceAtLeast(1)
