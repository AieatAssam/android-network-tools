package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one Retry control for error cards and failed states (PHB-03). Filled tonal, with a refresh
 * icon, at least one touch target high, with haptic feedback. Callers pass their own label so each
 * tool keeps its wording.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun ToolRetryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = hapticAction(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = ToolTouchTargetSize),
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
