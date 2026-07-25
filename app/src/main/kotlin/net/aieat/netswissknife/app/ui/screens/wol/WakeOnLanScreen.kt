package net.aieat.netswissknife.app.ui.screens.wol

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
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
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WakeOnLanScreen(viewModel: WakeOnLanViewModel = hiltViewModel()) {
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester()
    LaunchedEffect(Unit) { requestLocalNetworkPermission() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val macAddress by viewModel.macAddress.collectAsStateWithLifecycle()
    val broadcastAddress by viewModel.broadcastAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
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

            WolInputCard(
                macAddress = macAddress,
                broadcastAddress = broadcastAddress,
                port = port,
                isSending = uiState is WolUiState.Sending,
                onMacChange = viewModel::onMacAddressChange,
                onBroadcastChange = viewModel::onBroadcastAddressChange,
                onPortChange = viewModel::onPortChange,
                onSend = viewModel::send,
            )

            AnimatedContent(
                targetState = uiState,
                transitionSpec = { (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }) togetherWith fadeOut(tween(150)) },
                label = "wol-state"
            ) { state ->
                when (state) {
                    is WolUiState.Idle -> Spacer(Modifier.height(0.dp))
                    is WolUiState.Sending -> WolSendingCard()
                    is WolUiState.Success -> WolSuccessCard(state.report, onSendAgain = viewModel::reset)
                    is WolUiState.Error -> WolErrorCard(state.message, onRetry = viewModel::send)
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.wol_screen_title),
            sections = listOf(
                HelpSection(
                    heading = stringResource(R.string.wol_help_what_heading),
                    body = stringResource(R.string.wol_help_what_body)
                ),
                HelpSection(
                    heading = stringResource(R.string.wol_help_mac_heading),
                    body = stringResource(R.string.wol_help_mac_body)
                ),
                HelpSection(
                    heading = stringResource(R.string.wol_help_requirements_heading),
                    body = stringResource(R.string.wol_help_requirements_body)
                ),
                HelpSection(
                    heading = stringResource(R.string.wol_help_broadcast_heading),
                    body = stringResource(R.string.wol_help_broadcast_body)
                ),
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun WolInputCard(
    macAddress: String,
    broadcastAddress: String,
    port: String,
    isSending: Boolean,
    onMacChange: (String) -> Unit,
    onBroadcastChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var showAdvanced by remember { mutableStateOf(false) }

    val isMacInvalid = macAddress.isNotBlank() && !WolMagicPacket.isValidMac(macAddress)
    val isPortInvalid = port.toIntOrNull() !in 0..65_535
    val canSend = !isSending && !isPortInvalid &&
        broadcastAddress.isNotBlank() && WolMagicPacket.isValidMac(macAddress)

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier.padding(AppSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)
        ) {
            OutlinedTextField(
                value = macAddress,
                onValueChange = onMacChange,
                label = { Text(stringResource(R.string.wol_mac_label)) },
                placeholder = { Text(stringResource(R.string.wol_mac_placeholder), fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (macAddress.isNotEmpty()) {
                        IconButton(onClick = { onMacChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                isError = isMacInvalid,
                supportingText = if (isMacInvalid) {
                    { Text(stringResource(R.string.wol_mac_invalid)) }
                } else null,
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
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(R.string.wol_advanced_options))
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (showAdvanced) R.string.action_collapse else R.string.action_expand
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s + AppSpacing.xs)) {
                    OutlinedTextField(
                        value = broadcastAddress,
                        onValueChange = onBroadcastChange,
                        label = { Text(stringResource(R.string.wol_broadcast_label)) },
                        supportingText = { Text(stringResource(R.string.wol_broadcast_hint)) },
                        singleLine = true,
                        enabled = !isSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        label = { Text(stringResource(R.string.wol_port_label)) },
                        supportingText = { Text(stringResource(R.string.wol_port_hint)) },
                        isError = isPortInvalid,
                        singleLine = true,
                        enabled = !isSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                    )
                }
            }

            Button(
                onClick = hapticAction {
                    focusManager.clearFocus()
                    onSend()
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wol_send_button))
            }
        }
    }
}

// ── State cards ───────────────────────────────────────────────────────────────

@Composable
private fun WolSendingCard() {
    val sendingLabel = stringResource(R.string.wol_sending)
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
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

            TextButton(onClick = onSendAgain) {
                Text(stringResource(R.string.wol_send_again))
            }
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
            Button(onClick = hapticAction(onRetry)) {
                Text(stringResource(R.string.wol_retry_button))
            }
        }
    }
}
