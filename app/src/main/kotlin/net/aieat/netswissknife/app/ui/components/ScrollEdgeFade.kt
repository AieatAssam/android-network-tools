package net.aieat.netswissknife.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of the fade that marks chips cut off at a scrolling row's edge. */
val ScrollEdgeFadeWidth: Dp = 28.dp

/**
 * Fades each edge of a horizontally scrolling row only while more content is off-screen that way,
 * so a clipped chip reads as "scroll for more" rather than as the end of the list. Offscreen
 * compositing lets the DstIn mask fade the chips themselves on any background.
 */
fun Modifier.scrollEdgeFade(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    fadeWidth: Dp = ScrollEdgeFadeWidth,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fadeWidth.toPx()
            if (canScrollBackward) {
                val leading = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fadePx)
                drawRect(brush = leading, blendMode = BlendMode.DstIn)
            }
            if (canScrollForward) {
                val start = size.width - fadePx
                val trailing = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), start, size.width)
                drawRect(brush = trailing, blendMode = BlendMode.DstIn)
            }
        }

/** Height of the fade that marks rows cut off at the bottom of a vertically scrolling list. */
val BottomScrollEdgeFadeHeight: Dp = 56.dp

/**
 * Fades the bottom edge of a vertically scrolling list while more rows lie below it, so a list that
 * happens to end on a row boundary still reads as "scroll for more" rather than as the end.
 */
fun Modifier.bottomScrollEdgeFade(
    canScrollForward: Boolean,
    fadeHeight: Dp = BottomScrollEdgeFadeHeight,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (canScrollForward) {
                val start = size.height - fadeHeight.toPx()
                val trailing = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), start, size.height)
                drawRect(brush = trailing, blendMode = BlendMode.DstIn)
            }
        }
