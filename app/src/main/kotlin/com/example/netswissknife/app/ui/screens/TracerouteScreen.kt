package com.example.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.netswissknife.core.network.traceroute.HopGeoLocation
import com.example.netswissknife.core.network.traceroute.HopResult
import com.example.netswissknife.core.network.traceroute.HopStatus
import com.example.netswissknife.core.network.traceroute.TracerouteProbeType
import com.example.netswissknife.core.network.traceroute.TracerouteResult
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.spatialk.geojson.Position

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
                    contentKey = { it::class },
                    label = "traceroute-state"
                ) { state ->
                    when (state) {
                        is TracerouteUiState.Idle     -> TracerouteIdlePrompt()
                        is TracerouteUiState.Running  -> TracerouteRunningPanel(state)
                        is TracerouteUiState.Finished -> {
                            // mapCompositionReady is sticky: once the enter-transition
                            // settles it stays true so MaplibreMap is never torn down
                            // and re-composed just because transition.isRunning briefly
                            // flips again (e.g. on a subsequent viewMode change).
                            var mapCompositionReady by remember { mutableStateOf(false) }
                            LaunchedEffect(transition.isRunning) {
                                if (!transition.isRunning) mapCompositionReady = true
                            }
                            TracerouteFinishedPanel(
                                state               = state,
                                onToggleMode        = viewModel::onToggleViewMode,
                                onClear             = viewModel::onClear,
                                mapCompositionReady = mapCompositionReady
                            )
                        }
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

        // Live hop list – always render in hop-number order.
        // key(hop.hopNumber) gives Compose a stable identity per hop so that existing
        // animations are preserved across recompositions and AnimatedContent's
        // SubcomposeLayout only inserts one new slot per new hop.
        //
        // Each hop fades in via animateFloatAsState rather than AnimatedVisibility.
        // AnimatedVisibility creates its own animation infrastructure (Transition<Boolean>)
        // inside AnimatedContent's SubcomposeLayout; when the Running panel exits while
        // per-hop enter-animations are still in progress the outgoing subcomposition reads
        // a disposed State<Boolean>, causing IllegalStateException.  animateFloatAsState
        // is a plain value animation with no SubcomposeLayout of its own, so it is safe
        // to interrupt mid-animation when the outer AnimatedContent exits.
        state.hops.sortedBy { it.hopNumber }.forEachIndexed { index, hop ->
            key(hop.hopNumber) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val hopAlpha by animateFloatAsState(
                    targetValue   = if (shown) 1f else 0f,
                    animationSpec = tween(250),
                    label         = "hop-alpha"
                )
                Box(Modifier.alpha(hopAlpha)) {
                    HopCard(hop = hop, index = index)
                }
            }
        }
    }
}

// ── Finished panel ────────────────────────────────────────────────────────────

@Composable
private fun TracerouteFinishedPanel(
    state: TracerouteUiState.Finished,
    onToggleMode: () -> Unit,
    onClear: () -> Unit,
    mapCompositionReady: Boolean = false
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
                } else if (!mapCompositionReady) {
                    // Defer MaplibreMap until the AnimatedContent enter-transition has
                    // fully settled.  Composing MaplibreMap while the transition frame
                    // is still executing causes the library's internal CompositionLocal
                    // reads to fail with IllegalStateException.
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    MaplibreTracerouteMap(
                        hops     = result.hops,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
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

// ── MapLibre traceroute map (OpenFreeMap vector tiles, no API key) ─────────────

@Composable
private fun MaplibreTracerouteMap(hops: List<HopResult>, modifier: Modifier = Modifier) {
    val geoHops = remember(hops) { hops.filter { it.geoLocation != null } }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = 20.0, longitude = 0.0),
            zoom   = 1.5
        )
    )

    // Build GeoJSON once per unique hop set; single FeatureCollection holds
    // the LineString (rendered by LineLayer) and Points (rendered by CircleLayer).
    val geoJson = remember(geoHops) { buildTracerouteGeoJson(geoHops) }

    // Animate camera to fit all geo-located hops whenever the set changes
    LaunchedEffect(geoHops) {
        if (geoHops.isEmpty()) return@LaunchedEffect
        val lats = geoHops.map { it.geoLocation!!.lat }
        val lons = geoHops.map { it.geoLocation!!.lon }
        val centerLat = (lats.min() + lats.max()) / 2.0
        val centerLon = (lons.min() + lons.max()) / 2.0
        val span = maxOf(lats.max() - lats.min(), lons.max() - lons.min())
        val zoom = when {
            span < 1.0  -> 9.0
            span < 5.0  -> 6.0
            span < 20.0 -> 4.0
            span < 60.0 -> 2.5
            else        -> 1.5
        }
        cameraState.animateTo(
            CameraPosition(
                target = Position(latitude = centerLat, longitude = centerLon),
                zoom   = zoom
            )
        )
    }

    // Single GeoJSON source shared by both layers; MapLibre routes geometry types
    // to the appropriate layer automatically (LineString → LineLayer, Point → CircleLayer).
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(geoJson))

    MaplibreMap(
        modifier   = modifier,
        cameraState = cameraState,
        baseStyle  = BaseStyle.Uri("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")
    ) {
        // Polyline connecting hops in order
        LineLayer(
            id     = "traceroute-line",
            source = source,
            color  = const(Color(0xFF64C8FF)),
            width  = const(3.dp),
            cap    = const(LineCap.Round),
            join   = const(LineJoin.Round)
        )
        // Circle marker at each geo-located hop
        CircleLayer(
            id          = "traceroute-hops",
            source      = source,
            color       = const(Color(0xFF2196F3)),
            radius      = const(7.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )
    }
}

/**
 * Builds a GeoJSON FeatureCollection containing:
 *  - a LineString connecting all geo-located hops in order (only when there are 2+ hops,
 *    because GeoJSON spec RFC 7946 §3.1.4 requires a LineString to have ≥ 2 positions;
 *    a single-coordinate LineString is invalid and causes MapLibre to silently drop the layer)
 *  - one Point feature per hop (always included)
 */
private fun buildTracerouteGeoJson(geoHops: List<HopResult>): String {
    if (geoHops.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    return buildString {
        append("""{"type":"FeatureCollection","features":[""")
        var needsComma = false
        // LineString connecting all hops – only valid with 2+ coordinates
        if (geoHops.size >= 2) {
            append("""{"type":"Feature","geometry":{"type":"LineString","coordinates":[""")
            geoHops.forEachIndexed { i, hop ->
                if (i > 0) append(",")
                val geo = hop.geoLocation!!
                append("[${geo.lon},${geo.lat}]")
            }
            append("""]},"properties":{}}""")
            needsComma = true
        }
        // One Point per hop (CircleLayer renders only Point geometries)
        geoHops.forEach { hop ->
            if (needsComma) append(",")
            needsComma = true
            val geo = hop.geoLocation!!
            append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[${geo.lon},${geo.lat}]},"properties":{"hop":${hop.hopNumber}}}""")
        }
        append("]}")
    }
}

private fun rttColor(rtTimeMs: Long?): Color = when {
    rtTimeMs == null -> Color(0xFF9E9E9E)
    rtTimeMs < 50    -> Color(0xFF4CAF50)
    rtTimeMs < 150   -> Color(0xFFCDDC39)
    rtTimeMs < 300   -> Color(0xFFFF9800)
    else             -> Color(0xFFF44336)
}

// ── Hop detail list ───────────────────────────────────────────────────────────

@Composable
private fun HopDetailList(hops: List<HopResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hops.sortedBy { it.hopNumber }.forEachIndexed { index, hop ->
            key(hop.hopNumber) {
                HopCard(hop = hop, index = index)
            }
        }
    }
}

@Composable
private fun HopCard(hop: HopResult, index: Int) {
    val hopColor = rttColor(hop.rtTimeMs)
    var expanded by remember(hop.hopNumber) { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
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

                // RTT badge + expand chevron
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
                        Spacer(Modifier.height(4.dp))
                    }
                    Icon(
                        imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded detail section
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier            = Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                    hop.ip?.let {
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_ip), it)
                    }
                    hop.hostname?.let {
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_hostname), it)
                    }
                    hop.rtTimeMs?.let {
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_rtt), "${it}ms")
                    }
                    HopDetailRow(
                        label = stringResource(R.string.traceroute_hop_detail_status),
                        value = when (hop.status) {
                            HopStatus.SUCCESS -> stringResource(R.string.traceroute_hop_detail_status_success)
                            HopStatus.TIMEOUT -> stringResource(R.string.traceroute_hop_detail_status_timeout)
                            HopStatus.ERROR   -> stringResource(R.string.traceroute_hop_detail_status_error)
                        }
                    )
                    hop.geoLocation?.let { geo ->
                        val location = buildString {
                            if (geo.city.isNotBlank()) append("${geo.city}, ")
                            append(geo.country)
                        }
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_location), location)
                        geo.isp?.let { HopDetailRow(stringResource(R.string.traceroute_hop_detail_isp), it) }
                        geo.asn?.let { HopDetailRow(stringResource(R.string.traceroute_hop_detail_asn), it) }
                        HopDetailRow(
                            label = stringResource(R.string.traceroute_hop_detail_coordinates),
                            value = "%.4f, %.4f".format(geo.lat, geo.lon)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HopDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
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
    var scaleTarget by remember { mutableStateOf(0.85f) }
    LaunchedEffect(Unit) { scaleTarget = 1f }
    val scale by animateFloatAsState(
        targetValue   = scaleTarget,
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
