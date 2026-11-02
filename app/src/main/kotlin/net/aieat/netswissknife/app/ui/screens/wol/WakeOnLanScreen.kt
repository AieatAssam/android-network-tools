package net.aieat.netswissknife.app.ui.screens.wol

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.wol.WolBroadcastProblem
import net.aieat.netswissknife.core.network.wol.WolMacProblem
import net.aieat.netswissknife.core.network.wol.WolSendReport
import java.time.Instant

// ── Screen ────────────────────────────────────────────────────────────────────

object WakeOnLanScreenTestTags {
    const val SOURCE_CONTEXT = "wol_source_context"
    const val INVALID_HANDOFF = "wol_invalid_handoff"
    const val CLEAR_PREFILL_ACTION = "wol_clear_prefill_action"
}

private val MinTouchTarget = 48.dp

@Composable
fun WakeOnLanScreen(viewModel: WakeOnLanViewModel = hiltViewModel()) {
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester()
    LaunchedEffect(Unit) { requestLocalNetworkPermission() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val macAddress by viewModel.macAddress.collectAsStateWithLifecycle()
    val macProblem by viewModel.macProblem.collectAsStateWithLifecycle()
    val hasInvalidHandoff by viewModel.hasInvalidHandoff.collectAsStateWithLifecycle()
    val sourceContext by viewModel.sourceContextState.collectAsStateWithLifecycle()
    val broadcastAddress by viewModel.broadcastAddress.collectAsStateWithLifecycle()
    val broadcastProblem by viewModel.broadcastProblem.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val portInvalid by viewModel.portInvalid.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val editingMac by viewModel.editingMac.collectAsStateWithLifecycle()
    val saveConflict by viewModel.saveConflict.collectAsStateWithLifecycle()
    val saveNotice by viewModel.saveNotice.collectAsStateWithLifecycle()
    val announcementPhase = when (uiState) {
        WolUiState.Idle -> null
        WolUiState.Sending -> ToolAnnouncementPhase.RUNNING
        is WolUiState.Success -> ToolAnnouncementPhase.FINISHED
        is WolUiState.Error -> ToolAnnouncementPhase.ERROR
    }
    ToolStateAnnouncer(stringResource(R.string.wol_screen_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WolSavedDevice?>(null) }
    val isSending = uiState is WolUiState.Sending
    val canSend = !isSending && macProblem == null && broadcastProblem == null && !portInvalid

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap)
        ) {
            ToolHeroHeader(
                title = stringResource(R.string.wol_screen_title),
                subtitle = stringResource(R.string.wol_screen_subtitle),
                icon = Icons.Default.PowerSettingsNew,
                onHelpClick = { showHelp = true },
            )

            NetworkStatusBanner(
                status = networkStatus,
                scope = NetworkStatusScope.LOCAL_NETWORK,
                permissionDenied = (uiState as? WolUiState.Error)?.networkErrorKind == NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
                onGrantPermission = requestLocalNetworkPermission,
            )

            if (sourceContext == ToolSource.LAN) {
                WolSourceRow(
                    isSending = isSending,
                    onClear = viewModel::clearPrefill,
                )
            }
            if (hasInvalidHandoff) {
                Text(
                    text = stringResource(R.string.wol_invalid_handoff),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(WakeOnLanScreenTestTags.INVALID_HANDOFF),
                )
            }

            WolInputCard(
                macAddress = macAddress,
                macProblem = macProblem,
                broadcastAddress = broadcastAddress,
                broadcastProblem = broadcastProblem,
                port = port,
                portInvalid = portInvalid,
                isSending = isSending,
                canSend = canSend,
                onMacChange = viewModel::onMacAddressChange,
                onBroadcastChange = viewModel::onBroadcastAddressChange,
                onPortChange = viewModel::onPortChange,
                onSend = viewModel::send,
                // Saving the MAC sits in the same card, so one form covers entering, sending and saving (PHB-16).
                savedDevices = {
                    WolSavedDevicesSection(
                        devices = savedDevices,
                        deviceName = deviceName,
                        editingMac = editingMac,
                        conflict = saveConflict,
                        notice = saveNotice,
                        canSave = macProblem == null && macAddress.isNotBlank() && !isSending,
                        isSending = isSending,
                        onNameChange = viewModel.savedDeviceEditor::onDeviceNameChange,
                        onSave = viewModel.savedDeviceEditor::saveDevice,
                        onCancelEdit = viewModel.savedDeviceEditor::cancelEdit,
                        onUse = viewModel.savedDeviceEditor::useDevice,
                        onEdit = viewModel.savedDeviceEditor::startEditDevice,
                        onDelete = { pendingDelete = it },
                    )
                },
            )

            WolStateCard(
                uiState = uiState,
                onCancel = viewModel::stopSending,
                onSendAgain = viewModel::reset,
                onRetry = viewModel::send,
            )
        }
    }

    pendingDelete?.let { device ->
        val label = WolSavedDevices.displayName(device)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.wol_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wol_delete_confirm_body, label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savedDeviceEditor.deleteDevice(device.mac)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.wol_delete_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.wol_screen_title),
            conceptHeading = stringResource(R.string.help_wol_concept_heading),
            conceptBody = stringResource(R.string.help_wol_concept_body),
            sections = listOf(
                HelpSection(
                    heading = stringResource(R.string.help_wol_mac_heading),
                    body = stringResource(R.string.help_wol_mac_body)
                ),
                HelpSection(
                    heading = stringResource(R.string.help_wol_requirements_heading),
                    body = stringResource(R.string.help_wol_requirements_body)
                ),
                HelpSection(
                    heading = stringResource(R.string.help_wol_broadcast_heading),
                    body = stringResource(R.string.help_wol_broadcast_body)
                ),
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun WolSourceRow(
    isSending: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.wol_source_lan),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(WakeOnLanScreenTestTags.SOURCE_CONTEXT),
        )
        TextButton(
            onClick = onClear,
            enabled = !isSending,
            modifier =
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(WakeOnLanScreenTestTags.CLEAR_PREFILL_ACTION),
        ) {
            Text(stringResource(R.string.clear))
        }
    }
}

@StringRes
private fun WolMacProblem.messageRes(): Int =
    when (this) {
        WolMacProblem.BLANK -> R.string.err_mac_blank
        WolMacProblem.LENGTH -> R.string.wol_mac_problem_length
        WolMacProblem.CHARACTERS -> R.string.wol_mac_problem_characters
        WolMacProblem.SEPARATORS -> R.string.wol_mac_problem_separators
        WolMacProblem.MULTICAST -> R.string.wol_mac_problem_multicast
        WolMacProblem.BROADCAST -> R.string.wol_mac_problem_broadcast
        WolMacProblem.ZERO -> R.string.wol_mac_problem_zero
    }

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun WolInputCard(
    macAddress: String,
    macProblem: WolMacProblem?,
    broadcastAddress: String,
    broadcastProblem: WolBroadcastProblem?,
    port: String,
    portInvalid: Boolean,
    isSending: Boolean,
    canSend: Boolean,
    onMacChange: (String) -> Unit,
    onBroadcastChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSend: () -> Unit,
    savedDevices: @Composable () -> Unit,
) {
    val dismissKeyboard = rememberDismissKeyboard()
    var showAdvanced by remember { mutableStateOf(false) }
    // A problem in a hidden field would leave Send disabled with no visible reason; open the panel.
    val advancedInvalid = broadcastProblem != null || portInvalid
    LaunchedEffect(advancedInvalid) { if (advancedInvalid) showAdvanced = true }

    val macError = macProblem?.takeIf { it != WolMacProblem.BLANK }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier.padding(AppSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)
        ) {
            ToolTextField(
                value = macAddress,
                onValueChange = onMacChange,
                label = { Text(stringResource(R.string.wol_mac_label)) },
                placeholder = { Text(stringResource(R.string.wol_mac_placeholder), fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                isError = macError != null,
                supportingText =
                    if (macError != null) {
                        { Text(stringResource(macError.messageRes())) }
                    } else {
                        null
                    },
                singleLine = true,
                enabled = !isSending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
            )

            // Advanced options (broadcast address + port)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Text(stringResource(R.string.wol_advanced_options))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription =
                            stringResource(
                                if (showAdvanced) R.string.action_collapse else R.string.action_expand,
                            ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)) {
                    val broadcastError =
                        when (broadcastProblem) {
                            null -> null
                            WolBroadcastProblem.BLANK -> R.string.err_broadcast_blank
                            WolBroadcastProblem.NOT_IPV4 -> R.string.wol_broadcast_invalid
                        }
                    ToolTextField(
                        value = broadcastAddress,
                        onValueChange = onBroadcastChange,
                        label = { Text(stringResource(R.string.wol_broadcast_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        isError = broadcastError != null,
                        supportingText = {
                            Text(stringResource(broadcastError ?: R.string.wol_broadcast_hint))
                        },
                        singleLine = true,
                        enabled = !isSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                    )
                    ToolTextField(
                        value = port,
                        onValueChange = onPortChange,
                        label = { Text(stringResource(R.string.wol_port_label)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (portInvalid) R.string.wol_port_invalid else R.string.wol_port_hint
                                )
                            )
                        },
                        isError = portInvalid,
                        singleLine = true,
                        enabled = !isSending,
                        keyboardOptions =
                            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                    )
                }
            }

            Button(
                onClick =
                    hapticAction {
                        dismissKeyboard()
                        onSend()
                    },
                enabled = canSend,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wol_send_button))
            }
            HorizontalDivider()
            savedDevices()
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun WolStateCard(
    uiState: WolUiState,
    onCancel: () -> Unit,
    onSendAgain: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedContent(
        targetState = uiState,
        transitionSpec = {
            (fadeIn(AppMotion.enter(250)) + slideInVertically(AppMotion.enter(250)) { it / 6 }) togetherWith
                fadeOut(AppMotion.exit(150))
        },
        label = "wol-state",
    ) { state ->
        when (state) {
            is WolUiState.Idle -> {
                Spacer(Modifier.height(0.dp))
            }

            is WolUiState.Sending -> {
                WolSendingCard(onCancel = onCancel)
            }

            is WolUiState.Success -> {
                WolSuccessCard(state.report, onSendAgain = onSendAgain)
            }

            is WolUiState.Error -> {
                WolErrorCard(
                    message = ErrorTextMapper.map(state.info, state.message).asString(),
                    onRetry = onRetry,
                )
            }
        }
    }
}

// ── Saved devices ─────────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun WolSavedDevicesSection(
    devices: List<WolSavedDevice>,
    deviceName: String,
    editingMac: String?,
    conflict: Boolean,
    notice: WolSaveNotice?,
    canSave: Boolean,
    isSending: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onUse: (WolSavedDevice) -> Unit,
    onEdit: (WolSavedDevice) -> Unit,
    onDelete: (WolSavedDevice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)) {
        Text(
            text = stringResource(R.string.wol_saved_title),
            style = MaterialTheme.typography.titleMedium,
        )
        WolSavedDeviceForm(
            deviceName = deviceName,
            editingMac = editingMac,
            conflict = conflict,
            notice = notice,
            canSave = canSave,
            isSending = isSending,
            onNameChange = onNameChange,
            onSave = onSave,
            onCancelEdit = onCancelEdit,
        )
        HorizontalDivider()
        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.wol_saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            devices.forEach { device ->
                WolSavedDeviceRow(
                    device = device,
                    isSending = isSending,
                    onUse = { onUse(device) },
                    onEdit = { onEdit(device) },
                    onDelete = { onDelete(device) },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun WolSavedDeviceForm(
    deviceName: String,
    editingMac: String?,
    conflict: Boolean,
    notice: WolSaveNotice?,
    canSave: Boolean,
    isSending: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)) {
        ToolTextField(
            value = deviceName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.wol_device_name_label)) },
            leadingIcon = {
                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            singleLine = true,
            enabled = !isSending,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.medium,
        )
        if (conflict) {
            Text(
                text = stringResource(R.string.wol_device_conflict),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        notice?.let { WolSaveNoticeText(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s),
        ) {
            Button(
                onClick = hapticAction(onSave),
                enabled = canSave,
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                val label = if (editingMac != null) R.string.wol_update_device else R.string.wol_save_device
                Text(stringResource(label))
            }
            if (editingMac != null) {
                TextButton(
                    onClick = onCancelEdit,
                    enabled = !isSending,
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Text(stringResource(R.string.wol_cancel_edit))
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun WolSavedDeviceRow(
    device: WolSavedDevice,
    isSending: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val label = WolSavedDevices.displayName(device)
    val editDescription = stringResource(R.string.wol_edit_device_description, label)
    val deleteDescription = stringResource(R.string.wol_delete_device_description, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (label != device.mac) {
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = hapticAction(onUse),
            enabled = !isSending,
            modifier = Modifier.heightIn(min = MinTouchTarget),
        ) {
            Text(stringResource(R.string.wol_use_device))
        }
        IconButton(
            onClick = onEdit,
            enabled = !isSending,
            modifier = Modifier.semantics { contentDescription = editDescription },
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(
            onClick = onDelete,
            enabled = !isSending,
            modifier = Modifier.semantics { contentDescription = deleteDescription },
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ── State cards ───────────────────────────────────────────────────────────────

@Composable
private fun WolSendingCard(onCancel: () -> Unit) {
    val sendingLabel = stringResource(R.string.wol_sending)
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = sendingLabel }
            )
            Spacer(Modifier.width(AppSpacing.m))
            Text(
                text = sendingLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun WolSuccessCard(report: WolSendReport, onSendAgain: () -> Unit) {
    var iconReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { iconReady = true }
    val iconScale by animateFloatAsState(
        targetValue = if (iconReady) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "wol-success-icon"
    )
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.share_subject_wol, report.macAddress)

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(AppSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s)
        ) {
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = stringResource(R.string.wol_success_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.wol_success_detail,
                    report.macAddress,
                    report.broadcastAddress,
                    report.port,
                    report.packetsSent
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))

            Text(
                text = stringResource(R.string.wol_success_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ResultActionsRow(
                copyText = buildWolShareText(report, Instant.now()),
                onShare = { context.shareText(buildWolShareText(report, Instant.now()), shareSubject) },
                extraActions = {
                    TextButton(
                        onClick = onSendAgain,
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.wol_send_again))
                    }
                },
            )
        }
    }
}

@Composable
private fun WolErrorCard(message: String, onRetry: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.wol_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToolRetryButton(text = stringResource(R.string.wol_retry_button), onClick = onRetry)
        }
    }
}
