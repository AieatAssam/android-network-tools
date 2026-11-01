package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

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

/**
 * Brings the soft keyboard back to a text field after the activity was recreated with the keyboard
 * open on that field (rotation, font or theme change). [key] names the field and must be unique among
 * the restorable fields of a screen.
 *
 * Each field keeps one saved flag, true while the field has focus and the IME is visible. The flag is
 * written only while the window has focus, so the IME closing and focus leaving during the teardown
 * of a recreation cannot clear it. After recreation, a field whose flag was true waits for the window
 * to have focus again (the point where API 26-27 re-grants focus to the first focusable view), then
 * requests focus and shows the keyboard. A field whose flag was false is left alone, so a recreation
 * with the keyboard closed still does not open it.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.restoreFocusIfImeWasOpen(key: String): Modifier =
    composed {
        val requester = remember { FocusRequester() }
        val windowInfo = LocalWindowInfo.current
        val imeVisible = WindowInsets.isImeVisible
        val keyboard = LocalSoftwareKeyboardController.current
        val wasOpen = rememberSaveable("imeFocus:$key") { mutableStateOf(false) }
        // Captured on the first composition, before the tracking below can write the flag.
        val restoreRequested = remember { wasOpen.value }
        var fieldFocused by remember { mutableStateOf(false) }

        LaunchedEffect(windowInfo.isWindowFocused, fieldFocused, imeVisible) {
            if (windowInfo.isWindowFocused) wasOpen.value = fieldFocused && imeVisible
        }
        LaunchedEffect(Unit) {
            if (!restoreRequested) return@LaunchedEffect
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
            keyboard?.show()
        }
        this.focusRequester(requester).onFocusChanged { fieldFocused = it.isFocused }
    }
