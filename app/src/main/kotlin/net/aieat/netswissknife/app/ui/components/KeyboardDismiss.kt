package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first

/**
 * Returns an action that drops focus and hides the soft keyboard.
 *
 * The dismissal runs twice: once immediately, and again after the composition that follows the
 * caller's state update. A focus change or IME request made during the same input event (a tap on
 * a trailing icon, a button click that also triggers a state change) can otherwise put the field
 * back in focus after the immediate call, which reopens the keyboard over the results.
 */
@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pendingRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingRequests) {
        if (pendingRequests > 0) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    return {
        focusManager.clearFocus()
        keyboard?.hide()
        pendingRequests++
    }
}

/**
 * Keeps the keyboard closed when the activity is recreated (rotation, dark mode, font scale) while
 * it was closed before. The framework restores focus to the field the user last edited, and that
 * focus request opens the keyboard over the screen. If the keyboard was open at the time of the
 * change, the user was typing, so it is left alone.
 *
 * Place once at the root of the activity content.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun RecreatedKeyboardGuard() {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    // Saved with the activity: whether the keyboard was open the last time the content was shown.
    var imeOpenBeforeRecreation by rememberSaveable { mutableStateOf(false) }
    // Read once, before the effect below writes the current value, so the restored value is kept.
    val openBeforeRecreation = remember { imeOpenBeforeRecreation }
    LaunchedEffect(imeVisible) { imeOpenBeforeRecreation = imeVisible }
    LaunchedEffect(Unit) {
        if (!openBeforeRecreation) {
            // Focus comes back with the window: wait for it, then let one frame pass so the
            // restored focus is cleared after it has been applied.
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}
