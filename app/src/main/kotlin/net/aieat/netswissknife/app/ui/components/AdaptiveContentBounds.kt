package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reading-width cap for tool content on tablet-class windows. Tool forms and result cards
 * stretched across a 960dp landscape tablet were hard to scan (A:SHELL-02, C:SUB-04,
 * B:GAP-01), so content is held to a column-width that still fits a two-field form.
 */
val AdaptiveContentMaxWidth = 640.dp

/**
 * Caps tool-screen content to [AdaptiveContentMaxWidth] and centers it on wide
 * windows (tablet/foldable/desktop). On phone-width windows the cap never
 * binds, so this is a no-op there. Every tool screen already lays its cards
 * out with `fillMaxWidth()` against its parent, so wrapping the nav host's
 * content slot with this once reflows every screen without per-screen edits.
 */
@Composable
fun AdaptiveContentBounds(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = AdaptiveContentMaxWidth).fillMaxHeight()) {
            content()
        }
    }
}
