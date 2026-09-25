package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.components.ToolStopButton
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.screens.portscan.PortScanUiState
import net.aieat.netswissknife.app.ui.screens.portscan.PortScanViewModel
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import net.aieat.netswissknife.core.network.portscan.TlsSubjectSanitizer

object PortsScreenTestTags {
    const val PRESET_FIELD = "ports_preset_field"
    const val SCAN_BUTTON = "ports_scan_button"
    const val CONCURRENCY_SLIDER = "ports_concurrency_slider"
    const val SOURCE_CONTEXT = "ports_source_context"
    const val INVALID_HANDOFF = "ports_invalid_handoff"
    const val AGGRESSIVE_PROBES_SWITCH = "ports_aggressive_probes_switch"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortsScreen(viewModel: PortScanViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onLifecyclePause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLifecyclePause()
        }
    }
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester(viewModel::startScan)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val startPort by viewModel.startPort.collectAsStateWithLifecycle()
    val endPort by viewModel.endPort.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val concurrency by viewModel.concurrency.collectAsStateWithLifecycle()
    val aggressiveProbes by viewModel.aggressiveProbes.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val sourceContext = viewModel.sourceContext
    val hasInvalidHandoff by viewModel.hasInvalidHandoff.collectAsStateWithLifecycle()

    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (screenVisible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label         = "screen-alpha"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().alpha(screenAlpha),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // ── Header ──────────────────────────────────────────────────────────
            item {
                ToolHeroHeader(
                    title = stringResource(R.string.ports_screen_title),
                    subtitle = stringResource(R.string.ports_screen_subtitle),
                    icon = Icons.Default.Search,
                    onHelpClick = { showHelp = true }
                )
            }

            if (hasInvalidHandoff) {
                item {
                    Text(
                        text = stringResource(R.string.ports_invalid_handoff),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(PortsScreenTestTags.INVALID_HANDOFF),
                    )
                }
            }

            if (sourceContext == ToolSource.LAN) {
                item {
                    Text(
                        text = stringResource(R.string.ports_source_lan),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.testTag(PortsScreenTestTags.SOURCE_CONTEXT),
                    )
                }
            }

            // ── Input Card ──────────────────────────────────────────────────────
            item {
                PortScanInputCard(
                    host = host,
                    selectedPreset = selectedPreset,
                    startPort = startPort,
                    endPort = endPort,
                    timeoutMs = timeoutMs,
                    concurrency = concurrency,
                    aggressiveProbes = aggressiveProbes,
                    isScanning = uiState is PortScanUiState.Scanning,
                    recentHosts = recentHosts,
                    onHostChange = viewModel::onHostChange,
                    onPresetChange = viewModel::onPresetChange,
                    onStartPortChange = viewModel::onStartPortChange,
                    onEndPortChange = viewModel::onEndPortChange,
                    onTimeoutChange = viewModel::onTimeoutChange,
                    onConcurrencyChange = viewModel::onConcurrencyChange,
                    onAggressiveProbesChange = viewModel::onAggressiveProbesChange,
                    onStartScan = {
                        keyboardController?.hide()
                        requestLocalNetworkPermission(host)
                    },
                    onStopScan = viewModel::onStopScan,
                    onRemoveRecentHost = viewModel::removeRecentHost,
                    onClearRecentHosts = viewModel::clearRecentHosts
                )
            }

            // ── Result Area ─────────────────────────────────────────────────────
            item {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(AppMotion.enter(300)) togetherWith fadeOut(AppMotion.exit(200))
                    },
                    contentKey = { it::class },
                    label = "PortScanStateTransition"
                ) { state ->
                    when (state) {
                        is PortScanUiState.Idle -> PortScanIdleState()
                        is PortScanUiState.Scanning -> PortScanProgressCard(state)
                        is PortScanUiState.Error -> PortScanErrorCard(
                            message = if (state.isBudgetLimit) {
                                stringResource(R.string.ports_scan_budget_exceeded)
                            } else state.message,
                            onRetry = { requestLocalNetworkPermission(host) },
                            onClear = viewModel::onClear
                        )
                        is PortScanUiState.Finished -> { /* results shown below */ }
                    }
                }
            }

            // ── Finished: Summary Card ──────────────────────────────────────────
            if (uiState is PortScanUiState.Finished) {
                val finished = uiState as PortScanUiState.Finished
                val summary = finished.summary
                if (finished.completion == PortScanUiState.Completion.DEADLINE) {
                    item {
                        Text(
                            text = stringResource(R.string.ports_scan_deadline_partial),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    val shareSubject = stringResource(R.string.share_subject_ports, summary.host)
                    PortScanSummaryCard(
                        summary = summary,
                        onCopy = {
                            clipScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", buildScanReport(summary)))) }
                        },
                        onShare = {
                            context.shareText(
                                text = buildScanReport(summary),
                                subject = shareSubject
                            )
                        },
                        onClear = viewModel::onClear
                    )
                }

                // ── Open Ports Section ──────────────────────────────────────────
                val openPorts = summary.results.filter { it.status == PortStatus.OPEN }
                if (openPorts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ports_section_open),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(openPorts, key = { it.port }) { portResult ->
                        PortResultRow(portResult)
                    }
                }

                // ── All Results Section ─────────────────────────────────────────
                val allNonOpen = summary.results.filter { it.status != PortStatus.OPEN }
                if (allNonOpen.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.ports_section_all),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(
                                    if (showAll) stringResource(R.string.ports_show_open)
                                    else stringResource(R.string.ports_show_all)
                                )
                            }
                        }
                    }
                    if (showAll) {
                        items(allNonOpen, key = { it.port }) { portResult ->
                            PortResultRow(portResult)
                        }
                    }
                }

                // Empty state
                if (summary.results.none { it.status == PortStatus.OPEN }) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ports_no_open_ports),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Live results during scan ────────────────────────────────────────
            if (uiState is PortScanUiState.Scanning) {
                val scanning = uiState as PortScanUiState.Scanning
                val openLive = scanning.liveResults.filter { it.status == PortStatus.OPEN }
                if (openLive.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ports_section_open),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(openLive, key = { it.port }) { portResult ->
                        PortResultRow(portResult)
                    }
                }
            }
        }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_portscan_title),
            conceptHeading = stringResource(R.string.help_portscan_concept_heading),
            conceptBody = stringResource(R.string.help_portscan_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_portscan_what_heading), stringResource(R.string.help_portscan_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_portscan_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_portscan_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_portscan_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_portscan_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Input Card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PortScanInputCard(
    host: String,
    selectedPreset: PortScanPreset,
    startPort: String,
    endPort: String,
    timeoutMs: Int,
    concurrency: Int,
    aggressiveProbes: Boolean,
    isScanning: Boolean,
    recentHosts: List<String>,
    onHostChange: (String) -> Unit,
    onPresetChange: (PortScanPreset) -> Unit,
    onStartPortChange: (String) -> Unit,
    onEndPortChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onAggressiveProbesChange: (Boolean) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit
) {
    val normalizedHost = HostValidator.normalize(host)
    val isHostInvalid = host.isNotBlank() && normalizedHost == null
    val probeSwitchA11yLabel = stringResource(R.string.ports_aggressive_probes_a11y_label)
    val probeSwitchA11yState = stringResource(
        if (aggressiveProbes) R.string.ports_aggressive_probes_enabled_a11y
        else R.string.ports_aggressive_probes_disabled_a11y,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Host input
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.ports_host_label)) },
                placeholder = { Text(stringResource(R.string.ports_host_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (host.isNotEmpty()) {
                        IconButton(
                            onClick = { onHostChange("") },
                            enabled = !isScanning,
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (!isScanning && normalizedHost != null) onStartScan()
                }),
                isError = isHostInvalid,
                supportingText = if (isHostInvalid) {
                    { Text(stringResource(R.string.error_invalid_host)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            RecentHostsRow(
                recentHosts = recentHosts,
                onHostSelected = onHostChange,
                onRemoveHost = onRemoveRecentHost,
                onClearAll = onClearRecentHosts
            )

            // Preset selector
            var presetExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = !presetExpanded }
            ) {
                OutlinedTextField(
                    value = selectedPreset.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ports_preset_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PortsScreenTestTags.PRESET_FIELD)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false }
                ) {
                    PortScanPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                onPresetChange(preset)
                                presetExpanded = false
                            }
                        )
                    }
                }
            }

            // Custom port range (only shown for CUSTOM preset)
            val startNum = startPort.toIntOrNull()
            val endNum = endPort.toIntOrNull()
            val startInvalid = startPort.isNotEmpty() && (startNum == null || startNum !in 1..65535)
            val endInvalid = endPort.isNotEmpty() && (endNum == null || endNum !in 1..65535)
            val orderInvalid = startNum != null && endNum != null &&
                !startInvalid && !endInvalid && startNum > endNum
            val customRangeValid = selectedPreset != PortScanPreset.CUSTOM ||
                (startNum != null && endNum != null && !startInvalid && !endInvalid && !orderInvalid)

            AnimatedVisibility(visible = selectedPreset == PortScanPreset.CUSTOM) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = startPort,
                            onValueChange = { onStartPortChange(it.filter(Char::isDigit).take(5)) },
                            label = { Text(stringResource(R.string.ports_start_port_label)) },
                            singleLine = true,
                            isError = startInvalid || orderInvalid,
                            supportingText = if (startInvalid) {
                                { Text(stringResource(R.string.ports_range_error)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endPort,
                            onValueChange = { onEndPortChange(it.filter(Char::isDigit).take(5)) },
                            label = { Text(stringResource(R.string.ports_end_port_label)) },
                            singleLine = true,
                            isError = endInvalid || orderInvalid,
                            supportingText = if (endInvalid) {
                                { Text(stringResource(R.string.ports_range_error)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (startNum != null && endNum != null && !startInvalid && !endInvalid) {
                        RangeSlider(
                            value = startNum.coerceIn(1, 65535).toFloat()..
                                endNum.coerceIn(startNum.coerceIn(1, 65535), 65535).toFloat(),
                            onValueChange = { range ->
                                onStartPortChange(range.start.toInt().toString())
                                onEndPortChange(range.endInclusive.toInt().toString())
                            },
                            valueRange = 1f..65535f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    when {
                        orderInvalid -> Text(
                            text = stringResource(R.string.ports_range_order_error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        startNum != null && endNum != null && !startInvalid && !endInvalid -> Text(
                            text = pluralStringResource(R.plurals.ports_range_count, endNum - startNum + 1, endNum - startNum + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Timeout slider
            Column {
                Text(
                    text = "${stringResource(R.string.ports_timeout_label)}: ${timeoutMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = timeoutMs.toFloat(),
                    onValueChange = { onTimeoutChange(it.toInt()) },
                    valueRange = 200f..10000f,
                    steps = 49,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Concurrency slider
            Column {
                Text(
                    text = "${stringResource(R.string.ports_concurrency_label)}: $concurrency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = concurrency.toFloat(),
                    onValueChange = { onConcurrencyChange(it.toInt()) },
                    valueRange = 1f..500f,
                    steps = 498,
                    modifier = Modifier.fillMaxWidth().testTag(PortsScreenTestTags.CONCURRENCY_SLIDER)
                )
                AnimatedVisibility(visible = concurrency > 100) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.ports_concurrency_high_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ports_aggressive_probes_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ports_aggressive_probes_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = aggressiveProbes,
                    onCheckedChange = onAggressiveProbesChange,
                    enabled = !isScanning,
                    modifier = Modifier
                        .testTag(PortsScreenTestTags.AGGRESSIVE_PROBES_SWITCH)
                        .semantics {
                            contentDescription = probeSwitchA11yLabel
                            stateDescription = probeSwitchA11yState
                        },
                )
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    ToolStopButton(
                        text = stringResource(R.string.ports_stop_button),
                        onClick = onStopScan,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Button(
                        onClick = hapticAction(onStartScan),
                        enabled = normalizedHost != null && customRangeValid,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(PortsScreenTestTags.SCAN_BUTTON)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ports_scan_button))
                    }
                }
            }
        }
    }
}

// ── Idle State ────────────────────────────────────────────────────────────────

@Composable
private fun PortScanIdleState() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.ports_idle_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.ports_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Scanning Progress ─────────────────────────────────────────────────────────

@Composable
private fun PortScanProgressCard(state: PortScanUiState.Scanning) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "ScanProgress"
    )

    // Pulsing animation for the indicator
    val infiniteTransition = rememberInfiniteTransition(label = "ScanPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "ScanPulseScale"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ports_scanning_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val loadingCd = stringResource(R.string.a11y_loading)
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).semantics { contentDescription = loadingCd },
                    strokeCap = StrokeCap.Round,
                    strokeWidth = 3.dp
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                strokeCap = StrokeCap.Round
            )

            Text(
                text = pluralStringResource(
                    R.plurals.ports_progress_format,
                    state.totalCount,
                    state.scannedCount,
                    state.totalCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live stats
            val openSoFar = state.liveResults.count { it.status == PortStatus.OPEN }
            if (openSoFar > 0) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$openSoFar ${stringResource(R.string.ports_open_ports).lowercase()} found so far",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Error State ───────────────────────────────────────────────────────────────

@Composable
private fun PortScanErrorCard(
    message: String,
    onRetry: () -> Unit,
    onClear: () -> Unit
) {
    ToolErrorCard(
        title = stringResource(R.string.ports_error_title),
        message = message,
    ) {
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ports_retry_button))
        }
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.ports_clear_button))
        }
    }
}

// ── Summary Card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortScanSummaryCard(
    summary: PortScanSummary,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ports_result_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ports_copy_report))
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.ports_clear_button))
                    }
                }
            }

            // Host info
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${stringResource(R.string.ports_host_label_result)}: ${summary.host}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    summary.resolvedIp?.let { ip ->
                        Text(
                            text = stringResource(R.string.ports_resolved_ip, ip),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.ports_duration_label)}: " +
                                stringResource(R.string.ports_scan_duration, summary.scanDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Stats chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatChip(
                    label = stringResource(R.string.ports_open_ports),
                    value = summary.openPorts.toString(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatChip(
                    label = stringResource(R.string.ports_closed_ports),
                    value = summary.closedPorts.toString(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatChip(
                    label = stringResource(R.string.ports_filtered_ports),
                    value = summary.filteredPorts.toString(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                StatChip(
                    label = stringResource(R.string.ports_total_scanned),
                    value = summary.totalPorts.toString(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Port Result Row ───────────────────────────────────────────────────────────

@Composable
private fun PortResultRow(result: PortScanResult) {
    var targetAlpha by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { targetAlpha = 1f }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "port_row_alpha"
    )

    val statusColor = when (result.status) {
        PortStatus.OPEN     -> MaterialTheme.colorScheme.primary
        PortStatus.CLOSED   -> MaterialTheme.colorScheme.onSurfaceVariant
        PortStatus.FILTERED -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when (result.status) {
        PortStatus.OPEN     -> R.string.ports_open_label
        PortStatus.CLOSED   -> R.string.ports_closed_label
        PortStatus.FILTERED -> R.string.ports_filtered_label
    }
    val statusAlpha = when (result.status) {
        PortStatus.OPEN -> 1f
        else -> 0.65f
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(animatedAlpha * statusAlpha)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Port status indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(10.dp))

            // Port number
            Text(
                text = result.port.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(52.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Service name
                    result.serviceName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(statusLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Service description
                result.serviceDescription?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                result.tlsSubject?.let(TlsSubjectSanitizer::sanitize)?.takeIf(String::isNotBlank)?.let { subject ->
                    Text(
                        text = stringResource(R.string.ports_tls_subject, subject),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Banner
                if (result.banner != null || result.bannerTruncated) {
                    val banner = result.banner.orEmpty()
                    val visibleBanner = banner + if (result.bannerTruncated) "…" else ""
                    val bannerDescription = if (result.bannerTruncated) {
                        stringResource(R.string.ports_banner_truncated_content_description, banner)
                    } else {
                        banner
                    }
                    Text(
                        text = visibleBanner,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics {
                            contentDescription = bannerDescription
                        },
                    )
                }
            }

            // Response time
            Text(
                text = "${result.responseTimeMs}${stringResource(R.string.ports_ms_suffix)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ── Report builder ────────────────────────────────────────────────────────────

internal fun buildScanReport(summary: PortScanSummary): String = buildString {
    appendLine("=== Port Scan Report ===")
    appendLine("Host: ${summary.host}")
    summary.resolvedIp?.let { appendLine("IP: $it") }
    appendLine("Ports scanned: ${summary.totalPorts}")
    appendLine("Duration: ${summary.scanDurationMs} ms")
    appendLine()
    appendLine("Open:     ${summary.openPorts}")
    appendLine("Closed:   ${summary.closedPorts}")
    appendLine("Filtered: ${summary.filteredPorts}")
    appendLine()
    val openPorts = summary.results.filter { it.status == PortStatus.OPEN }
    if (openPorts.isNotEmpty()) {
        appendLine("--- Open Ports ---")
        openPorts.forEach { r ->
            val service = r.serviceName ?: "unknown"
            val banner = when {
                r.banner != null -> " | ${r.banner}${if (r.bannerTruncated) "…" else ""}"
                r.bannerTruncated -> " | …"
                else -> ""
            }
            appendLine("  ${r.port.toString().padEnd(6)} $service${banner}")
            r.tlsSubject?.let(TlsSubjectSanitizer::sanitize)?.takeIf(String::isNotBlank)?.let {
                appendLine("         TLS subject CN: $it")
            }
        }
    } else {
        appendLine("No open ports found.")
    }
}
