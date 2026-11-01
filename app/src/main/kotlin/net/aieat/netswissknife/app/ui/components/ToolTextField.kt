package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
    /**
     * Names the field for [restoreFocusIfImeWasOpen]: when the activity is recreated with the keyboard
     * open on this field, the keyboard is shown again on it. Null leaves the field out of the restore.
     */
    restoreFocusKey: String? = null,
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
        modifier = if (restoreFocusKey != null) modifier.restoreFocusIfImeWasOpen(restoreFocusKey) else modifier,
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
