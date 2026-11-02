package net.aieat.netswissknife.app.ui.screens.oui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolTouchTargetSize
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.core.domain.OuiLookupUseCase
import net.aieat.netswissknife.core.network.oui.LocalAdministration
import net.aieat.netswissknife.core.network.oui.MacAddressInfo
import net.aieat.netswissknife.core.network.oui.MacAddressKind
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.MacNotation
import net.aieat.netswissknife.core.network.oui.VendorResult

private const val CARD_PADDING_DP = 16
private const val ROW_GAP_DP = 8
private const val SECTION_GAP_DP = 12

/** Results header, then one card per entry. A rejected entry keeps its place with its reason. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiResultsContent(
    results: OuiLookupPhase.Results,
    summary: String,
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    // Results land below the input card. Bring the header into view so they are not hidden under
    // the keyboard or below the fold after Look up.
    val headerInView = remember { BringIntoViewRequester() }
    LaunchedEffect(results) { headerInView.bringIntoView() }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(OuiLookupTestTags.RESULTS),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap),
    ) {
        OuiResultsHeader(
            modifier = Modifier.bringIntoViewRequester(headerInView),
            results = results,
            lowercase = lowercase,
            onLowercaseChange = onLowercaseChange,
            onClear = onClear,
        )
        results.entries.forEach { entry ->
            when (entry) {
                is MacLookupEntry.Address -> OuiAddressCard(entry, lowercase, onCopy)
                is MacLookupEntry.Rejected -> OuiRejectedCard(entry.input)
            }
        }
        ResultActionsRow(
            copyText = summary,
            onShare = onShare,
            modifier = Modifier.testTag(OuiLookupTestTags.RESULT_ACTIONS),
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiResultsHeader(
    modifier: Modifier = Modifier,
    results: OuiLookupPhase.Results,
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val count = results.entries.size
    ElevatedCard(shape = AppShapes.large, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.oui_results_count, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Named for what it clears: the input field has its own Clear icon.
                TextButton(onClick = hapticAction(onClear), modifier = Modifier.heightIn(min = ToolTouchTargetSize)) {
                    Text(stringResource(R.string.oui_clear_results))
                }
            }
            if (results.truncated) {
                Text(
                    text = stringResource(R.string.oui_truncated, OuiLookupUseCase.MAX_ENTRIES),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LowercaseSwitch(lowercase, onLowercaseChange)
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LowercaseSwitch(
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ToolTouchTargetSize)
                .toggleable(value = lowercase, role = Role.Switch, onValueChange = onLowercaseChange)
                .testTag(OuiLookupTestTags.LOWERCASE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.oui_lowercase),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = lowercase, onCheckedChange = null)
    }
}

/** One analysed address: the normalised form, vendor, block, type, and the four notations with copy. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiAddressCard(
    entry: MacLookupEntry.Address,
    lowercase: Boolean,
    onCopy: (String) -> Unit,
) {
    val info = entry.info
    ElevatedCard(shape = AppShapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP_DP.dp),
        ) {
            if (entry.occurrences > 1) {
                Text(
                    text = pluralStringResource(R.plurals.oui_entered_times, entry.occurrences, entry.occurrences),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (info.isPrefixOnly) {
                Text(
                    text = stringResource(R.string.oui_prefix_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OuiAddressFields(info)
            HorizontalDivider()
            Text(
                text = stringResource(R.string.oui_notation_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            listOf(MacNotation.COLON, MacNotation.HYPHEN, MacNotation.DOTTED, MacNotation.BARE).forEach { notation ->
                OuiNotationRow(
                    label = stringResource(notationRes(notation)),
                    value = info.format(notation, uppercase = !lowercase),
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiAddressFields(info: MacAddressInfo) {
    OuiField(stringResource(R.string.oui_vendor_label), vendorText(info.vendor))
    (info.vendor as? VendorResult.Known)?.let { known ->
        OuiField(
            stringResource(R.string.oui_block_label),
            stringResource(R.string.oui_block_value, known.blockSize.registryName, known.blockSize.prefixBits),
        )
    }
    OuiField(stringResource(R.string.oui_type_label), stringResource(kindRes(info.kind)))
    OuiField(stringResource(R.string.oui_local_label), localAdministrationText(info.localAdministration))
    if (info.locallyAdministered && info.kind == MacAddressKind.UNICAST) {
        Text(
            text = stringResource(R.string.oui_local_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun localAdministrationText(state: LocalAdministration): String =
    stringResource(
        when (state) {
            LocalAdministration.LOCAL -> R.string.oui_yes
            LocalAdministration.UNIVERSAL -> R.string.oui_no
            LocalAdministration.NOT_APPLICABLE -> R.string.oui_not_applicable
        },
    )

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun OuiRejectedCard(input: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PADDING_DP.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = input,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.oui_rejected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiField(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiNotationRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = hapticAction { onCopy(value) }, modifier = Modifier.size(ToolTouchTargetSize)) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.oui_copy_description, label))
        }
    }
}
