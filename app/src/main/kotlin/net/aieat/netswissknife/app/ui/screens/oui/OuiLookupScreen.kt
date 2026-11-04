package net.aieat.netswissknife.app.ui.screens.oui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.oui.MacLookupSummaryFormatter

private const val SLIDE_DIVISOR = 4
private const val CARD_PADDING_DP = 16
private const val BUTTON_GAP_DP = 8
private const val INPUT_GAP_DP = 12

/** Every action the screen can take, bundled so the sub-composables stay small. */
private class OuiLookupActions(
    val onInputChange: (String) -> Unit,
    val onLookup: () -> Unit,
    val onPaste: () -> Unit,
    val onRecentSelected: (String) -> Unit,
    val onRecentRemoved: (String) -> Unit,
    val onRecentsCleared: () -> Unit,
    val onClear: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: () -> Unit,
)

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun OuiLookupScreen(viewModel: OuiLookupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recents by viewModel.recentLookups.collectAsStateWithLifecycle()
    val announcementPhase =
        when (uiState.phase) {
            OuiLookupPhase.Idle -> null
            OuiLookupPhase.Loading -> ToolAnnouncementPhase.RUNNING
            is OuiLookupPhase.Results -> ToolAnnouncementPhase.FINISHED
            is OuiLookupPhase.Failed -> ToolAnnouncementPhase.ERROR
        }
    ToolStateAnnouncer(stringResource(R.string.oui_screen_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }
    var lowercase by rememberSaveable { mutableStateOf(false) }

    val labels = rememberOuiSummaryLabels()
    val results = uiState.phase as? OuiLookupPhase.Results
    val summary = if (results == null) "" else MacLookupSummaryFormatter.format(results.entries, labels)
    val copy = rememberOuiCopy()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareSubject = stringResource(R.string.oui_share_subject)
    val paste: () -> Unit = {
        scope.launch { viewModel.onClipboardText(clipboard.readText()) }
    }
    val actions =
        OuiLookupActions(
            onInputChange = viewModel::onInputChange,
            onLookup = viewModel::onLookup,
            onPaste = paste,
            onRecentSelected = viewModel::onRecentSelected,
            onRecentRemoved = viewModel::onRecentRemoved,
            onRecentsCleared = viewModel::onRecentsCleared,
            onClear = viewModel::onClear,
            onCopy = copy,
            onShare = { context.shareText(summary, shareSubject) },
        )

    OuiLookupBody(
        visible = visible,
        uiState = uiState,
        recents = recents,
        lowercase = lowercase,
        onLowercaseChange = { lowercase = it },
        onHelp = { showHelp = true },
        summary = summary,
        actions = actions,
    )

    if (showHelp) {
        OuiHelpSheet(onDismiss = { showHelp = false })
    }
}

/** The animated page: hero, input, recents, then the phase-specific content. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiLookupBody(
    visible: Boolean,
    uiState: OuiLookupUiState,
    recents: List<String>,
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
    onHelp: () -> Unit,
    summary: String,
    actions: OuiLookupActions,
) {
    val dismissRecent = rememberDismissKeyboard()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter()) + slideInVertically(AppMotion.enter()) { it / SLIDE_DIVISOR },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap),
        ) {
            ToolHeroHeader(
                title = stringResource(R.string.oui_screen_title),
                subtitle = stringResource(R.string.oui_screen_subtitle),
                icon = Icons.Default.Fingerprint,
                onHelpClick = onHelp,
            )
            OuiInputCard(
                input = uiState.input,
                invalidCount = uiState.invalidEntryCount,
                lookupEnabled = uiState.input.isNotBlank() && uiState.phase != OuiLookupPhase.Loading,
                actions = actions,
            )
            RecentHostsRow(
                recentHosts = recents,
                onHostSelected = { entry ->
                    dismissRecent()
                    actions.onRecentSelected(entry)
                },
                onRemoveHost = actions.onRecentRemoved,
                onClearAll = actions.onRecentsCleared,
                actionsEnabled = uiState.phase != OuiLookupPhase.Loading,
            )
            AnimatedContent(targetState = uiState.phase, label = "oui-phase") { phase ->
                OuiPhaseContent(
                    phase = phase,
                    summary = summary,
                    lowercase = lowercase,
                    onLowercaseChange = onLowercaseChange,
                    actions = actions,
                )
            }
        }
    }
}

/** Input field with inline validation, then the paste and look-up actions. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiInputCard(
    input: String,
    invalidCount: Int,
    lookupEnabled: Boolean,
    actions: OuiLookupActions,
) {
    // Look up and Paste are actions on the field's text, so the keyboard closes before they run;
    // the results then appear where the keyboard was rather than under it.
    val dismiss = rememberDismissKeyboard()
    ElevatedCard(shape = AppShapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(INPUT_GAP_DP.dp),
        ) {
            OuiInputField(input, invalidCount, lookupEnabled, actions)
            // Full-width and stacked: side by side, the labels wrapped mid-word at 360 dp with font 1.3.
            OutlinedButton(
                onClick =
                    hapticAction {
                        dismiss()
                        actions.onPaste()
                    },
                modifier = Modifier.fillMaxWidth().testTag(OuiLookupTestTags.PASTE),
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(BUTTON_GAP_DP.dp))
                Text(stringResource(R.string.oui_action_paste))
            }
            Button(
                onClick =
                    hapticAction {
                        dismiss()
                        actions.onLookup()
                    },
                enabled = lookupEnabled,
                modifier = Modifier.fillMaxWidth().testTag(OuiLookupTestTags.LOOKUP),
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(BUTTON_GAP_DP.dp))
                Text(stringResource(R.string.oui_action_lookup))
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiInputField(
    input: String,
    invalidCount: Int,
    lookupEnabled: Boolean,
    actions: OuiLookupActions,
) {
    val dismiss = rememberDismissKeyboard()
    ToolTextField(
        value = input,
        onValueChange = actions.onInputChange,
        modifier = Modifier.fillMaxWidth().testTag(OuiLookupTestTags.INPUT),
        label = { Text(stringResource(R.string.oui_input_label)) },
        placeholder = { Text(stringResource(R.string.oui_input_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
        supportingText = {
            Text(
                text =
                    if (invalidCount > 0) {
                        pluralStringResource(R.plurals.oui_invalid_entries, invalidCount, invalidCount)
                    } else {
                        stringResource(R.string.oui_input_hint)
                    },
            )
        },
        isError = invalidCount > 0,
        minLines = 2,
        maxLines = 6,
        keyboardOptions = ouiInputKeyboardOptions,
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    dismiss()
                    if (lookupEnabled) actions.onLookup()
                },
            ),
    )
}

/** Idle, loading, results and error states, cross-faded by [AnimatedContent]. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiPhaseContent(
    phase: OuiLookupPhase,
    summary: String,
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
    actions: OuiLookupActions,
) {
    when (phase) {
        OuiLookupPhase.Idle -> {
            OuiIdleCard()
        }

        OuiLookupPhase.Loading -> {
            OuiLoadingCard()
        }

        is OuiLookupPhase.Failed -> {
            val retry = if (phase.reason == OuiLookupFailure.CLIPBOARD_EMPTY) actions.onPaste else actions.onLookup
            OuiFailedCard(reason = phase.reason, onRetry = retry)
        }

        is OuiLookupPhase.Results -> {
            OuiResultsContent(
                results = phase,
                summary = summary,
                lowercase = lowercase,
                onLowercaseChange = onLowercaseChange,
                onCopy = actions.onCopy,
                onShare = actions.onShare,
                onClear = actions.onClear,
            )
        }
    }
}

/** Help for MAC Lookup, opened from the hero's help button. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun OuiHelpSheet(onDismiss: () -> Unit) {
    ToolHelpSheet(
        title = stringResource(R.string.oui_screen_title),
        conceptHeading = stringResource(R.string.help_oui_concept_heading),
        conceptBody = stringResource(R.string.help_oui_concept_body),
        sections =
            listOf(
                HelpSection(
                    stringResource(R.string.help_oui_vendor_heading),
                    stringResource(R.string.help_oui_vendor_body),
                ),
                HelpSection(
                    stringResource(R.string.help_oui_local_heading),
                    stringResource(R.string.help_oui_local_body),
                ),
                HelpSection(
                    stringResource(R.string.help_oui_kinds_heading),
                    stringResource(R.string.help_oui_kinds_body),
                ),
            ),
        onDismiss = onDismiss,
    )
}

/** The first text item on the clipboard, or null when it holds no text. */
private suspend fun Clipboard.readText(): String? =
    getClipEntry()
        ?.clipData
        ?.getItemAt(0)
        ?.text
        ?.toString()

/** Copies text to the clipboard with a confirmation toast, as the other tools do. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun rememberOuiCopy(): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.oui_copied)
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
}
