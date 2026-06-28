package net.aieat.netswissknife.app.ui.screens.speedtest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.util.formatBytes
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.speedtest.LatencyStats
import net.aieat.netswissknife.core.network.speedtest.SpeedTestPhase
import net.aieat.netswissknife.core.network.speedtest.SpeedTestResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputResult
import net.aieat.netswissknife.core.network.speedtest.ThroughputSample
import kotlin.math.cos
import kotlin.math.sin

private const val CLOUDFLARE_SPEED_URL = "https://speed.cloudflare.com"
private const val GAUGE_MAX_MBPS = 1_000.0

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val phase = remember(uiState) {
        when (uiState) {
            is SpeedTestUiState.Idle -> DisplayPhase.IDLE
            is SpeedTestUiState.Running -> DisplayPhase.RUNNING
            is SpeedTestUiState.Finished -> DisplayPhase.FINISHED
            is SpeedTestUiState.Error -> DisplayPhase.ERROR
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            item { SpeedTestHeaderCard(onHelpClick = { showHelp = true }) }

            item {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "speedtest_content_state"
                ) { target ->
                    when (target) {
                        DisplayPhase.IDLE ->
                            SpeedTestIdleContent(onStart = viewModel::startTest)
                        DisplayPhase.RUNNING ->
                            (uiState as? SpeedTestUiState.Running)?.let {
                                SpeedTestRunningContent(it, onCancel = viewModel::onCancel)
                            }
                        DisplayPhase.FINISHED ->
                            (uiState as? SpeedTestUiState.Finished)?.let { finished ->
                                SpeedTestResultContent(
                                    result = finished.result,
                                    onRetry = viewModel::onRetry,
                                    onShare = {
                                        context.shareText(
                                            text = buildSpeedTestShareText(finished.result),
                                            subject = context.getString(R.string.share_subject_speedtest, "results")
                                        )
                                    }
                                )
                            }
                        DisplayPhase.ERROR ->
                            (uiState as? SpeedTestUiState.Error)?.let {
                                SpeedTestErrorContent(it, onRetry = viewModel::onRetry)
                            }
                    }
                }
            }

            item { CloudflareAttributionCard() }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_speedtest_title),
            sections = listOf(
                HelpSection(stringResource(R.string.help_speedtest_what_heading), stringResource(R.string.help_speedtest_what_body)),
                HelpSection(stringResource(R.string.help_speedtest_phases_heading), stringResource(R.string.help_speedtest_phases_body)),
                HelpSection(stringResource(R.string.help_speedtest_results_heading), stringResource(R.string.help_speedtest_results_body))
            ),
            onDismiss = { showHelp = false }
        )
    }
}

private enum class DisplayPhase { IDLE, RUNNING, FINISHED, ERROR }

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedTestHeaderCard(onHelpClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.speedtest_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.speedtest_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_help),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { uriHandler.openUri(CLOUDFLARE_SPEED_URL) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.speedtest_attribution),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Idle state ────────────────────────────────────────────────────────────────

@Composable
private fun SpeedTestIdleContent(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "speedtest_idle_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "speedtest_idle_pulse_scale"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulse)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.speedtest_start_button),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.speedtest_idle_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.speedtest_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.speedtest_start_button))
            }
        }
    }
}

// ── Running state ─────────────────────────────────────────────────────────────

@Composable
private fun SpeedTestRunningContent(state: SpeedTestUiState.Running, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                PhaseStepper(currentPhase = state.phase)
                Spacer(Modifier.height(16.dp))

                val phaseTitle = when (state.phase) {
                    SpeedTestPhase.LATENCY -> R.string.speedtest_running_latency
                    SpeedTestPhase.DOWNLOAD -> R.string.speedtest_running_download
                    SpeedTestPhase.UPLOAD -> R.string.speedtest_running_upload
                }
                Text(
                    text = stringResource(phaseTitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))

                when (state.phase) {
                    SpeedTestPhase.LATENCY -> LatencyLiveStats(state.latencyStats)
                    SpeedTestPhase.DOWNLOAD -> ThroughputLiveView(
                        samples = state.downloadSamples,
                        accentColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.ArrowDownward
                    )
                    SpeedTestPhase.UPLOAD -> ThroughputLiveView(
                        samples = state.uploadSamples,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Default.ArrowUpward
                    )
                }
            }
        }

        if (state.phase != SpeedTestPhase.LATENCY) {
            ResultStrip(downloadResult = state.downloadResult)
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.speedtest_cancel_button))
        }
    }
}

@Composable
private fun PhaseStepper(currentPhase: SpeedTestPhase) {
    val phases = listOf(
        SpeedTestPhase.LATENCY to R.string.speedtest_phase_latency,
        SpeedTestPhase.DOWNLOAD to R.string.speedtest_phase_download,
        SpeedTestPhase.UPLOAD to R.string.speedtest_phase_upload
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        phases.forEach { (phase, labelRes) ->
            val isActive = phase == currentPhase
            val isDone = phase.ordinal < currentPhase.ordinal
            val containerColor by animateFloatAsState(
                targetValue = if (isActive || isDone) 1f else 0f,
                animationSpec = tween(300),
                label = "phase_chip_${phase.name}"
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    containerColor
                )
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimary,
                        containerColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LatencyLiveStats(stats: LatencyStats) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val infiniteTransition = rememberInfiniteTransition(label = "latency_pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.9f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "latency_pulse_scale"
        )
        Icon(
            imageVector = Icons.Default.NetworkCheck,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .scale(pulse)
        )
        Spacer(Modifier.height(12.dp))
        if (stats.samples.isNotEmpty()) {
            StatGrid(latencyStatGridEntries(stats))
        }
    }
}

@Composable
private fun latencyStatGridEntries(stats: LatencyStats): List<Pair<String, String>> {
    val msSuffix = stringResource(R.string.speedtest_ms_suffix)
    return listOf(
        stringResource(R.string.speedtest_latency_min) to "${stats.minMs} $msSuffix",
        stringResource(R.string.speedtest_latency_avg) to "%.1f %s".format(stats.avgMs, msSuffix),
        stringResource(R.string.speedtest_latency_max) to "${stats.maxMs} $msSuffix",
        stringResource(R.string.speedtest_latency_jitter) to "%.1f %s".format(stats.jitterMs, msSuffix)
    )
}

@Composable
private fun ThroughputLiveView(
    samples: List<ThroughputSample>,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val current = samples.lastOrNull()?.instantMbps ?: 0.0
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SpeedGauge(valueMbps = current, accentColor = accentColor, icon = icon)
        Spacer(Modifier.height(16.dp))
        ThroughputChart(samples = samples, accentColor = accentColor)
    }
}

// ── Speed gauge ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedGauge(
    valueMbps: Double,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val animatedValue by animateFloatAsState(
        targetValue = valueMbps.toFloat(),
        animationSpec = tween(500),
        label = "speed_gauge_value"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val strokeWidth = with(density) { 14.dp.toPx() }

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweepBackground = 270f
            val startAngle = 135f
            val inset = strokeWidth / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepBackground,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val progress = (animatedValue / GAUGE_MAX_MBPS.toFloat()).coerceIn(0f, 1f)
            drawArc(
                brush = Brush.sweepGradient(listOf(accentColor.copy(alpha = 0.4f), accentColor)),
                startAngle = startAngle,
                sweepAngle = sweepBackground * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "%.1f".format(animatedValue),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.speedtest_mbps_suffix),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Throughput chart ──────────────────────────────────────────────────────────

@Composable
private fun ThroughputChart(samples: List<ThroughputSample>, accentColor: Color) {
    if (samples.size < 2) return

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = labelColor)
    val density = LocalDensity.current
    val leftPad = with(density) { 38.dp.toPx() }
    val edgePad = with(density) { 8.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val maxMbps = (samples.maxOfOrNull { it.instantMbps } ?: 1.0).coerceAtLeast(1.0)

        val gridLines = 4
        repeat(gridLines) { i ->
            val y = edgePad + (chartHeight - 2 * edgePad) * i / (gridLines - 1)
            drawLine(
                color = surfaceColor,
                start = Offset(leftPad, y),
                end = Offset(chartWidth - edgePad, y),
                strokeWidth = 1f
            )
            val valueAtLine = maxMbps * (gridLines - 1 - i) / (gridLines - 1)
            val measured = textMeasurer.measure("%.0f".format(valueAtLine), style = labelStyle)
            drawText(textLayoutResult = measured, topLeft = Offset(0f, y - measured.size.height / 2f))
        }

        val points = samples.mapIndexed { index, sample ->
            val x = leftPad + (chartWidth - leftPad - edgePad) * index / (samples.size - 1).coerceAtLeast(1)
            val y = chartHeight - edgePad - (sample.instantMbps / maxMbps).toFloat() * (chartHeight - 2 * edgePad)
            Offset(x, y)
        }

        val fillPath = Path().apply {
            moveTo(points.first().x, chartHeight - edgePad)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, chartHeight - edgePad)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f, endY = chartHeight
            )
        )
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path = linePath, color = accentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

// ── Result strip (mini summary while running) ────────────────────────────────

@Composable
private fun ResultStrip(downloadResult: ThroughputResult?) {
    if (downloadResult == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniResultCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.speedtest_download_header),
            icon = Icons.Default.ArrowDownward,
            accentColor = MaterialTheme.colorScheme.primary,
            valueMbps = downloadResult.avgMbps
        )
    }
}

@Composable
private fun MiniResultCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    valueMbps: Double
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "%.1f %s".format(valueMbps, stringResource(R.string.speedtest_mbps_suffix)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

// ── Finished state ────────────────────────────────────────────────────────────

@Composable
private fun SpeedTestResultContent(
    result: SpeedTestResult,
    onRetry: () -> Unit,
    onShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.speedtest_results_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        LatencyResultCard(result.latency)
        ThroughputResultCard(
            title = stringResource(R.string.speedtest_download_header),
            icon = Icons.Default.ArrowDownward,
            accentColor = MaterialTheme.colorScheme.primary,
            result = result.download
        )
        ThroughputResultCard(
            title = stringResource(R.string.speedtest_upload_header),
            icon = Icons.Default.ArrowUpward,
            accentColor = MaterialTheme.colorScheme.tertiary,
            result = result.upload
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.speedtest_share))
            }
            Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.speedtest_retry))
            }
        }
    }
}

@Composable
private fun LatencyResultCard(stats: LatencyStats) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.speedtest_latency_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            StatGrid(latencyStatGridEntries(stats))
        }
    }
}

@Composable
private fun ThroughputResultCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    result: ThroughputResult
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor)
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            StatGrid(
                listOf(
                    stringResource(R.string.speedtest_avg_speed) to "%.1f %s".format(result.avgMbps, stringResource(R.string.speedtest_mbps_suffix)),
                    stringResource(R.string.speedtest_peak_speed) to "%.1f %s".format(result.peakMbps, stringResource(R.string.speedtest_mbps_suffix)),
                    stringResource(R.string.speedtest_data_transferred) to formatBytes(result.bytesTransferred)
                )
            )
            if (result.samples.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.speedtest_chart_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ThroughputChart(samples = result.samples, accentColor = accentColor)
            }
        }
    }
}

// ── Error state ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedTestErrorContent(state: SpeedTestUiState.Error, onRetry: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.speedtest_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.speedtest_retry))
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun StatGrid(entries: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        entries.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CloudflareAttributionCard() {
    val uriHandler = LocalUriHandler.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.speedtest_attribution_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(CLOUDFLARE_SPEED_URL) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = CLOUDFLARE_SPEED_URL,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun buildSpeedTestShareText(result: SpeedTestResult): String = buildString {
    appendLine("Net Swiss Knife – Speed Test Results")
    appendLine()
    appendLine("Latency: min ${result.latency.minMs} ms / avg %.1f ms / max ${result.latency.maxMs} ms / jitter %.1f ms"
        .format(result.latency.avgMs, result.latency.jitterMs))
    appendLine("Download: avg %.1f Mbps / peak %.1f Mbps / %s".format(result.download.avgMbps, result.download.peakMbps, formatBytes(result.download.bytesTransferred)))
    appendLine("Upload: avg %.1f Mbps / peak %.1f Mbps / %s".format(result.upload.avgMbps, result.upload.peakMbps, formatBytes(result.upload.bytesTransferred)))
    appendLine()
    appendLine("Measured against $CLOUDFLARE_SPEED_URL")
}
