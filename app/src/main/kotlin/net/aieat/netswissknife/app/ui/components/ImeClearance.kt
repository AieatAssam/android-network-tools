package net.aieat.netswissknife.app.ui.components

import kotlin.math.max

/**
 * Bottom navigation bar height in dp (Material 3 default). The navigation suite reserves this
 * height in the content slot, so the keyboard only needs to clear the part of its height that
 * sits above the bar.
 */
const val BOTTOM_NAV_BAR_HEIGHT_DP = 80

/**
 * Padding (px) the content slot needs so the keyboard does not cover the focused field.
 *
 * The content slot already ends above the bottom bar and the system navigation bar inset, which
 * together make up [reservedBelowContentPx]. The keyboard reaches up from the window bottom, so
 * the part of it that overlaps that reserved band is already accounted for. Padding by the whole
 * keyboard height as well clipped every tool screen above the keyboard (PHB-01).
 */
fun imeOverlapAboveBottomBar(
    imeHeightPx: Int,
    reservedBelowContentPx: Int,
): Int = max(0, imeHeightPx - reservedBelowContentPx)
