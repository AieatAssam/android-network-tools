package net.aieat.netswissknife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R

/**
 * The one Copy / Share row for result cards (PHB-12). Both actions are labelled text buttons with
 * an icon, sit trailing under the result, and Copy shows the shared "Copied to clipboard" toast.
 * [copyText] and [onShare] carry each tool's own export text; [extraActions] adds a tool-specific
 * action (for example Copy as cURL) ahead of Copy.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun ResultActionsRow(
    copyText: String,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val copyResult = rememberCopyWithFeedback()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extraActions?.invoke(this)
        TextButton(onClick = { copyResult(copyText) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_copy))
        }
        TextButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_share_short))
        }
    }
}
