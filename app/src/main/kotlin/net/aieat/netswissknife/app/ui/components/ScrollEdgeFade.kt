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
