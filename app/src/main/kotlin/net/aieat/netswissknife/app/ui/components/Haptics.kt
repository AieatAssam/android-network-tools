package net.aieat.netswissknife.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wraps [action] so invoking it also fires a short haptic tick.
 * Use on primary tool actions (start/stop/scan) and pin toggles so taps
 * give tactile confirmation alongside the ripple.
 */
@Composable
fun hapticAction(action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
}
