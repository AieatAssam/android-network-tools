package net.aieat.netswissknife.app.ui.screens.wol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing

// ── Saved devices ─────────────────────────────────────────────────────────────

@Composable
internal fun WolSavedDevicesSection(
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
    onWake: (WolSavedDevice) -> Unit,
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
                    onWake = { onWake(device) },
                    onEdit = { onEdit(device) },
                    onDelete = { onDelete(device) },
                )
            }
        }
    }
}

@Composable
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
private fun WolSavedDeviceRow(
    device: WolSavedDevice,
    isSending: Boolean,
    onUse: () -> Unit,
    onWake: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val label = WolSavedDevices.displayName(device)
    // The MAC gets the full row width (never wrapped mid-address); the actions sit on their own line.
    Column(modifier = Modifier.fillMaxWidth()) {
        WolSavedDeviceSummary(device = device, label = label)
        WolSavedDeviceActions(
            label = label,
            isSending = isSending,
            onUse = onUse,
            onWake = onWake,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

/** The device's name, its MAC when the name differs, and its target when it is not the default one. */
@Composable
private fun WolSavedDeviceSummary(
    device: WolSavedDevice,
    label: String,
) {
    val usesDefaultTarget =
        device.broadcastAddress == WakeOnLanViewModel.DEFAULT_BROADCAST &&
            device.port == WakeOnLanViewModel.DEFAULT_PORT
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
            maxLines = 1,
        )
    }
    if (!usesDefaultTarget) {
        Text(
            text = stringResource(R.string.wol_saved_target, device.broadcastAddress, device.port),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WolSavedDeviceActions(
    label: String,
    isSending: Boolean,
    onUse: () -> Unit,
    onWake: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editDescription = stringResource(R.string.wol_edit_device_description, label)
    val deleteDescription = stringResource(R.string.wol_delete_device_description, label)
    val wakeDescription = stringResource(R.string.wol_wake_device_description, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = hapticAction(onUse),
            enabled = !isSending,
            modifier = Modifier.heightIn(min = MinTouchTarget),
        ) {
            Text(stringResource(R.string.wol_use_device))
        }
        FilledTonalButton(
            onClick = hapticAction(onWake),
            enabled = !isSending,
            modifier =
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .semantics { contentDescription = wakeDescription },
        ) {
            Text(stringResource(R.string.wol_wake_device))
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

/** Confirms deleting [device] from the saved list. */
@Composable
internal fun WolDeleteDeviceDialog(
    device: WolSavedDevice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = WolSavedDevices.displayName(device)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wol_delete_confirm_title)) },
        text = { Text(stringResource(R.string.wol_delete_confirm_body, label)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.wol_delete_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
