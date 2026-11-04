package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Focus target that takes focus when no text field should. Provided by [FocusSinkHost]. */
val LocalFocusSink = compositionLocalOf<FocusRequester?> { null }

/**
 * Wraps the activity content in a 1dp invisible focus target placed first in traversal order.
 *
 * Why it exists: on Android 8.0 and 8.1 (API 26-27) the framework does not keep focus where Compose
 * leaves it. When the window regains focus, or after the activity is recreated, the framework hands
 * focus to the first focusable view of the window. Compose's [FocusManager.clearFocus] is also
 * undone by that re-grant, so the first text field on screen (the DNS domain, the Port Scanner port
 * field) took focus back and reopened the soft keyboard. From API 28 the framework leaves focus
 * alone and the problem does not occur.
 *
 * With the sink first, that re-grant lands on the sink, not on a text field. The sink has no
 * semantics (TalkBack does not see it) and no indication (no focus highlight), so it is invisible.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun FocusSinkHost(content: @Composable () -> Unit) {
    val sink = remember { FocusRequester() }
    CompositionLocalProvider(LocalFocusSink provides sink) {
        Box {
            Box(
                Modifier
                    .size(1.dp)
                    .focusRequester(sink)
                    .focusable()
                    .clearAndSetSemantics {},
            )
            content()
        }
    }
}

/** Moves focus to the sink so no text field keeps it; clears focus when no sink is provided. */
private fun moveFocusToSink(
    focusManager: FocusManager,
    sink: FocusRequester?,
) {
    val moved = runCatching { sink?.requestFocus() }.getOrNull() == true
    if (!moved) focusManager.clearFocus()
}

/**
 * Returns the single app-wide keyboard dismissal: it moves focus to the [FocusSinkHost] sink and
 * hides the soft keyboard. Use it for every "done" action (Look up, Scan, Start, Clear, a recent
 * chip) instead of calling clearFocus() or keyboard.hide() directly, so the focus and IME behaviour
 * stays the same on every API level.
 *
 * Intended behaviour: the keyboard stays closed after the activity is recreated (rotation, dark mode,
 * font change). If the keyboard is open when the screen rotates, the rotation closes it and no field
 * is re-focused; the user taps the field again to reopen the keyboard. Restoring focus with the
 * keyboard open was tried and dropped (WP29): the keyboard stayed shown with no field focused.
 */
@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val sink = LocalFocusSink.current
    return remember(focusManager, keyboard, sink) {
        {
            moveFocusToSink(focusManager, sink)
            keyboard?.hide()
        }
    }
}
