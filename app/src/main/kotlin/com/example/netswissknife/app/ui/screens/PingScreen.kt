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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.netswissknife.app.ui.screens.ping.PingUiState
import com.example.netswissknife.app.ui.screens.ping.PingViewModel
import com.example.netswissknife.core.network.ping.PingPacketResult
import com.example.netswissknife.core.network.ping.PingResult
import com.example.netswissknife.core.network.ping.PingStats
import com.example.netswissknife.core.network.ping.PingStatus

@Composable
fun PingScreen(
    viewModel: PingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val packetSize by viewModel.packetSize.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label         = "screen-alpha"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero header ─────────────────────────────────────────────────
            item {
                PingHeroHeader()
            }

            // ── Input card ──────────────────────────────────────────────────
            item {
                PingInputCard(
                    host = host,
                    count = count,
                    timeoutMs = timeoutMs,
                    packetSize = packetSize,
                    isRunning = uiState is PingUiState.Running,
                    onHostChange = viewModel::onHostChange,
                    onCountChange = viewModel::onCountChange,
                    onTimeoutChange = viewModel::onTimeoutChange,
                    onPacketSizeChange = viewModel::onPacketSizeChange,
                    onStart = viewModel::startPing,
                    onStop = viewModel::onStop
                )
            }

            // ── Results area ─────────────────────────────────────────────────
            item {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 })
                            .togetherWith(fadeOut(tween(200)))
                    },
                    contentKey = { it::class },
                    label = "ping_state"
                ) { state ->
                    when (state) {
                        is PingUiState.Idle -> PingIdlePanel()
                        is PingUiState.Running -> PingRunningPanel(state)
                        is PingUiState.Finished -> PingFinishedPanel(
                            state = state,
                            onToggleRaw = viewModel::onToggleRawView,
                            onClear = viewModel::onClearResults
                        )
                        is PingUiState.Error -> PingErrorPanel(
                            message = state.message,
                            onRetry = viewModel::onRetry,
                            onClear = viewModel::onClearResults
                        )
                    }
                }
            }
        }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun PingHeroHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "ping_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hero_pulse"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(pulse)
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
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.ping_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.ping_screen_subtitle),
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
private fun PingInputCard(
    host: String,
    count: Int,
    timeoutMs: Int,
    packetSize: Int,
    isRunning: Boolean,
    onHostChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onPacketSizeChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Host input
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.ping_host_label)) },
                placeholder = { Text(stringResource(R.string.ping_host_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                },
                trailingIcon = {
                    if (host.isNotEmpty()) {
                        IconButton(onClick = { onHostChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboardController?.hide()
                    if (!isRunning) onStart()
                }),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            )

            // Count slider
            PingSliderRow(
                label = "${stringResource(R.string.ping_count_label)}: $count",
                value = count.toFloat(),
                valueRange = 1f..50f,
                steps = 48,
                onValueChange = { onCountChange(it.toInt()) },
                enabled = !isRunning
            )

            // Timeout chips
            PingTimeoutRow(
                timeoutMs = timeoutMs,
                onTimeoutChange = onTimeoutChange,
                enabled = !isRunning
            )

            // Packet size chips
            PingPacketSizeRow(
                packetSize = packetSize,
                onPacketSizeChange = onPacketSizeChange,
                enabled = !isRunning
            )

            // Action button
            if (isRunning) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ping_stop_button))
                }
            } else {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = host.isNotBlank()
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ping_start_button))
                }
            }
        }
    }
}

@Composable
private fun PingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PingTimeoutRow(timeoutMs: Int, onTimeoutChange: (Int) -> Unit, enabled: Boolean) {
    val options = listOf(1_000 to "1s", 2_000 to "2s", 3_000 to "3s", 5_000 to "5s")
    Column {
        Text(
            text = stringResource(R.string.ping_timeout_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (ms, label) ->
                FilterChip(
                    selected = timeoutMs == ms,
                    onClick = { if (enabled) onTimeoutChange(ms) },
                    label = { Text(label) },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun PingPacketSizeRow(packetSize: Int, onPacketSizeChange: (Int) -> Unit, enabled: Boolean) {
    val options = listOf(32 to "32 B", 56 to "56 B", 128 to "128 B", 512 to "512 B")
    Column {
        Text(
            text = stringResource(R.string.ping_packet_size_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (size, label) ->
                FilterChip(
                    selected = packetSize == size,
                    onClick = { if (enabled) onPacketSizeChange(size) },
                    label = { Text(label) },
                    enabled = enabled
                )
            }
        }
    }
}

// ── Idle panel ────────────────────────────────────────────────────────────────

@Composable
private fun PingIdlePanel() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NetworkCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.ping_idle_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.ping_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Running panel ─────────────────────────────────────────────────────────────

@Composable
private fun PingRunningPanel(state: PingUiState.Running) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Progress card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ping_running_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.ping_progress_format, state.packets.size, state.totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { state.packets.size.toFloat() / state.totalCount.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = state.host,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live RTT chart
        if (state.packets.any { it.rtTimeMs != null }) {
            RttChartCard(packets = state.packets)
        }

        // Live packet list
        if (state.packets.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ping_result_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    state.packets.forEach { packet ->
                        PacketRow(packet)
                    }
                }
            }
        }
    }
}

// ── Finished panel ────────────────────────────────────────────────────────────

@Composable
private fun PingFinishedPanel(
    state: PingUiState.Finished,
    onToggleRaw: () -> Unit,
    onClear: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val result = state.result

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Stats card
        StatsCard(stats = result.stats, host = result.host)

        // RTT chart
        if (result.packets.any { it.rtTimeMs != null }) {
            RttChartCard(packets = result.packets)
        }

        // Packet list card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ping_result_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(buildCsvOutput(result)))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ping_copy_csv))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                result.packets.forEach { packet ->
                    PacketRow(packet)
                    if (packet != result.packets.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        // Raw output toggle
        RawOutputCard(
            rawOutput = result.rawOutput,
            showRaw = state.showRaw,
            onToggle = onToggleRaw
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.clear))
            }
        }
    }
}

// ── Error panel ───────────────────────────────────────────────────────────────

@Composable
private fun PingErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ping_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ping_retry))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.clear))
                }
            }
        }
    }
}

// ── Stats card ────────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(stats: PingStats, host: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ping_stats_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Packet counts row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.ping_packets_sent),
                    value = "${stats.sent}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                StatItem(
                    label = stringResource(R.string.ping_packets_received),
                    value = "${stats.received}",
                    color = if (stats.received == stats.sent)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
                StatItem(
                    label = stringResource(R.string.ping_packet_loss),
                    value = "${"%.0f".format(stats.lossPercent)}%",
                    color = if (stats.lossPercent == 0f)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
            if (stats.received > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                // RTT stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = stringResource(R.string.ping_rtt_min),
                        value = "${stats.minMs} ${stringResource(R.string.ping_ms_suffix)}",
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    StatItem(
                        label = stringResource(R.string.ping_rtt_avg),
                        value = "${"%.1f".format(stats.avgMs)} ${stringResource(R.string.ping_ms_suffix)}",
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    StatItem(
                        label = stringResource(R.string.ping_rtt_max),
                        value = "${stats.maxMs} ${stringResource(R.string.ping_ms_suffix)}",
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    StatItem(
                        label = stringResource(R.string.ping_rtt_jitter),
                        value = "${"%.1f".format(stats.jitterMs)} ${stringResource(R.string.ping_ms_suffix)}",
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

// ── RTT chart ─────────────────────────────────────────────────────────────────

@Composable
private fun RttChartCard(packets: List<PingPacketResult>) {
    val successPackets = packets.filter { it.rtTimeMs != null }
    if (successPackets.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ping_chart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val maxRtt = successPackets.mapNotNull { it.rtTimeMs }.maxOrNull() ?: 1L
                val chartWidth = size.width
                val chartHeight = size.height
                val padding = 16f

                // Background grid
                val gridLines = 4
                repeat(gridLines) { i ->
                    val y = padding + (chartHeight - 2 * padding) * i / (gridLines - 1)
                    drawLine(
                        color = surfaceColor,
                        start = Offset(padding, y),
                        end = Offset(chartWidth - padding, y),
                        strokeWidth = 1f
                    )
                }

                // Build path of RTT values
                val points = mutableListOf<Offset>()
                packets.forEachIndexed { index, packet ->
                    val x = padding + (chartWidth - 2 * padding) * index / (packets.size - 1).coerceAtLeast(1)
                    val rtt = packet.rtTimeMs
                    if (rtt != null) {
                        val y = chartHeight - padding - (rtt.toFloat() / maxRtt) * (chartHeight - 2 * padding)
                        points.add(Offset(x, y))
                    }
                }

                if (points.size >= 2) {
                    // Fill gradient beneath curve
                    val fillPath = Path().apply {
                        moveTo(points.first().x, chartHeight - padding)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, chartHeight - padding)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f, endY = chartHeight
                        )
                    )
                    // Line
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                // Dots per packet
                packets.forEachIndexed { index, packet ->
                    val x = padding + (chartWidth - 2 * padding) * index / (packets.size - 1).coerceAtLeast(1)
                    val rtt = packet.rtTimeMs
                    val dotColor = when {
                        rtt != null -> successColor
                        else -> errorColor
                    }
                    val y = if (rtt != null)
                        chartHeight - padding - (rtt.toFloat() / maxRtt) * (chartHeight - 2 * padding)
                    else
                        chartHeight - padding

                    drawCircle(color = dotColor, radius = 5f, center = Offset(x, y))
                    drawCircle(
                        color = dotColor.copy(alpha = 0.3f),
                        radius = 9f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

// ── Packet row ────────────────────────────────────────────────────────────────

@Composable
private fun PacketRow(packet: PingPacketResult) {
    var targetAlpha by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { targetAlpha = 1f }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "packet_alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sequence badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (packet.status) {
                        PingStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        PingStatus.TIMEOUT -> MaterialTheme.colorScheme.surfaceVariant
                        PingStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${packet.sequence}",
                style = MaterialTheme.typography.labelSmall,
                color = when (packet.status) {
                    PingStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                    PingStatus.TIMEOUT -> MaterialTheme.colorScheme.onSurfaceVariant
                    PingStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                },
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Status / RTT
        when (packet.status) {
            PingStatus.SUCCESS -> {
                Text(
                    text = "${packet.rtTimeMs} ${stringResource(R.string.ping_ms_suffix)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // Simple bar proportional to RTT (relative to 200ms max)
                val rtRatio = ((packet.rtTimeMs ?: 0L).toFloat() / 200f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rtRatio.coerceAtLeast(0.04f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    )
                }
            }
            PingStatus.TIMEOUT -> {
                Text(
                    text = stringResource(R.string.ping_timeout_label_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
            }
            PingStatus.ERROR -> {
                Text(
                    text = packet.errorMessage ?: stringResource(R.string.ping_error_label_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Raw output card ───────────────────────────────────────────────────────────

@Composable
private fun RawOutputCard(
    rawOutput: String,
    showRaw: Boolean,
    onToggle: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ping_raw_output),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    if (showRaw) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(rawOutput))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ping_copy_raw))
                        }
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showRaw) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = rawOutput,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildCsvOutput(result: PingResult): String = buildString {
    appendLine("sequence,host,status,rtt_ms,error")
    result.packets.forEach { p ->
        appendLine("${p.sequence},${p.host},${p.status},${p.rtTimeMs ?: ""},${p.errorMessage ?: ""}")
    }
    appendLine()
    appendLine("# Stats")
    appendLine("sent,received,loss_percent,min_ms,avg_ms,max_ms,jitter_ms")
    appendLine("${result.stats.sent},${result.stats.received},${"%.1f".format(result.stats.lossPercent)}," +
            "${result.stats.minMs},${"%.3f".format(result.stats.avgMs)},${result.stats.maxMs}," +
            "${"%.3f".format(result.stats.jitterMs)}")
}
