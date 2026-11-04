@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetSplit

// ── Split into equal subnets ──────────────────────────────────────────────────

@Composable
internal fun SubnetSplitCard(
    mode: SubnetSplitMode,
    input: String,
    split: SubnetSplit?,
    error: NetworkResult.Error?,
    onModeChange: (SubnetSplitMode) -> Unit,
    onInputChange: (String) -> Unit,
    onSplit: () -> Unit,
) {
    val dismissKeyboard = rememberDismissKeyboard()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.subnet_split_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SubnetSplitModeRow(mode = mode, onModeChange = onModeChange)
            SplitInputField(
                mode = mode,
                input = input,
                onInputChange = onInputChange,
                onDone = {
                    dismissKeyboard()
                    onSplit()
                },
            )
            Button(
                onClick = hapticAction(onSplit),
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank(),
            ) {
                Text(stringResource(R.string.subnet_split_button))
            }
            AnimatedContent(targetState = Triple(split, error, mode), label = "split-state") { (result, failure, _) ->
                SplitOutcome(result = result, failure = failure)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubnetSplitModeRow(
    mode: SubnetSplitMode,
    onModeChange: (SubnetSplitMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == SubnetSplitMode.BY_PREFIX,
            onClick = { onModeChange(SubnetSplitMode.BY_PREFIX) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.subnet_split_by_prefix)) },
        )
        SegmentedButton(
            selected = mode == SubnetSplitMode.BY_COUNT,
            onClick = { onModeChange(SubnetSplitMode.BY_COUNT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.subnet_split_by_count)) },
        )
    }
}

@Composable
private fun SplitInputField(
    mode: SubnetSplitMode,
    input: String,
    onInputChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val byPrefix = mode == SubnetSplitMode.BY_PREFIX
    val labelRes = if (byPrefix) R.string.subnet_split_prefix_label else R.string.subnet_split_count_label
    val placeholderRes =
        if (byPrefix) R.string.subnet_split_prefix_placeholder else R.string.subnet_split_count_placeholder
    ToolTextField(
        value = input,
        onValueChange = onInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(placeholderRes)) },
        leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun SplitOutcome(
    result: SubnetSplit?,
    failure: NetworkResult.Error?,
) {
    when {
        result != null -> {
            SubnetSplitResult(result)
        }

        failure != null -> {
            Text(
                text = ErrorTextMapper.map(failure.info, failure.message).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> {
            Spacer(modifier = Modifier.size(0.dp))
        }
    }
}

@Composable
private fun SubnetSplitResult(split: SubnetSplit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.subnet_split_summary,
                    subnetPluralQuantity(split.subnetCount),
                    formatSubnetCount(split.subnetCount),
                    split.newPrefix,
                ),
            style = MaterialTheme.typography.titleSmall,
        )
        val requested = split.requestedCount
        if (requested != null && requested != split.subnetCount) {
            Text(
                text =
                    stringResource(
                        R.string.subnet_split_rounded,
                        formatSubnetCount(requested),
                        formatSubnetCount(split.subnetCount),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        split.rows.forEach { row ->
            SplitRowText(cidr = row.cidr, range = "${row.firstAddress} – ${row.lastAddress}")
        }
        if (split.truncated) {
            Text(
                text =
                    stringResource(
                        R.string.subnet_split_truncated,
                        split.rows.size,
                        formatSubnetCount(split.subnetCount),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SplitRowText(
    cidr: String,
    range: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = cidr,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
            text = range,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
