package com.example.netswissknife.app.ui.screens.lan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.netswissknife.app.R
import com.example.netswissknife.core.network.lan.LanHost
import com.example.netswissknife.core.network.lan.LanScanSummary

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun LanScreen(viewModel: LanScanViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val subnet by viewModel.subnet.collectAsState()
    val timeoutMs by viewModel.timeoutMs.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val isSubnetLoading by viewModel.isSubnetLoading.collectAsState()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanHeaderCard()

            LanInputCard(
                subnet = subnet,
                timeoutMs = timeoutMs,
                concurrency = concurrency,
                isSubnetLoading = isSubnetLoading,
                isScanning = uiState is LanScanUiState.Scanning,
                onSubnetChange = viewModel::onSubnetChange,
                onTimeoutChange = viewModel::onTimeoutChange,
                onConcurrencyChange = viewModel::onConcurrencyChange,
                onRefreshSubnet = viewModel::refreshSubnet,
                onStartScan = viewModel::startScan,
                onStopScan = viewModel::onStopScan,
            )

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 })
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "lan_state",
            ) { state ->
                when (state) {
                    is LanScanUiState.Idle -> LanIdleContent()
                    is LanScanUiState.Scanning -> LanScanningContent(state)
                    is LanScanUiState.Finished -> LanFinishedContent(
                        summary = state.summary,
                        expandedHostIp = state.expandedHostIp,
                        onToggleExpand = viewModel::onToggleHostExpanded,
                        onClear = viewModel::onClear,
                        onRescan = viewModel::startScan,
                    )
                    is LanScanUiState.Error -> LanErrorContent(
                        message = state.message,
                        onRetry = viewModel::startScan,
                        onClear = viewModel::onClear,
                    )
                }
            }
        }
    }
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun LanHeaderCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite,
                    )
                )
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lan,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.lan_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.lan_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun LanInputCard(
    subnet: String,
    timeoutMs: Int,
    concurrency: Int,
    isSubnetLoading: Boolean,
    isScanning: Boolean,
    onSubnetChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onRefreshSubnet: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.lan_config_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Subnet field
            OutlinedTextField(
                value = subnet,
                onValueChange = onSubnetChange,
                label = { Text(stringResource(R.string.lan_subnet_label)) },
                placeholder = { Text(stringResource(R.string.lan_subnet_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                },
                trailingIcon = {
                    Row {
                        if (subnet.isNotEmpty()) {
                            IconButton(onClick = { onSubnetChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }
                        if (isSubnetLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onRefreshSubnet) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.lan_detect_subnet),
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth(),
            )

            // Timeout slider
            LabeledSlider(
                label = stringResource(R.string.lan_timeout_label, timeoutMs),
                value = timeoutMs.toFloat(),
                onValueChange = { onTimeoutChange(it.toInt()) },
                valueRange = 100f..10_000f,
                steps = 0,
                enabled = !isScanning,
            )

            // Concurrency slider
            LabeledSlider(
                label = stringResource(R.string.lan_concurrency_label, concurrency),
                value = concurrency.toFloat(),
                onValueChange = { onConcurrencyChange(it.toInt()) },
                valueRange = 1f..500f,
                steps = 0,
                enabled = !isScanning,
            )

            // Action button
            if (isScanning) {
                Button(
                    onClick = onStopScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lan_stop_button))
                }
            } else {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subnet.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lan_scan_button))
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun LanIdleContent() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DeviceHub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.lan_idle_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.lan_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Scanning ──────────────────────────────────────────────────────────────────

@Composable
private fun LanScanningContent(state: LanScanUiState.Scanning) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Progress card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PulsingIndicator()
                        Text(
                            text = stringResource(R.string.lan_scanning_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = if (state.totalCount > 0)
                            stringResource(R.string.lan_progress_format, state.scannedCount, state.totalCount)
                        else "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val animProgress by animateFloatAsState(
                    targetValue = state.progress,
                    animationSpec = tween(300),
                    label = "scan_progress",
                )
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = stringResource(R.string.lan_hosts_found_format, state.hosts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Live host list
        if (state.hosts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.lan_live_hosts_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            state.hosts.forEach { host ->
                HostCard(host = host, expanded = false, onClick = {})
            }
        }
    }
}

@Composable
private fun PulsingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

// ── Finished ──────────────────────────────────────────────────────────────────

@Composable
private fun LanFinishedContent(
    summary: LanScanSummary,
    expandedHostIp: String?,
    onToggleExpand: (String) -> Unit,
    onClear: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Summary stats card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(R.string.lan_scan_complete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.lan_duration_format, summary.scanDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Stats grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip(
                        label = stringResource(R.string.lan_stat_scanned),
                        value = summary.totalScanned.toString(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    StatChip(
                        label = stringResource(R.string.lan_stat_alive),
                        value = summary.aliveHosts.toString(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    StatChip(
                        label = stringResource(R.string.lan_stat_subnet),
                        value = summary.subnet.substringAfter("/") + " prefix",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onRescan,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.lan_rescan_button))
                    }
                    FilledTonalButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.lan_clear_button))
                    }
                }
            }
        }

        // Host list
        if (summary.hosts.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.lan_no_hosts_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.lan_hosts_header, summary.hosts.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Network topology mini-map
            NetworkTopologyCard(hosts = summary.hosts)

            Spacer(Modifier.height(4.dp))

            summary.hosts.forEach { host ->
                HostCard(
                    host = host,
                    expanded = host.ip == expandedHostIp,
                    onClick = { onToggleExpand(host.ip) },
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f),
        )
    }
}

// ── Network topology mini-map ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkTopologyCard(hosts: List<LanHost>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.lan_topology_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Central router icon + device grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top,
            ) {
                // Gateway node
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val gateway = hosts.firstOrNull { it.isGateway } ?: hosts.first()
                    TopologyNode(
                        icon = Icons.Default.Router,
                        label = stringResource(R.string.lan_gateway_label),
                        ip = gateway.ip,
                        isGateway = true,
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Connected devices
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4,
                ) {
                    hosts.filter { !it.isGateway }.take(20).forEach { host ->
                        TopologyNode(
                            icon = hostIcon(host),
                            label = host.vendor ?: host.hostname?.split(".")?.firstOrNull() ?: host.ip.substringAfterLast("."),
                            ip = host.ip,
                            isGateway = false,
                        )
                    }
                    if (hosts.count { !it.isGateway } > 20) {
                        MoreDevicesNode(count = hosts.count { !it.isGateway } - 20)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopologyNode(
    icon: ImageVector,
    label: String,
    ip: String,
    isGateway: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.width(56.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isGateway) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGateway)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = ip.substringAfterLast("."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MoreDevicesNode(count: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.width(56.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.lan_more_devices),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Host card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostCard(
    host: LanHost,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (expanded)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "host_card_color",
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.background(containerColor)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (host.isGateway) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = hostIcon(host),
                                contentDescription = null,
                                tint = if (host.isGateway)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = host.ip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (host.isGateway) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lan_gateway_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            val subtitle = host.vendor ?: host.hostname ?: stringResource(R.string.lan_unknown_device)
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lan_ping_format, host.pingTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = pingColor(host.pingTimeMs),
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Open ports chips (always visible)
                if (host.openPorts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        host.openPorts.take(8).forEach { port ->
                            PortChip(port)
                        }
                        if (host.openPorts.size > 8) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = "+${host.openPorts.size - 8}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                // Expanded details
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                ) {
                    HostDetailPanel(host)
                }
            }
        }
    }
}

@Composable
private fun HostDetailPanel(host: LanHost) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.lan_details_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        DetailRow(label = stringResource(R.string.lan_detail_ip), value = host.ip)
        host.hostname?.let {
            DetailRow(label = stringResource(R.string.lan_detail_hostname), value = it)
        }
        host.macAddress?.let {
            DetailRow(label = stringResource(R.string.lan_detail_mac), value = it)
        }
        host.vendor?.let {
            DetailRow(label = stringResource(R.string.lan_detail_vendor), value = it)
        }
        DetailRow(
            label = stringResource(R.string.lan_detail_ping),
            value = stringResource(R.string.lan_ping_format, host.pingTimeMs),
        )
        DetailRow(
            label = stringResource(R.string.lan_detail_role),
            value = if (host.isGateway)
                stringResource(R.string.lan_role_gateway)
            else
                stringResource(R.string.lan_role_device),
        )

        if (host.openPorts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.lan_open_ports_title, host.openPorts.size),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            host.openPorts.forEach { port ->
                DetailRow(
                    label = "  $port",
                    value = portServiceName(port),
                )
            }
        } else {
            DetailRow(
                label = stringResource(R.string.lan_open_ports_title, 0),
                value = stringResource(R.string.lan_no_open_ports),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (label.contains("IP") || label.contains("MAC") || label.contains("Ping"))
                FontFamily.Monospace
            else
                FontFamily.Default,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun PortChip(port: Int) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = port.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun LanErrorContent(
    message: String,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.lan_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.lan_retry_button))
                }
                FilledTonalButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.lan_clear_button))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun pingColor(pingMs: Long) = when {
    pingMs < 10 -> MaterialTheme.colorScheme.primary
    pingMs < 50 -> MaterialTheme.colorScheme.secondary
    pingMs < 200 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun hostIcon(host: LanHost): ImageVector = when {
    host.isGateway -> Icons.Default.Router
    host.vendor?.contains("Apple", ignoreCase = true) == true -> Icons.Default.Computer
    host.vendor?.contains("Raspberry", ignoreCase = true) == true -> Icons.Default.SmartToy
    host.openPorts.contains(80) || host.openPorts.contains(443) -> Icons.Default.Computer
    host.openPorts.contains(22) -> Icons.Default.Computer
    else -> Icons.Default.DeviceHub
}

private fun portServiceName(port: Int): String = when (port) {
    21 -> "FTP"
    22 -> "SSH"
    23 -> "Telnet"
    25 -> "SMTP"
    53 -> "DNS"
    80 -> "HTTP"
    110 -> "POP3"
    139 -> "NetBIOS"
    143 -> "IMAP"
    443 -> "HTTPS"
    445 -> "SMB"
    3306 -> "MySQL"
    3389 -> "RDP"
    5900 -> "VNC"
    8080 -> "HTTP-Alt"
    8443 -> "HTTPS-Alt"
    else -> "TCP/$port"
}
