package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R

/** Minimum touch target for icon-only controls inside tool forms and result cards. */
val ToolTouchTargetSize: Dp = 48.dp

/**
 * Clear (X) control for a text field or recent-host chip. The whole 48dp box is the
 * touch target, so the glyph itself can stay small without shrinking the hit area.
 *
 * The caller decides what clearing means and whether focus should leave the field; see
 * [ToolTextField], which drops focus and hides the keyboard after clearing.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun ToolClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(ToolTouchTargetSize),
    ) {
        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
    }
}

/**
 * Standard input for tool screens: an [OutlinedTextField] with an optional leading icon and
 * a clear button that appears while [showClear] holds and the field is editable.
 *
 * Callers keep their own validation and clear-visibility rules; this only standardises the
 * decoration so every tool shows the same icon, clear affordance and touch target.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    showClear: Boolean = value.isNotEmpty(),
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = OutlinedTextFieldDefaults.shape,
) {
    // Clearing is the end of the edit: focus leaves the field and the keyboard closes with it, so
    // the keyboard does not open over results. The dismissal lives here, not in the button, because
    // the button leaves composition when the field empties.
    val dismissKeyboard = rememberDismissKeyboard()
    val trailing: (@Composable () -> Unit)? =
        if (showClear) {
            {
                ToolClearButton(
                    onClick = {
                        onValueChange("")
                        dismissKeyboard()
                    },
                    enabled = enabled,
                )
            }
        } else {
            null
        }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.scrollIntoViewOnFocus(),
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailing,
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
    )
}

/**
 * Keeps the focused field, its supporting and error text, and the row under it (recent hosts) on
 * screen above the keyboard (PHB-01, V6A-01). Apply it to every text field in a tool form, whether
 * it is a [ToolTextField] or a plain [androidx.compose.material3.OutlinedTextField].
 *
 * The request is made again whenever the keyboard height or the field's size changes, not only on
 * focus. The keyboard animates in over a few hundred milliseconds and the layout above it changes
 * with it, so a single request made on focus lands on a layout that is about to move. The request
 * covers the field plus [IME_REVEAL_TRAILING_DP] (see [focusRevealHeightPx]). Screens must not add
 * their own keyboard-triggered scroll to a button: that scroll moves the field off screen (V6A-01).
 */
@Composable
@Suppress("ktlint:standard:function-naming")
fun Modifier.scrollIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val trailingPx = with(density) { IME_REVEAL_TRAILING_DP.dp.roundToPx() }
    var focused by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val visibleHeightPx = windowHeightPx - imeBottomPx
    LaunchedEffect(focused, imeBottomPx, visibleHeightPx, fieldSize) {
        if (focused && fieldSize.height > 0) {
            val revealHeight = focusRevealHeightPx(fieldSize.height, trailingPx, visibleHeightPx)
            requester.bringIntoView(
                Rect(0f, 0f, fieldSize.width.toFloat(), revealHeight.toFloat()),
            )
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onSizeChanged { fieldSize = it }
        .onFocusChanged { focused = it.isFocused }
}
