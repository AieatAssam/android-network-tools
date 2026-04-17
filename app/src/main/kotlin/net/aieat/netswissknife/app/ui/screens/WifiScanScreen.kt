package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand

@Composable
fun WifiScanScreen(
    viewModel: WifiScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val autoRefresh by viewModel.autoRefresh.collectAsState()

    // ── Permission launcher ───────────────────────────────────────────────────
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.onPermissionGranted()
        else viewModel.onPermissionDenied()
    }

    // Request permissions on first launch; restart auto-refresh when returning to the screen.
    LaunchedEffect(Unit) {
        if (uiState is WifiScanUiState.Idle) {
            permissionLauncher.launch(requiredPermissions)
        } else if (uiState !is WifiScanUiState.NoPermission && uiState !is WifiScanUiState.NotSupported) {
            // Returning to the screen after navigating away – resume the refresh cycle.
            viewModel.startAutoRefresh()
        }
    }

    // Stop auto-refresh whenever this composable leaves the composition (navigation away).
    DisposableEffect(Unit) {
        onDispose { viewModel.stopAutoRefresh() }
    }

    // ── Root animated state switcher ──────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label         = "screen-alpha"
    )

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        modifier = Modifier.fillMaxSize().alpha(screenAlpha),
            label = "wifi_state"
        ) { state ->
            when (state) {
                is WifiScanUiState.Idle         -> WifiIdleScreen()
                is WifiScanUiState.NoPermission -> WifiNoPermissionScreen(
                    onRequest = { permissionLauncher.launch(requiredPermissions) }
                )
                is WifiScanUiState.NotSupported -> WifiNotSupportedScreen()
                is WifiScanUiState.WifiDisabled -> WifiDisabledScreen(
                    onRetry = { viewModel.startScan() }
                )
                is WifiScanUiState.Scanning     -> WifiScanningScreen()
                is WifiScanUiState.Success      -> WifiSuccessScreen(
                    state      = state,
                    autoRefresh = autoRefresh,
                    onScan     = { viewModel.startScan() },
                    onToggleAutoRefresh = { viewModel.toggleAutoRefresh() },
                    onBandFilter = { viewModel.setBandFilter(it) },
                    onSortOrder  = { viewModel.setSortOrder(it) },
                    onSelectAp   = { viewModel.selectAccessPoint(it) }
                )
                is WifiScanUiState.Error        -> WifiErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.onRetry(); permissionLauncher.launch(requiredPermissions) }
                )
            }
        }
}

// ── Idle / loading ────────────────────────────────────────────────────────────

@Composable private fun WifiIdleScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ── Empty / error states ──────────────────────────────────────────────────────

@Composable private fun WifiStatusCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(body,  style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (action != null) { Spacer(Modifier.height(4.dp)); action() }
            }
        }
    }
}

@Composable private fun WifiNoPermissionScreen(onRequest: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.LocationOff, null, Modifier.size(48.dp),
                       tint = MaterialTheme.colorScheme.error) },
        title  = "Location Permission Required",
        body   = "Android requires Location permission to scan for nearby Wi-Fi networks. " +
                 "Your location is not sent anywhere.",
        action = { Button(onRequest) { Text("Grant Permission") } }
    )
}

@Composable private fun WifiNotSupportedScreen() {
    WifiStatusCard(
        icon  = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = "Wi-Fi Not Available",
        body  = "This device does not have Wi-Fi hardware."
    )
}

@Composable private fun WifiDisabledScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.SignalWifiOff, null, Modifier.size(48.dp),
                       tint = MaterialTheme.colorScheme.tertiary) },
        title  = "Wi-Fi is Off",
        body   = "Enable Wi-Fi in your device settings, then scan again.",
        action = { Button(onRetry) { Text("Retry") } }
    )
}

@Composable private fun WifiErrorScreen(message: String, onRetry: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp),
                       tint = MaterialTheme.colorScheme.error) },
        title  = "Scan Failed",
        body   = message,
        action = { Button(onRetry) { Text("Retry") } }
    )
}

// ── Shimmer helper ────────────────────────────────────────────────────────────

@Composable private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant
    )
    Box(modifier.background(
        Brush.linearGradient(colors, start = Offset(shimmerX * 600f, 0f), end = Offset(shimmerX * 600f + 600f, 0f))
    ))
}

@Composable private fun WifiScanningScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gradient header shimmer
        item {
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)))
        }
        // Band filter tabs shimmer
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { ShimmerBox(Modifier.width(80.dp).height(36.dp).clip(RoundedCornerShape(50))) }
            }
        }
        // Channel chart shimmer
        item { ShimmerBox(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))) }
        // AP card shimmers
        items(5) {
            ShimmerBox(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)))
        }
    }
}

// ── Success screen ────────────────────────────────────────────────────────────

@Composable private fun WifiSuccessScreen(
    state: WifiScanUiState.Success,
    autoRefresh: Boolean,
    onScan: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onBandFilter: (WifiBand?) -> Unit,
    onSortOrder: (ApSortOrder) -> Unit,
    onSelectAp: (WifiAccessPoint?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // 1 – Gradient header
        item {
            WifiHeader(
                result      = state.result,
                autoRefresh = autoRefresh,
                onScan      = onScan,
                onToggleAutoRefresh = onToggleAutoRefresh
            )
        }

        // 2 – Band filter + sort chips
        item {
            WifiBandFilterRow(
                detectedBands = state.result.detectedBands,
                selected      = state.bandFilter,
                onSelect      = onBandFilter
            )
        }

        // 3 – Sort order chips
        item { WifiSortRow(current = state.sortOrder, onSelect = onSortOrder) }

        // 4 – Channel chart (placeholder — next step)
        item { WifiChannelChartCard(state = state) }

        // 5 – AP list
        item {
            Text(
                "${state.filteredAccessPoints.size} Networks" +
                    (if (state.bandFilter != null) " (${state.bandFilter.displayName})" else ""),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(state.filteredAccessPoints.size) { i ->
            WifiApCard(ap = state.filteredAccessPoints[i], onClick = { onSelectAp(it) })
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // Detail bottom sheet
    if (state.selectedAp != null) {
        WifiApDetailSheet(ap = state.selectedAp, connectedInfo = state.result.connectedNetwork,
            onDismiss = { onSelectAp(null) })
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable private fun WifiHeader(
    result: net.aieat.netswissknife.core.network.wifi.WifiScanResult,
    autoRefresh: Boolean,
    onScan: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    )
    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(Modifier.background(gradient)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null, Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Text("Wi-Fi Scanner", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onScan) {
                        Icon(Icons.Default.Refresh, "Scan")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val time = remember(result.scanTimestampMs) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.scanTimestampMs))
                }
                Text(
                    "${result.accessPoints.size} networks · ${result.uniqueNetworkCount} SSIDs · $time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (result.detectedBands.isNotEmpty()) {
                    Text(
                        result.detectedBands.joinToString(" · ") { it.displayName },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ── Band filter row ───────────────────────────────────────────────────────────

@Composable private fun WifiBandFilterRow(
    detectedBands: List<WifiBand>,
    selected: WifiBand?,
    onSelect: (WifiBand?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        detectedBands.forEach { band ->
            FilterChip(
                selected = selected == band,
                onClick  = { onSelect(if (selected == band) null else band) },
                label    = { Text(band.ghzLabel + " GHz") }
            )
        }
    }
}

// ── Sort order row ────────────────────────────────────────────────────────────

@Composable private fun WifiSortRow(current: ApSortOrder, onSelect: (ApSortOrder) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sort:", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterVertically),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ApSortOrder.values().forEach { order ->
            FilterChip(
                selected = current == order,
                onClick  = { onSelect(order) },
                label    = { Text(order.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ── Stubs for next steps ──────────────────────────────────────────────────────

// ── Channel chart ─────────────────────────────────────────────────────────────

@Composable private fun WifiChannelChartCard(state: WifiScanUiState.Success) {
    val channels = if (state.bandFilter == null) state.result.channels
                   else state.result.channels.filter { it.band == state.bandFilter }
    if (channels.isEmpty()) return

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Channel Utilisation", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            // Group by band for sub-headers
            val byBand = channels.groupBy { it.band }
            byBand.forEach { (band, chList) ->
                if (byBand.size > 1) {
                    Text(band.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                ChannelBarChart(channels = chList)
            }
        }
    }
}

@Composable private fun ChannelBarChart(
    channels: List<net.aieat.netswissknife.core.network.wifi.WifiChannelInfo>
) {
    val trackColor  = MaterialTheme.colorScheme.surfaceVariant
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f).toArgb()
    val density     = LocalDensity.current

    val barWidth    = 36.dp
    val chartHeight = 120.dp
    val totalWidth  = (barWidth + 8.dp) * channels.size

    // Pre-compute pixel sizes outside Canvas to avoid nested Composable calls
    val bwPx: Float
    val gapPx: Float
    val countTextSizePx: Float
    val labelTextSizePx: Float
    with(density) {
        bwPx            = barWidth.toPx()
        gapPx           = 8.dp.toPx()
        countTextSizePx = 10.sp.toPx()
        labelTextSizePx = 9.sp.toPx()
    }

    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(totalWidth).height(chartHeight)) {
            val maxBarH    = size.height * 0.75f
            val labelAreaH = size.height * 0.25f

            val countPaint = android.graphics.Paint().apply {
                color     = android.graphics.Color.WHITE
                textSize  = countTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias    = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color     = labelColorArgb
                textSize  = labelTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            channels.forEachIndexed { i, ch ->
                val x    = i * (bwPx + gapPx)
                val barH = (ch.congestionScore * maxBarH).coerceAtLeast(4f)
                val barTop = maxBarH - barH

                // Track background
                drawRoundRect(
                    color        = trackColor,
                    topLeft      = Offset(x, 0f),
                    size         = Size(bwPx, maxBarH),
                    cornerRadius = CornerRadius(6f)
                )
                // Filled bar: green → amber → red by congestion score
                val barFill = lerp(Color(0xFF4CAF50), Color(0xFFF44336), ch.congestionScore)
                drawRoundRect(
                    color        = barFill,
                    topLeft      = Offset(x, barTop),
                    size         = Size(bwPx, barH),
                    cornerRadius = CornerRadius(6f)
                )
                // AP count inside bar
                if (ch.accessPointCount > 0) {
                    val countY = (barTop + barH / 2f + countTextSizePx / 3f).coerceAtLeast(countTextSizePx)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${ch.accessPointCount}", x + bwPx / 2f, countY, countPaint
                    )
                }
                // Channel label below bar
                drawContext.canvas.nativeCanvas.drawText(
                    "Ch ${ch.channel}", x + bwPx / 2f, maxBarH + labelAreaH * 0.65f, labelPaint
                )
            }
        }
    }
}

// ── AP Card ───────────────────────────────────────────────────────────────────

@Composable private fun WifiApCard(ap: WifiAccessPoint, onClick: (WifiAccessPoint) -> Unit) {
    val connectedGradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    )
    val cardColors = if (ap.isConnected) {
        CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    } else {
        CardDefaults.elevatedCardColors()
    }

    ElevatedCard(
        onClick   = { onClick(ap) },
        modifier  = Modifier.fillMaxWidth(),
        colors    = cardColors
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal bars icon
            SignalBarsIcon(quality = ap.signalQualityPercent, level = ap.signalLevel)

            Spacer(Modifier.width(12.dp))

            // Network info
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        ap.displaySsid,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    if (ap.isConnected) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (ap.ssid.isBlank()) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("Hidden", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApBadge(ap.band.ghzLabel + " GHz")
                    ApBadge("Ch ${ap.channel}")
                    ApBadge("${ap.channelWidthMhz} MHz")
                    if (ap.standard.generationLabel != "Unknown") ApBadge(ap.standard.generationLabel)
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        if (ap.security.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                        null, Modifier.size(12.dp),
                        tint = if (ap.security.isEncrypted) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.tertiary
                    )
                    Text(ap.security.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ap.vendor.isNotBlank()) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(ap.vendor, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            // RSSI column
            Column(horizontalAlignment = Alignment.End) {
                val rssiColor = signalLevelColor(ap.signalLevel)
                Text("${ap.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = rssiColor)
                Text("${ap.signalQualityPercent}%", style = MaterialTheme.typography.labelSmall,
                    color = rssiColor.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable private fun ApBadge(text: String) {
    Surface(
        color  = MaterialTheme.colorScheme.secondaryContainer,
        shape  = RoundedCornerShape(4.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable private fun SignalBarsIcon(quality: Int, level: net.aieat.netswissknife.core.network.wifi.SignalLevel) {
    val color = signalLevelColor(level)
    val bars  = when {
        quality >= 75 -> 4
        quality >= 50 -> 3
        quality >= 25 -> 2
        else          -> 1
    }
    Row(
        modifier = Modifier.width(24.dp).height(24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val barHeights = listOf(6.dp, 10.dp, 15.dp, 20.dp)
        barHeights.forEachIndexed { i, h ->
            val active = i < bars
            Box(
                Modifier.width(4.dp).height(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (active) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable private fun signalLevelColor(
    level: net.aieat.netswissknife.core.network.wifi.SignalLevel
): Color = when (level) {
    net.aieat.netswissknife.core.network.wifi.SignalLevel.EXCELLENT -> Color(0xFF4CAF50)
    net.aieat.netswissknife.core.network.wifi.SignalLevel.GOOD      -> Color(0xFF8BC34A)
    net.aieat.netswissknife.core.network.wifi.SignalLevel.FAIR      -> Color(0xFFFFC107)
    net.aieat.netswissknife.core.network.wifi.SignalLevel.WEAK      -> Color(0xFFFF9800)
    net.aieat.netswissknife.core.network.wifi.SignalLevel.POOR      -> Color(0xFFF44336)
}

// ── AP Detail Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WifiApDetailSheet(
    ap: WifiAccessPoint,
    connectedInfo: net.aieat.netswissknife.core.network.wifi.WifiConnectionInfo?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Title row ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBarsIcon(ap.signalQualityPercent, ap.signalLevel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ap.displaySsid, style = MaterialTheme.typography.titleMedium)
                    Text(ap.bssid, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${ap.rssi} dBm", style = MaterialTheme.typography.titleMedium,
                        color = signalLevelColor(ap.signalLevel))
                    Text(ap.signalLevel.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = signalLevelColor(ap.signalLevel).copy(alpha = 0.7f))
                }
            }

            if (ap.isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Currently Connected", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // ── Signal quality arc ────────────────────────────────────────────
            SignalArcGauge(quality = ap.signalQualityPercent, level = ap.signalLevel)

            HorizontalDivider()

            // ── Network info section ──────────────────────────────────────────
            DetailSectionHeader("Network Information")
            DetailRow("Band",         ap.band.displayName)
            DetailRow("Channel",      "${ap.channel}  (${ap.frequency} MHz)")
            DetailRow("Width",        "${ap.channelWidthMhz} MHz")
            DetailRow("Standard",     "${ap.standard.generationLabel}  (${ap.standard.protocolName})")
            DetailRow("Max Speed",    ap.standard.maxSpeedLabel)
            if (ap.vendor.isNotBlank()) DetailRow("Vendor", ap.vendor)
            if (ap.centerFrequency0 != 0) DetailRow("Center Freq 0", "${ap.centerFrequency0} MHz")
            if (ap.centerFrequency1 != 0) DetailRow("Center Freq 1", "${ap.centerFrequency1} MHz (80+80)")

            HorizontalDivider()

            // ── Security section ──────────────────────────────────────────────
            DetailSectionHeader("Security")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ap.security.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                    null, Modifier.size(18.dp),
                    tint = if (ap.security.isEncrypted) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary
                )
                Text(ap.security.displayName, style = MaterialTheme.typography.bodyMedium)
            }
            // Capability tokens
            val tokens = ap.capabilities
                .removePrefix("[").removeSuffix("]")
                .split("][")
                .filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    tokens.forEach { token -> ApBadge(token) }
                }
            }

            // ── Live connection info (only when this AP is connected) ──────────
            if (ap.isConnected && connectedInfo != null) {
                HorizontalDivider()
                DetailSectionHeader("Live Connection")
                DetailRow("IP Address",   connectedInfo.ipAddress.ifBlank { "—" })
                DetailRow("Link Speed",   "${connectedInfo.linkSpeedMbps} Mbps")
                if (connectedInfo.txLinkSpeedMbps >= 0)
                    DetailRow("TX Speed", "${connectedInfo.txLinkSpeedMbps} Mbps ↑")
                if (connectedInfo.rxLinkSpeedMbps >= 0)
                    DetailRow("RX Speed", "${connectedInfo.rxLinkSpeedMbps} Mbps ↓")
                DetailRow("Signal",       "${connectedInfo.rssi} dBm  (${connectedInfo.signalQualityPercent}%)")
            }
        }
    }
}

// ── Signal arc gauge ──────────────────────────────────────────────────────────

@Composable private fun SignalArcGauge(quality: Int, level: net.aieat.netswissknife.core.network.wifi.SignalLevel) {
    val fillColor  = signalLevelColor(level)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density    = LocalDensity.current
    val labelSizePx: Float
    val qualityLabelSizePx: Float
    with(density) {
        labelSizePx        = 11.sp.toPx()
        qualityLabelSizePx = 22.sp.toPx()
    }
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val subLabelArgb   = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp, 100.dp)) {
            val strokeWidth = 18f
            val startAngle  = 180f
            val sweepTotal  = 180f
            val sweepFill   = sweepTotal * (quality / 100f)
            val radius      = size.width / 2f - strokeWidth / 2f
            val cx = size.width / 2f
            val cy = size.height

            // Track arc
            drawArc(
                color        = trackColor,
                startAngle   = startAngle,
                sweepAngle   = sweepTotal,
                useCenter    = false,
                topLeft      = Offset(cx - radius, cy - radius),
                size         = Size(radius * 2, radius * 2),
                style        = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            // Filled arc
            drawArc(
                color        = fillColor,
                startAngle   = startAngle,
                sweepAngle   = sweepFill,
                useCenter    = false,
                topLeft      = Offset(cx - radius, cy - radius),
                size         = Size(radius * 2, radius * 2),
                style        = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            // Quality % label in center
            drawContext.canvas.nativeCanvas.drawText(
                "$quality%", cx, cy - radius / 2.5f,
                android.graphics.Paint().apply {
                    color     = labelColorArgb
                    textSize  = qualityLabelSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias    = true
                }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Signal Quality", cx, cy - radius / 2.5f + labelSizePx * 1.6f,
                android.graphics.Paint().apply {
                    color     = subLabelArgb
                    textSize  = labelSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}

// ── Detail section helpers ────────────────────────────────────────────────────

@Composable private fun DetailSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary)
}

@Composable private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f))
    }
}
