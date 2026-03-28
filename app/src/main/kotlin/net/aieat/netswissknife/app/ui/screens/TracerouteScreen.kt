package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
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
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteUiState
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewModel
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewMode
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

    // animateFloatAsState instead of AnimatedVisibility: both AnimatedVisibility and the
    // inner AnimatedContent use SubcomposeLayout.  Two simultaneously-animating nested
    // SubcomposeLayouts throw IllegalStateException (e.g. when a running trace completes
    // during the 400 ms entrance animation on navigation return).
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label         = "screen-alpha"
    )

    LazyColumn(
        modifier          = Modifier.fillMaxSize().alpha(screenAlpha),
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
                            TracerouteFinishedPanel(
                                state        = state,
                                onToggleMode = viewModel::onToggleViewMode,
                                onClear      = viewModel::onClear
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
    onClear: () -> Unit
) {
    val result = state.result
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Summary stats card
        TraceStatsSummary(result = result, onClear = onClear)

        // Journey intelligence statistics card
        TraceJourneyStats(result = result)

        // Tab row: Hop Details / Raw Output
        // TabRow uses SubcomposeLayout internally, which causes IllegalStateException when
        // nested inside the outer AnimatedContent (also SubcomposeLayout) during the
        // Running→Finished transition – same root cause as the Crossfade issue above.
        // Replaced with a plain Row of clickable surfaces; no SubcomposeLayout involved.
        val tabIndex = if (state.viewMode == TracerouteViewMode.Visual) 0 else 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            listOf(
                Icons.Default.Router      to stringResource(R.string.traceroute_tab_hops),
                Icons.Default.TextSnippet to stringResource(R.string.traceroute_tab_raw)
            ).forEachIndexed { index, (icon, label) ->
                val selected = tabIndex == index
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { if (tabIndex != index) onToggleMode() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Crossfade (= AnimatedContent internally, uses SubcomposeLayout) must NOT be
        // nested inside the outer AnimatedContent – when both animations run simultaneously
        // (e.g. outer Running→Finished transition still in flight while the user taps a tab)
        // the two SubcomposeLayout instances fight over the slot table and the outgoing
        // subcomposition reads a disposed State<T>, causing IllegalStateException.
        // Plain when-branch with no animation is safe and avoids the nesting entirely.
        when (state.viewMode) {
            TracerouteViewMode.Visual -> HopDetailList(result.hops)
            TracerouteViewMode.Raw    -> RawOutputCard(result.rawOutput)
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

// ── Journey statistics card ────────────────────────────────────────────────────

@Composable
private fun TraceJourneyStats(result: TracerouteResult) {
    val geoHops = result.geoLocatedHops
    val successHops = result.hops.filter { it.status == HopStatus.SUCCESS && it.rtTimeMs != null }
    val rtts = successHops.mapNotNull { it.rtTimeMs }

    // ── Geo stats ────────────────────────────────────────────────────────────
    val totalDistanceKm: Double = remember(geoHops) {
        if (geoHops.size < 2) 0.0
        else geoHops.zipWithNext().sumOf { (a, b) ->
            haversineKm(
                a.geoLocation!!.lat, a.geoLocation!!.lon,
                b.geoLocation!!.lat, b.geoLocation!!.lon
            )
        }
    }
    val countries = remember(geoHops) { geoHops.map { it.geoLocation!!.country }.distinct() }
    val isps      = remember(geoHops) { geoHops.mapNotNull { it.geoLocation!!.isp }.distinct() }

    // ── RTT stats ────────────────────────────────────────────────────────────
    val avgRtt    = if (rtts.isNotEmpty()) rtts.average() else null
    val minRttHop = successHops.minByOrNull { it.rtTimeMs!! }
    val maxRttHop = successHops.maxByOrNull { it.rtTimeMs!! }
    val timeoutCount = result.hops.count { it.status == HopStatus.TIMEOUT }
    val timeoutPct   = if (result.hops.isNotEmpty()) timeoutCount * 100 / result.hops.size else 0

    // ── Packet speed vs. speed of light in fibre (~200 km/ms) ───────────────
    val packetSpeedPct: Int? = if (totalDistanceKm > 0 && avgRtt != null && avgRtt > 0) {
        val oneWayMs  = avgRtt / 2.0
        val speedKmMs = totalDistanceKm / oneWayMs
        ((speedKmMs / 200.0) * 100.0).roundToInt().coerceIn(1, 100)
    } else null

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val cardAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label         = "journey-stats-alpha"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth().alpha(cardAlpha)) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.traceroute_stats_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // ── Geographic section ───────────────────────────────────────────
            HorizontalDivider()
            Text(
                stringResource(R.string.traceroute_stats_geo_section),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight  = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            if (geoHops.isNotEmpty()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (totalDistanceKm > 0) {
                        JourneyStatBox(
                            icon     = Icons.Default.TravelExplore,
                            label    = stringResource(R.string.traceroute_stats_distance),
                            value    = formatDistance(totalDistanceKm),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    JourneyStatBox(
                        icon     = Icons.Default.Public,
                        label    = stringResource(R.string.traceroute_stats_countries),
                        value    = "${countries.size}",
                        modifier = Modifier.weight(1f)
                    )
                    if (isps.isNotEmpty()) {
                        JourneyStatBox(
                            icon     = Icons.Default.Router,
                            label    = stringResource(R.string.traceroute_stats_isps),
                            value    = "${isps.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (packetSpeedPct != null) {
                        JourneyStatBox(
                            icon     = Icons.Default.Bolt,
                            label    = stringResource(R.string.traceroute_stats_packet_speed),
                            value    = stringResource(R.string.traceroute_stats_packet_speed_value, packetSpeedPct),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (countries.size > 1) {
                    Text(
                        stringResource(R.string.traceroute_stats_country_path, countries.joinToString(" → ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.FmdGood,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.traceroute_stats_no_geo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── RTT section ──────────────────────────────────────────────────
            if (avgRtt != null) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.traceroute_stats_rtt_section),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JourneyStatBox(
                        icon     = Icons.Default.Timer,
                        label    = stringResource(R.string.traceroute_stats_avg_rtt),
                        value    = "%.1f ms".format(avgRtt),
                        modifier = Modifier.weight(1f)
                    )
                    minRttHop?.let { hop ->
                        JourneyStatBox(
                            icon     = Icons.Default.Bolt,
                            label    = stringResource(R.string.traceroute_stats_min_rtt),
                            value    = "${hop.rtTimeMs} ms",
                            subValue = stringResource(R.string.traceroute_stats_hop_label, hop.hopNumber),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    maxRttHop?.let { hop ->
                        JourneyStatBox(
                            icon     = Icons.Default.Warning,
                            label    = stringResource(R.string.traceroute_stats_max_rtt),
                            value    = "${hop.rtTimeMs} ms",
                            subValue = stringResource(R.string.traceroute_stats_hop_label, hop.hopNumber),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (timeoutCount > 0) {
                        JourneyStatBox(
                            icon     = Icons.Default.Warning,
                            label    = stringResource(R.string.traceroute_stats_timeout_rate),
                            value    = "$timeoutPct%",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Fun-fact footer ───────────────────────────────────────────────
            if (packetSpeedPct != null) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.traceroute_stats_light_speed_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JourneyStatBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subValue: String? = null,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
            Text(
                text      = value,
                style     = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color     = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
            if (subValue != null) {
                Text(
                    text      = subValue,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
        }
    }
}

/** Haversine great-circle distance between two (lat, lon) pairs, in kilometres. */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R    = 6371.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLam = Math.toRadians(lon2 - lon1)
    val sinDPhi = sin(dPhi / 2)
    val sinDLam = sin(dLam / 2)
    val a = sinDPhi * sinDPhi + cos(phi1) * cos(phi2) * sinDLam * sinDLam
    return R * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

private fun formatDistance(km: Double): String = when {
    km >= 10_000 -> "${(km / 1_000).roundToInt()}k km"
    km >= 1_000  -> "${"%.1f".format(km / 1_000)}k km"
    else         -> "${km.roundToInt()} km"
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
    // animateFloatAsState + animateContentSize replace AnimatedVisibility(expandVertically/shrinkVertically).
    // AnimatedVisibility with size enter/exit specs uses SubcomposeLayout internally; nested inside
    // AnimatedContent (and Crossfade which is itself AnimatedContent) it causes IllegalStateException
    // when the outer animation exits while the inner expand/collapse animation is in progress.
    // animateContentSize is a plain Modifier that never uses SubcomposeLayout.
    val expandedAlpha by animateFloatAsState(
        targetValue   = if (expanded) 1f else 0f,
        animationSpec = tween(200),
        label         = "expanded-alpha"
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.animateContentSize(animationSpec = tween(200))) {
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

            // Expanded detail section – rendered while alpha > 0 so the fade-out
            // plays fully; animateContentSize on the parent Column handles height.
            if (expanded || expandedAlpha > 0f) {
                Column(
                    modifier            = Modifier
                        .alpha(expandedAlpha)
                        .padding(start = 60.dp, end = 12.dp, bottom = 12.dp),
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
