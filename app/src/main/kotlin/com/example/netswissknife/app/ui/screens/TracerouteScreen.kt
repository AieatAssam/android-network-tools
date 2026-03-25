package com.example.netswissknife.app.ui.screens

import android.graphics.Paint as NativePaint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netswissknife.app.R
import com.example.netswissknife.app.ui.screens.traceroute.TracerouteUiState
import com.example.netswissknife.app.ui.screens.traceroute.TracerouteViewModel
import com.example.netswissknife.app.ui.screens.traceroute.TracerouteViewMode
import com.example.netswissknife.app.ui.screens.traceroute.WorldMapData
import com.example.netswissknife.core.network.traceroute.HopGeoLocation
import com.example.netswissknife.core.network.traceroute.HopResult
import com.example.netswissknife.core.network.traceroute.HopStatus
import com.example.netswissknife.core.network.traceroute.TracerouteProbeType
import com.example.netswissknife.core.network.traceroute.TracerouteResult
import kotlin.math.sqrt

// ── Screen entry point ────────────────────────────────────────────────────────

@Composable
fun TracerouteScreen(viewModel: TracerouteViewModel = hiltViewModel()) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val host         by viewModel.host.collectAsStateWithLifecycle()
    val maxHops      by viewModel.maxHops.collectAsStateWithLifecycle()
    val timeoutMs    by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val probesPerHop by viewModel.probesPerHop.collectAsStateWithLifecycle()
    val probeType    by viewModel.probeType.collectAsStateWithLifecycle()
    val packetSize   by viewModel.packetSize.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier          = Modifier.fillMaxSize(),
            contentPadding    = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TracerouteHeroHeader() }

            item {
                TracerouteInputCard(
                    host                = host,
                    maxHops             = maxHops,
                    timeoutMs           = timeoutMs,
                    probesPerHop        = probesPerHop,
                    probeType           = probeType,
                    packetSize          = packetSize,
                    isRunning           = uiState is TracerouteUiState.Running,
                    onHostChange        = viewModel::onHostChange,
                    onMaxHopsChange     = viewModel::onMaxHopsChange,
                    onTimeoutChange     = viewModel::onTimeoutChange,
                    onProbesPerHopChange = viewModel::onProbesPerHopChange,
                    onProbeTypeChange   = viewModel::onProbeTypeChange,
                    onPacketSizeChange  = viewModel::onPacketSizeChange,
                    onToggleMtuDiscovery = viewModel::onToggleMtuDiscovery,
                    onStart             = viewModel::startTrace,
                    onStop              = viewModel::onStop
                )
            }

            item {
                AnimatedContent(
                    targetState   = uiState,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 })
                            .togetherWith(fadeOut(tween(200)))
                    },
                    label = "traceroute-state"
                ) { state ->
                    when (state) {
                        is TracerouteUiState.Idle     -> TracerouteIdlePrompt()
                        is TracerouteUiState.Running  -> TracerouteRunningPanel(state)
                        is TracerouteUiState.Finished -> TracerouteFinishedPanel(
                            state          = state,
                            onToggleMode   = viewModel::onToggleViewMode,
                            onClear        = viewModel::onClear
                        )
                        is TracerouteUiState.Error    -> TracerouteErrorPanel(
                            state   = state,
                            onRetry = viewModel::onRetry,
                            onClear = viewModel::onClear
                        )
                    }
                }
            }
        }
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteHeroHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "hero-alpha"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .alpha(pulseAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Public,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text  = stringResource(R.string.traceroute_screen_title),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text  = stringResource(R.string.traceroute_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun TracerouteInputCard(
    host: String,
    maxHops: Int,
    timeoutMs: Int,
    probesPerHop: Int,
    probeType: TracerouteProbeType,
    packetSize: Int,
    isRunning: Boolean,
    onHostChange: (String) -> Unit,
    onMaxHopsChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onProbesPerHopChange: (Int) -> Unit,
    onProbeTypeChange: (TracerouteProbeType) -> Unit,
    onPacketSizeChange: (Int) -> Unit,
    onToggleMtuDiscovery: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val mtuDiscovery = packetSize == 0

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text  = stringResource(R.string.traceroute_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            // Host field
            OutlinedTextField(
                value         = host,
                onValueChange = onHostChange,
                label         = { Text(stringResource(R.string.traceroute_host_label)) },
                placeholder   = { Text(stringResource(R.string.traceroute_host_placeholder)) },
                leadingIcon   = { Icon(Icons.Default.Router, null) },
                trailingIcon  = {
                    if (host.isNotEmpty()) {
                        IconButton(onClick = { onHostChange("") }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.clear))
                        }
                    }
                },
                singleLine    = true,
                enabled       = !isRunning,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    if (!isRunning) onStart()
                })
            )

            // Probe protocol selector (ICMP / UDP)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text  = stringResource(R.string.traceroute_probe_type_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = probeType == TracerouteProbeType.ICMP,
                        onClick  = { if (!isRunning) onProbeTypeChange(TracerouteProbeType.ICMP) },
                        label    = { Text(stringResource(R.string.traceroute_probe_icmp)) },
                        enabled  = !isRunning
                    )
                    FilterChip(
                        selected = probeType == TracerouteProbeType.UDP,
                        onClick  = { if (!isRunning) onProbeTypeChange(TracerouteProbeType.UDP) },
                        label    = { Text(stringResource(R.string.traceroute_probe_udp)) },
                        enabled  = !isRunning
                    )
                }
            }

            // Max hops slider
            SliderRow(
                label    = stringResource(R.string.traceroute_max_hops_label),
                value    = maxHops.toFloat(),
                valueRange = 5f..64f,
                steps    = 11,
                display  = "$maxHops",
                enabled  = !isRunning,
                onValueChangeFinished = { onMaxHopsChange(it.toInt()) }
            )

            // Timeout slider
            SliderRow(
                label    = stringResource(R.string.traceroute_timeout_label),
                value    = timeoutMs.toFloat(),
                valueRange = 500f..10_000f,
                steps    = 0,
                display  = "${timeoutMs / 1_000.0}s",
                enabled  = !isRunning,
                onValueChangeFinished = { onTimeoutChange(it.toInt()) }
            )

            // Probes per hop slider
            SliderRow(
                label    = stringResource(R.string.traceroute_probes_per_hop_label),
                value    = probesPerHop.toFloat(),
                valueRange = 1f..5f,
                steps    = 3,
                display  = "$probesPerHop",
                enabled  = !isRunning,
                onValueChangeFinished = { onProbesPerHopChange(it.toInt()) }
            )

            // Packet size: MTU discovery toggle + fixed-size slider
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = stringResource(R.string.traceroute_mtu_discovery_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Switch(
                    checked         = mtuDiscovery,
                    onCheckedChange = { if (!isRunning) onToggleMtuDiscovery(it) },
                    enabled         = !isRunning
                )
            }

            AnimatedVisibility(visible = !mtuDiscovery) {
                SliderRow(
                    label    = stringResource(R.string.traceroute_packet_size_label),
                    value    = packetSize.toFloat().coerceIn(28f, 1472f),
                    valueRange = 28f..1472f,
                    steps    = 0,
                    display  = "$packetSize B",
                    enabled  = !isRunning,
                    onValueChangeFinished = { onPacketSizeChange(it.toInt()) }
                )
            }

            // Action button
            if (isRunning) {
                Button(
                    onClick  = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.traceroute_stop_button))
                }
            } else {
                Button(
                    onClick  = { keyboard?.hide(); onStart() },
                    enabled  = host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Public, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.traceroute_start_button))
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    enabled: Boolean,
    onValueChangeFinished: (Float) -> Unit
) {
    var localValue by remember(value) { mutableStateOf(value) }
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                display,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value        = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange   = valueRange,
            steps        = steps,
            enabled      = enabled
        )
    }
}

// ── Idle prompt ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteIdlePrompt() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Public,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(40.dp)
            )
            Text(
                text      = stringResource(R.string.traceroute_idle_title),
                style     = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text      = stringResource(R.string.traceroute_idle_subtitle),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Running panel ─────────────────────────────────────────────────────────────

@Composable
private fun TracerouteRunningPanel(state: TracerouteUiState.Running) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status header
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text  = stringResource(R.string.traceroute_running_title, state.host),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text  = stringResource(R.string.traceroute_running_subtitle, state.hops.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Live hop list
        state.hops.forEachIndexed { index, hop ->
            var shown by remember(hop.hopNumber) { mutableStateOf(false) }
            LaunchedEffect(hop.hopNumber) { shown = true }
            AnimatedVisibility(
                visible = shown,
                enter   = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }
            ) {
                HopCard(hop = hop, index = index)
            }
        }
    }
}

// ── Finished panel ────────────────────────────────────────────────────────────

@Composable
private fun TracerouteFinishedPanel(
    state: TracerouteUiState.Finished,
    onToggleMode: () -> Unit,
    onClear: () -> Unit
) {
    val result = state.result
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Summary stats card
        TraceStatsSummary(result = result, onClear = onClear)

        // World map (always visible)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Map,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.traceroute_map_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    val geoCount = result.geoLocatedHops.size
                    if (geoCount > 0) {
                        Text(
                            stringResource(R.string.traceroute_map_geo_count, geoCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (result.geoLocatedHops.isEmpty()) {
                    Box(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment  = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FmdGood,
                                null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.traceroute_map_no_geo),
                                style     = MaterialTheme.typography.bodySmall,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    WorldMapCanvas(
                        hops     = result.hops,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // Tab row: Hop Details / Raw Output
        val tabIndex = if (state.viewMode == TracerouteViewMode.Visual) 0 else 1
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick  = { if (tabIndex != 0) onToggleMode() },
                icon     = { Icon(Icons.Default.Router, null, Modifier.size(16.dp)) },
                text     = { Text(stringResource(R.string.traceroute_tab_hops)) }
            )
            Tab(
                selected = tabIndex == 1,
                onClick  = { if (tabIndex != 1) onToggleMode() },
                icon     = { Icon(Icons.Default.TextSnippet, null, Modifier.size(16.dp)) },
                text     = { Text(stringResource(R.string.traceroute_tab_raw)) }
            )
        }

        Crossfade(targetState = state.viewMode, label = "view-mode") { mode ->
            when (mode) {
                TracerouteViewMode.Visual -> HopDetailList(result.hops)
                TracerouteViewMode.Raw    -> RawOutputCard(result.rawOutput)
            }
        }
    }
}

// ── Stats summary ─────────────────────────────────────────────────────────────

@Composable
private fun TraceStatsSummary(result: TracerouteResult, onClear: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.traceroute_result_header),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.traceroute_clear_button))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(
                    label = stringResource(R.string.traceroute_stat_hops),
                    value = "${result.hops.size}"
                )
                StatChip(
                    label = stringResource(R.string.traceroute_stat_time),
                    value = if (result.totalTimeMs > 0) "${result.totalTimeMs}ms" else "—"
                )
                StatChip(
                    label = stringResource(R.string.traceroute_stat_reached),
                    value = if (result.reachedDestination)
                        stringResource(R.string.traceroute_stat_yes)
                    else
                        stringResource(R.string.traceroute_stat_no)
                )
            }
            if (result.resolvedIp != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.traceroute_resolved_ip, result.resolvedIp!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── World map canvas ──────────────────────────────────────────────────────────

@Composable
private fun WorldMapCanvas(hops: List<HopResult>, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "map-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.4f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse-scale"
    )
    val dashOffset by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 30f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label         = "dash-offset"
    )

    // Geo-located hops only
    val geoHops = hops.filter { it.geoLocation != null }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ── Background ─────────────────────────────────────────────────────
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF0A1628), Color(0xFF0D2240))
            )
        )

        // ── Grid lines ─────────────────────────────────────────────────────
        val gridColor = Color(0xFF1E3A5F)
        val gridStroke = Stroke(1f)
        // Latitude lines every 30°
        for (lat in listOf(60, 30, 0, -30, -60)) {
            val y = ((90f - lat) / 180f) * h
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        // Longitude lines every 60°
        for (lon in listOf(-120, -60, 0, 60, 120)) {
            val x = ((lon + 180f) / 360f) * w
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }

        // ── Continent fills ────────────────────────────────────────────────
        val landColor  = Color(0xFF1A4040)
        val landBorder = Color(0xFF2A6060)

        WorldMapData.continents.forEach { points ->
            if (points.size < 4) return@forEach
            val path = Path()
            path.moveTo(points[0] * w, points[1] * h)
            var i = 2
            while (i < points.size - 1) {
                path.lineTo(points[i] * w, points[i + 1] * h)
                i += 2
            }
            path.close()
            drawPath(path, landColor, style = Fill)
            drawPath(path, landBorder, style = Stroke(width = 1.5f))
        }

        // ── Arc lines between consecutive geo-located hops ─────────────────
        if (geoHops.size >= 2) {
            for (idx in 0 until geoHops.size - 1) {
                val a = geoHops[idx].geoLocation!!
                val b = geoHops[idx + 1].geoLocation!!

                val x1 = ((a.lon + 180f) / 360f).toFloat() * w
                val y1 = ((90f - a.lat) / 180f).toFloat() * h
                val x2 = ((b.lon + 180f) / 360f).toFloat() * w
                val y2 = ((90f - b.lat) / 180f).toFloat() * h

                val dx   = x2 - x1
                val dy   = y2 - y1
                val dist = sqrt(dx * dx + dy * dy)
                val midX = (x1 + x2) / 2f
                val midY = (y1 + y2) / 2f - dist * 0.35f  // arc control point

                // Glow trail
                drawContext.canvas.nativeCanvas.apply {
                    val glowPaint = NativePaint().apply {
                        isAntiAlias = true
                        style       = NativePaint.Style.STROKE
                        strokeWidth = 6f
                        color       = android.graphics.Color.argb(40, 100, 200, 255)
                        strokeCap   = NativePaint.Cap.ROUND
                    }
                    val path = android.graphics.Path().apply {
                        moveTo(x1, y1)
                        quadTo(midX, midY, x2, y2)
                    }
                    drawPath(path, glowPaint)
                }

                // Main arc (dashed, animated)
                val arcPath = Path()
                arcPath.moveTo(x1, y1)
                // Approximate quadratic curve with line segments
                val segments = 20
                for (s in 1..segments) {
                    val t  = s.toFloat() / segments
                    val qx = (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * midX + t * t * x2
                    val qy = (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * midY + t * t * y2
                    arcPath.lineTo(qx, qy)
                }
                drawPath(
                    arcPath,
                    Color(0xFF64C8FF),
                    style = Stroke(
                        width    = 2f,
                        cap      = StrokeCap.Round,
                        join     = StrokeJoin.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(12f, 8f), dashOffset
                        )
                    )
                )
            }
        }

        // ── Hop markers ───────────────────────────────────────────────────
        geoHops.forEachIndexed { idx, hop ->
            val geo = hop.geoLocation!!
            val x   = ((geo.lon + 180f) / 360f).toFloat() * w
            val y   = ((90f - geo.lat) / 180f).toFloat() * h

            val isLast = idx == geoHops.lastIndex
            val color  = rttColor(hop.rtTimeMs)

            // Outer glow ring (pulsing on last hop)
            val outerR = if (isLast) 14f * pulseScale else 12f
            drawCircle(color.copy(alpha = 0.25f), outerR, Offset(x, y))
            // Mid ring
            drawCircle(color.copy(alpha = 0.5f), 8f, Offset(x, y))
            // Core dot
            drawCircle(color, 5f, Offset(x, y))
            // White center
            drawCircle(Color.White, 2f, Offset(x, y))

            // Hop number label
            drawContext.canvas.nativeCanvas.apply {
                val textPaint = NativePaint().apply {
                    isAntiAlias = true
                    textSize    = 20f
                    this.color  = android.graphics.Color.WHITE
                    typeface    = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign   = NativePaint.Align.CENTER
                    setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
                }
                drawText("${hop.hopNumber}", x, y - 14f, textPaint)
            }
        }
    }
}

private fun rttColor(rtTimeMs: Long?): Color = when {
    rtTimeMs == null     -> Color(0xFF9E9E9E)
    rtTimeMs < 50        -> Color(0xFF4CAF50)
    rtTimeMs < 150       -> Color(0xFFCDDC39)
    rtTimeMs < 300       -> Color(0xFFFF9800)
    else                 -> Color(0xFFF44336)
}

// ── Hop detail list ───────────────────────────────────────────────────────────

@Composable
private fun HopDetailList(hops: List<HopResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hops.forEachIndexed { index, hop ->
            HopCard(hop = hop, index = index)
        }
    }
}

@Composable
private fun HopCard(hop: HopResult, index: Int) {
    val hopColor = rttColor(hop.rtTimeMs)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hop number badge
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(hopColor.copy(alpha = 0.15f))
                    .border(1.5.dp, hopColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "${hop.hopNumber}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color      = hopColor
                    )
                )
            }

            Spacer(Modifier.width(12.dp))

            // Main info
            Column(modifier = Modifier.weight(1f)) {
                when (hop.status) {
                    HopStatus.TIMEOUT -> {
                        Text(
                            text  = "* * *",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            stringResource(R.string.traceroute_hop_timeout),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HopStatus.ERROR -> {
                        Text(
                            hop.ip ?: "Error",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HopStatus.SUCCESS -> {
                        Text(
                            hop.ip ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis
                        )
                        if (hop.hostname != null) {
                            Text(
                                hop.hostname!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (hop.geoLocation != null) {
                            GeoLocationChip(hop.geoLocation!!)
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // RTT badge
            Column(horizontalAlignment = Alignment.End) {
                if (hop.rtTimeMs != null) {
                    Surface(
                        color  = hopColor.copy(alpha = 0.15f),
                        shape  = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text     = "${hop.rtTimeMs}ms",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style    = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color      = hopColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeoLocationChip(geo: HopGeoLocation) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(top = 2.dp)
    ) {
        Icon(
            Icons.Default.FmdGood,
            null,
            modifier = Modifier.size(12.dp),
            tint     = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.width(3.dp))
        val location = buildString {
            if (geo.city.isNotBlank()) append("${geo.city}, ")
            append(geo.country)
        }
        Text(
            text  = location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
        if (geo.asn != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "· ${geo.asn}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Raw output card ───────────────────────────────────────────────────────────

@Composable
private fun RawOutputCard(rawOutput: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.traceroute_raw_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Icon(
                    Icons.Default.Info,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text  = rawOutput,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Error panel ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteErrorPanel(
    state: TracerouteUiState.Error,
    onRetry: () -> Unit,
    onClear: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "error-scale"
    )
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    null,
                    tint     = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                stringResource(R.string.traceroute_error_title),
                style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color     = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                state.message,
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onClear) {
                    Text(stringResource(R.string.traceroute_clear_button))
                }
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.traceroute_retry_button))
                }
            }
        }
    }
}
