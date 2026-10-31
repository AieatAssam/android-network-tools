package net.aieat.netswissknife.app.ui.screens.oui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ToolTouchTargetSize
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes

private const val CARD_PADDING_DP = 16
private const val LOADING_PADDING_DP = 24
private const val STATE_GAP_DP = 8

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiIdleCard() {
    ElevatedCard(shape = AppShapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(STATE_GAP_DP.dp),
        ) {
            Text(
                text = stringResource(R.string.oui_idle_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.oui_idle_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiLoadingCard() {
    Box(modifier = Modifier.fillMaxWidth().padding(LOADING_PADDING_DP.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.oui_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiFailedCard(
    reason: OuiLookupFailure,
    onRetry: () -> Unit,
) {
    val clipboard = reason == OuiLookupFailure.CLIPBOARD_EMPTY
    val title =
        stringResource(if (clipboard) R.string.oui_failed_clipboard_title else R.string.oui_failed_no_valid_title)
    val body =
        stringResource(if (clipboard) R.string.oui_failed_clipboard_body else R.string.oui_failed_no_valid_body)
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = AppShapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(STATE_GAP_DP.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = hapticAction(onRetry), modifier = Modifier.heightIn(min = ToolTouchTargetSize)) {
                Text(stringResource(R.string.oui_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
