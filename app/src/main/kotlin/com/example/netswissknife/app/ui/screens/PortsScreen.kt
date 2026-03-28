package com.example.netswissknife.app.ui.screens

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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netswissknife.app.R
import com.example.netswissknife.app.ui.screens.portscan.PortScanUiState
import com.example.netswissknife.app.ui.screens.portscan.PortScanViewModel
import com.example.netswissknife.core.domain.PortScanPreset
import com.example.netswissknife.core.network.portscan.PortScanResult
import com.example.netswissknife.core.network.portscan.PortScanSummary
import com.example.netswissknife.core.network.portscan.PortStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortsScreen(viewModel: PortScanViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val startPort by viewModel.startPort.collectAsStateWithLifecycle()
    val endPort by viewModel.endPort.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val concurrency by viewModel.concurrency.collectAsStateWithLifecycle()

    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (screenVisible) 1f else 0f,
        animationSpec = tween(400),
        label         = "screen-alpha"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    var showAll by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().alpha(screenAlpha),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // ── Header ──────────────────────────────────────────────────────────
            item {
                PortScanHeader()
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
                    isScanning = uiState is PortScanUiState.Scanning,
                    onHostChange = viewModel::onHostChange,
                    onPresetChange = viewModel::onPresetChange,
                    onStartPortChange = viewModel::onStartPortChange,
                    onEndPortChange = viewModel::onEndPortChange,
                    onTimeoutChange = viewModel::onTimeoutChange,
                    onConcurrencyChange = viewModel::onConcurrencyChange,
                    onStartScan = {
                        keyboardController?.hide()
                        viewModel.startScan()
                    },
                    onStopScan = viewModel::onStopScan
                )
            }

            // ── Result Area ─────────────────────────────────────────────────────
            item {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    contentKey = { it::class },
                    label = "PortScanStateTransition"
                ) { state ->
                    when (state) {
                        is PortScanUiState.Idle -> PortScanIdleState()
                        is PortScanUiState.Scanning -> PortScanProgressCard(state)
                        is PortScanUiState.Error -> PortScanErrorCard(
                            message = state.message,
                            onRetry = viewModel::startScan,
                            onClear = viewModel::onClear
                        )
                        is PortScanUiState.Finished -> { /* results shown below */ }
                    }
                }
            }

            // ── Finished: Summary Card ──────────────────────────────────────────
            if (uiState is PortScanUiState.Finished) {
                val summary = (uiState as PortScanUiState.Finished).summary
                item {
                    PortScanSummaryCard(
                        summary = summary,
                        onCopy = {
                            clipboard.setText(AnnotatedString(buildScanReport(summary)))
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
                    items(openPorts) { portResult ->
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
                        items(allNonOpen) { portResult ->
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
                    items(openLive) { portResult ->
                        PortResultRow(portResult)
                    }
                }
            }
        }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun PortScanHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.ports_screen_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.ports_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
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
    isScanning: Boolean,
    onHostChange: (String) -> Unit,
    onPresetChange: (PortScanPreset) -> Unit,
    onStartPortChange: (String) -> Unit,
    onEndPortChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
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
                        IconButton(onClick = { onHostChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onStartScan() }),
                modifier = Modifier.fillMaxWidth()
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
                        .menuAnchor()
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
            AnimatedVisibility(visible = selectedPreset == PortScanPreset.CUSTOM) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = startPort,
                        onValueChange = onStartPortChange,
                        label = { Text(stringResource(R.string.ports_start_port_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endPort,
                        onValueChange = onEndPortChange,
                        label = { Text(stringResource(R.string.ports_end_port_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
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
                    valueRange = 10f..300f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    Button(
                        onClick = onStopScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ports_stop_button))
                    }
                } else {
                    Button(
                        onClick = onStartScan,
                        enabled = host.isNotBlank(),
                        modifier = Modifier.weight(1f)
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
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
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
                text = stringResource(
                    R.string.ports_progress_format,
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ports_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

// ── Summary Card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortScanSummaryCard(
    summary: PortScanSummary,
    onCopy: () -> Unit,
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
    val cardAlpha = when (result.status) {
        PortStatus.OPEN -> 1f
        else -> 0.65f
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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

                // Banner
                result.banner?.let { banner ->
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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

private fun buildScanReport(summary: PortScanSummary): String = buildString {
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
            val banner = r.banner?.let { " | $it" } ?: ""
            appendLine("  ${r.port.toString().padEnd(6)} $service${banner}")
        }
    } else {
        appendLine("No open ports found.")
    }
}
