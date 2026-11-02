package net.aieat.netswissknife.app.ui.components

import kotlin.math.max
import kotlin.math.min

/**
 * Height (px) that the focused field asks to have on screen above the keyboard.
 *
 * The field alone is not enough: the recent-host row, the error text and the field's supporting
 * text sit directly under it, so the request covers [trailingPx] below the field as well. The
 * request never asks for more than [visibleHeightPx] (the part of the window above the keyboard).
 * Asking for more would make the scroll align the top edge, which can push the field itself off
 * screen. The field is always requested in full.
 */
fun focusRevealHeightPx(
    fieldHeightPx: Int,
    trailingPx: Int,
    visibleHeightPx: Int,
): Int = max(fieldHeightPx, min(fieldHeightPx + trailingPx, visibleHeightPx))

/**
 * Room (dp) below a focused field that is revealed with it, so the recent-host row that follows
 * most inputs stays on screen. About one chip row plus the column spacing.
 */
const val IME_REVEAL_TRAILING_DP = 72
