package net.aieat.netswissknife.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One line under a disabled primary action that says why it is disabled (V6A-12, PHA-12). Show it
 * only while the action is disabled; the wording names what the user must fill in.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun DisabledActionHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
