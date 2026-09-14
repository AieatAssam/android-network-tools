package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Standard M3 "tablet form" reading-width cap for single-column tool screens. */
val AdaptiveContentMaxWidth = 840.dp

/**
 * Caps tool-screen content to [AdaptiveContentMaxWidth] and centers it on wide
 * windows (tablet/foldable/desktop). On phone-width windows the cap never
 * binds, so this is a no-op there. Every tool screen already lays its cards
 * out with `fillMaxWidth()` against its parent, so wrapping the nav host's
 * content slot with this once reflows every screen without per-screen edits.
 */
@Composable
fun AdaptiveContentBounds(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = AdaptiveContentMaxWidth).fillMaxHeight()) {
            content()
        }
    }
}
