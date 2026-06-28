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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.StatusBad
import net.aieat.netswissknife.app.ui.theme.StatusCritical
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.app.ui.theme.StatusOk
import net.aieat.netswissknife.app.ui.theme.StatusWarn
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.IconButton
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.core.network.wifi.WifiBand

// ── Network colour palette (12 visually distinct colours) ────────────────────

private val NETWORK_PALETTE = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF5722), Color(0xFF9C27B0),
    Color(0xFFFF9800), Color(0xFF00BCD4), Color(0xFFE91E63), Color(0xFF8BC34A),
    Color(0xFF3F51B5), Color(0xFFFFC107), Color(0xFF009688), Color(0xFFFF5252)
)

private fun networkColor(colorIndex: Int): Color =
    NETWORK_PALETTE[colorIndex % NETWORK_PALETTE.size]

// ── Screen root ───────────────────────────────────────────────────────────────

@Composable
fun WifiScanScreen(
    viewModel: WifiScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val autoRefresh by viewModel.autoRefresh.collectAsStateWithLifecycle()
    val expandedNetworks by viewModel.expandedNetworks.collectAsStateWithLifecycle()

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

    LaunchedEffect(Unit) {
        if (uiState is WifiScanUiState.Idle) {
            permissionLauncher.launch(requiredPermissions)
        } else if (uiState !is WifiScanUiState.NoPermission && uiState !is WifiScanUiState.NotSupported) {
            viewModel.startAutoRefresh()
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopAutoRefresh() } }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "screen-alpha"
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
            is WifiScanUiState.WifiDisabled -> WifiDisabledScreen(onRetry = { viewModel.startScan() })
            is WifiScanUiState.Scanning     -> WifiScanningScreen()
            is WifiScanUiState.Success      -> WifiSuccessScreen(
                state               = state,
                autoRefresh         = autoRefresh,
                expandedNetworks    = expandedNetworks,
                onScan              = { viewModel.startScan() },
                onToggleAutoRefresh = { viewModel.toggleAutoRefresh() },
                onBandFilter        = { viewModel.setBandFilter(it) },
                onSortOrder         = { viewModel.setSortOrder(it) },
                onSelectAp          = { viewModel.selectAccessPoint(it) },
                onToggleNetworkExpanded = { viewModel.toggleNetworkExpanded(it) }
            )
            is WifiScanUiState.Error        -> WifiErrorScreen(
                message = state.message,
                onRetry = { viewModel.onRetry(); permissionLauncher.launch(requiredPermissions) }
            )
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable private fun WifiIdleScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "Loading" },
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Empty / error state helper ────────────────────────────────────────────────

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
                Text(body, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (action != null) { Spacer(Modifier.height(4.dp)); action() }
            }
        }
    }
}

@Composable private fun WifiNoPermissionScreen(onRequest: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.LocationOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        title  = stringResource(R.string.wifi_no_permission_title),
        body   = stringResource(R.string.wifi_no_permission_body),
        action = { Button(onRequest) { Text(stringResource(R.string.wifi_grant_permission)) } }
    )
}

@Composable private fun WifiNotSupportedScreen() {
    WifiStatusCard(
        icon  = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = stringResource(R.string.wifi_not_supported_title),
        body  = stringResource(R.string.wifi_not_supported_body)
    )
}

@Composable private fun WifiDisabledScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.SignalWifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary) },
        title  = stringResource(R.string.wifi_disabled_title),
        body   = stringResource(R.string.wifi_disabled_body),
        action = { Button(onRetry) { Text(stringResource(R.string.wifi_retry)) } }
    )
}

@Composable private fun WifiErrorScreen(message: String, onRetry: () -> Unit) {
    WifiStatusCard(
        icon   = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        title  = stringResource(R.string.wifi_error_title),
        body   = message,
        action = { Button(onRetry) { Text(stringResource(R.string.wifi_retry)) } }
    )
}

// ── Shimmer ───────────────────────────────────────────────────────────────────

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
        item {
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)))
        }
        item { ShimmerBox(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))) }
        item { ShimmerBox(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))) }
        items(4) {
            ShimmerBox(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)))
        }
    }
}

// ── Success screen ────────────────────────────────────────────────────────────

@Composable private fun WifiSuccessScreen(
    state: WifiScanUiState.Success,
    autoRefresh: Boolean,
    expandedNetworks: Set<String>,
    onScan: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onBandFilter: (WifiBand?) -> Unit,
    onSortOrder: (ApSortOrder) -> Unit,
    onSelectAp: (WifiAccessPoint?) -> Unit,
    onToggleNetworkExpanded: (String) -> Unit,
) {
    val networks = state.filteredNetworks
    var showHelp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            WifiHeader(
                result = state.result,
                autoRefresh = autoRefresh,
                onScan = onScan,
                onToggleAutoRefresh = onToggleAutoRefresh,
                onHelpClick = { showHelp = true }
            )
        }

        // Band tabs — always one tab per detected band
        if (state.result.detectedBands.size > 1) {
            item {
                WifiBandTabRow(
                    detectedBands = state.result.detectedBands,
                    selected = state.bandFilter,
                    onSelect = { onBandFilter(it) }
                )
            }
        }

        // Best-channel callout (2.4 GHz only)
        if (state.bandFilter == WifiBand.BAND_2_4GHZ) {
            val bestCh = state.result.bestChannel24GHz
            if (bestCh != null) {
                item { WifiBestChannelCallout(channel = bestCh) }
            }
        }

        // Spectrum analyser
        item { WifiSpectrumCard(state = state) }

        // Sort row
        item { WifiSortRow(current = state.sortOrder, onSelect = onSortOrder) }

        // Network count
        item {
            Text(
                stringResource(R.string.wifi_networks_count, networks.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Network cards (SSID grouped, expandable)
        items(networks.size) { i ->
            val network = networks[i]
            val networkId = "${network.ssid}|${network.security.name}"
            WifiNetworkCard(
                network = network,
                expanded = networkId in expandedNetworks,
                onToggleExpanded = { onToggleNetworkExpanded(networkId) },
                onSelectAp = onSelectAp
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (state.selectedAp != null) {
        WifiApDetailSheet(ap = state.selectedAp, connectedInfo = state.result.connectedNetwork,
            onDismiss = { onSelectAp(null) })
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_wifi_title),
            sections = listOf(
                HelpSection(stringResource(R.string.help_wifi_what_heading), stringResource(R.string.help_wifi_what_body)),
                HelpSection(stringResource(R.string.help_wifi_params_heading), stringResource(R.string.help_wifi_params_body)),
                HelpSection(stringResource(R.string.help_wifi_results_heading), stringResource(R.string.help_wifi_results_body))
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable private fun WifiHeader(
    result: net.aieat.netswissknife.core.network.wifi.WifiScanResult,
    autoRefresh: Boolean,
    onScan: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onHelpClick: () -> Unit,
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
                    Text(
                        stringResource(R.string.wifi_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onHelpClick) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_help),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val time = remember(result.scanTimestampMs) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.scanTimestampMs))
                }
                Text(
                    "${result.networks.size} SSIDs · ${result.accessPoints.size} APs · $time",
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
                Spacer(Modifier.height(8.dp))
                FilledTonalIconButton(onClick = onScan) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.wifi_scan_button))
                }
            }
        }
    }
}

// ── Band TabRow ───────────────────────────────────────────────────────────────

@Composable private fun WifiBandTabRow(
    detectedBands: List<WifiBand>,
    selected: WifiBand?,
    onSelect: (WifiBand) -> Unit
) {
    if (detectedBands.isEmpty()) return
    val selectedIndex = detectedBands.indexOf(selected).coerceAtLeast(0)
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        detectedBands.forEachIndexed { index, band ->
            Tab(
                selected = index == selectedIndex,
                onClick  = { onSelect(band) },
                text     = { Text("${band.ghzLabel} GHz") }
            )
        }
    }
}

// ── Best-channel callout ──────────────────────────────────────────────────────

@Composable private fun WifiBestChannelCallout(channel: Int) {
    Surface(
        color  = MaterialTheme.colorScheme.secondaryContainer,
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Star, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary)
            Column {
                Text(stringResource(R.string.wifi_best_channel_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(stringResource(R.string.wifi_best_channel_body, channel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

// ── Spectrum analyser chart ───────────────────────────────────────────────────

private data class BandFreqRange(val min: Float, val max: Float)

private fun bandFreqRange(band: WifiBand): BandFreqRange? = when (band) {
    WifiBand.BAND_2_4GHZ -> BandFreqRange(2395f, 2495f)
    WifiBand.BAND_5GHZ   -> BandFreqRange(5150f, 5895f)
    WifiBand.BAND_6GHZ   -> BandFreqRange(5925f, 7125f)
    else                  -> null
}

private fun bandChannelLabels(band: WifiBand): List<Pair<Int, Float>> = when (band) {
    WifiBand.BAND_2_4GHZ -> listOf(1 to 2412f, 6 to 2437f, 11 to 2462f, 14 to 2484f)
    WifiBand.BAND_5GHZ   -> listOf(36 to 5180f, 64 to 5320f, 100 to 5500f, 149 to 5745f, 165 to 5825f)
    WifiBand.BAND_6GHZ   -> listOf(1 to 5955f, 37 to 6135f, 73 to 6315f, 117 to 6545f, 181 to 6885f)
    else                  -> emptyList()
}

@Composable private fun WifiSpectrumCard(state: WifiScanUiState.Success) {
    val band = state.bandFilter ?: WifiBand.BAND_2_4GHZ
    val range = bandFreqRange(band) ?: return
    val aps   = state.filteredAccessPoints
    if (aps.isEmpty()) return

    // Build bssid→color map from grouped networks so same SSID = same color
    val apColors = remember(state.filteredNetworks) {
        buildMap<String, Color> {
            state.filteredNetworks.forEach { network ->
                val color = networkColor(network.colorIndex)
                network.accessPoints.forEach { ap -> put(ap.bssid, color) }
            }
        }
    }

    val gridColor      = MaterialTheme.colorScheme.outlineVariant
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f).toArgb()
    val density        = LocalDensity.current
    val labelPx: Float
    val ssidPx: Float
    with(density) { labelPx = 9.sp.toPx(); ssidPx = 10.sp.toPx() }

    val channelLabels = remember(band) { bandChannelLabels(band) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.wifi_spectrum_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val leftPad    = 36.dp.toPx()
                val bottomPad  = 20.dp.toPx()
                val chartW     = size.width - leftPad
                val chartH     = size.height - bottomPad

                val rssiMin = -100f
                val rssiMax = -30f

                fun freqToX(f: Float) = leftPad + (f - range.min) / (range.max - range.min) * chartW
                fun rssiToY(r: Float) = chartH - ((r - rssiMin) / (rssiMax - rssiMin)) * chartH

                val bottomY = chartH

                // Y-axis gridlines: -90, -70, -50, -30 dBm
                val gridPaint = android.graphics.Paint().apply {
                    color       = labelColorArgb
                    textSize    = labelPx
                    textAlign   = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
                listOf(-90f, -70f, -50f, -30f).forEach { rssi ->
                    val y = rssiToY(rssi)
                    drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${rssi.toInt()}", leftPad - 4.dp.toPx(), y + labelPx / 3f, gridPaint
                    )
                }

                // Baseline
                drawLine(gridColor, Offset(leftPad, bottomY), Offset(size.width, bottomY), strokeWidth = 1f)

                // Channel labels on X axis (subtle vertical guides)
                val chPaint = android.graphics.Paint().apply {
                    color       = labelColorArgb
                    textSize    = labelPx
                    textAlign   = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                channelLabels.forEach { (ch, freq) ->
                    val x = freqToX(freq)
                    if (x >= leftPad && x <= size.width) {
                        drawLine(
                            gridColor.copy(alpha = 0.35f),
                            Offset(x, 0f), Offset(x, bottomY),
                            strokeWidth = 0.5.dp.toPx()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "$ch", x, size.height - 2.dp.toPx(), chPaint
                        )
                    }
                }

                // Draw AP triangles — weakest first so strongest renders on top
                aps.sortedBy { it.rssi }.forEach { ap ->
                    val color      = apColors[ap.bssid] ?: Color.Gray
                    val centerFreq = (ap.centerFrequency0.takeIf { it != 0 } ?: ap.frequency).toFloat()
                    val halfMhz    = ap.channelWidthMhz / 2f

                    val xCenter = freqToX(centerFreq)
                    val xLeft   = freqToX(centerFreq - halfMhz).coerceAtLeast(leftPad)
                    val xRight  = freqToX(centerFreq + halfMhz).coerceAtMost(size.width)
                    val peakY   = rssiToY(ap.rssi.toFloat()).coerceIn(4f, bottomY - 4f)

                    val path = Path().apply {
                        moveTo(xLeft, bottomY)
                        lineTo(xCenter, peakY)
                        lineTo(xRight, bottomY)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.45f))
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }

                // SSID labels at each peak
                aps.forEach { ap ->
                    val color      = apColors[ap.bssid] ?: Color.Gray
                    val centerFreq = (ap.centerFrequency0.takeIf { it != 0 } ?: ap.frequency).toFloat()
                    val xCenter    = freqToX(centerFreq)
                    val peakY      = rssiToY(ap.rssi.toFloat()).coerceIn(4f, bottomY - 4f)
                    val labelY     = (peakY - 4.dp.toPx()).coerceAtLeast(ssidPx + 2f)

                    val ssidPaint = android.graphics.Paint().apply {
                        this.color      = color.toArgb()
                        textSize        = ssidPx
                        textAlign       = android.graphics.Paint.Align.CENTER
                        isFakeBoldText  = true
                        isAntiAlias     = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        ap.displaySsid.take(10), xCenter, labelY, ssidPaint
                    )
                }
            }
        }
    }
}

// ── Sort row ──────────────────────────────────────────────────────────────────

@Composable private fun WifiSortRow(current: ApSortOrder, onSelect: (ApSortOrder) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.wifi_sort_label), style = MaterialTheme.typography.labelMedium,
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

// ── Network card (SSID grouped, expandable) ───────────────────────────────────

@Composable private fun WifiNetworkCard(
    network: WifiNetwork,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectAp: (WifiAccessPoint?) -> Unit
) {
    val accentColor = networkColor(network.colorIndex)

    ElevatedCard(
        onClick  = onToggleExpanded,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }
    ) {
        Column {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colour stripe
                Box(
                    Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))

                SignalBarsIcon(quality = network.signalQualityPercent, level = network.signalLevel)
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(network.displaySsid, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (network.isConnected) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(stringResource(R.string.wifi_connected_badge),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (network.ssid.isBlank()) {
                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(stringResource(R.string.wifi_hidden_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        network.sortedBands.forEach { band -> ApBadge("${band.ghzLabel} GHz") }
                        if (network.bssidCount > 1) {
                            ApBadge(stringResource(R.string.wifi_aps_count, network.bssidCount))
                        }
                        ApBadge(network.security.displayName)
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val levelColor = signalLevelColor(network.signalLevel)
                    Text(stringResource(R.string.wifi_rssi_dbm, network.bestRssi),
                        style = MaterialTheme.typography.labelMedium, color = levelColor)
                    Text(stringResource(R.string.wifi_signal_quality_pct, network.signalQualityPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expanded: per-BSSID rows ──────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit  = fadeOut() + slideOutVertically()
            ) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    network.accessPoints.forEach { ap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAp(ap) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SignalBarsIcon(quality = ap.signalQualityPercent, level = ap.signalLevel)
                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(ap.bssid, style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ApBadge("${ap.band.ghzLabel} GHz")
                                    ApBadge("Ch ${ap.channel}")
                                    ApBadge("${ap.channelWidthMhz} MHz")
                                    if (ap.vendor.isNotBlank()) ApBadge(ap.vendor)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val c = signalLevelColor(ap.signalLevel)
                                Text(stringResource(R.string.wifi_rssi_dbm, ap.rssi),
                                    style = MaterialTheme.typography.labelMedium, color = c)
                                Text(stringResource(R.string.wifi_signal_quality_pct, ap.signalQualityPercent),
                                    style = MaterialTheme.typography.labelSmall, color = c.copy(alpha = 0.7f))
                            }

                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable private fun ApBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable private fun SignalBarsIcon(
    quality: Int,
    level: net.aieat.netswissknife.core.network.wifi.SignalLevel
) {
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
        listOf(6.dp, 10.dp, 15.dp, 20.dp).forEachIndexed { i, h ->
            Box(
                Modifier.width(4.dp).height(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (i < bars) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

private fun signalLevelColor(level: net.aieat.netswissknife.core.network.wifi.SignalLevel): Color =
    when (level) {
        net.aieat.netswissknife.core.network.wifi.SignalLevel.EXCELLENT -> StatusGood
        net.aieat.netswissknife.core.network.wifi.SignalLevel.GOOD      -> StatusOk
        net.aieat.netswissknife.core.network.wifi.SignalLevel.FAIR      -> StatusWarn
        net.aieat.netswissknife.core.network.wifi.SignalLevel.WEAK      -> StatusBad
        net.aieat.netswissknife.core.network.wifi.SignalLevel.POOR      -> StatusCritical
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBarsIcon(ap.signalQualityPercent, ap.signalLevel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ap.displaySsid, style = MaterialTheme.typography.titleMedium)
                    Text(ap.bssid, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val levelColor = signalLevelColor(ap.signalLevel)
                    Text(stringResource(R.string.wifi_rssi_dbm, ap.rssi), style = MaterialTheme.typography.titleMedium,
                        color = levelColor)
                    Text(ap.signalLevel.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall, color = levelColor.copy(alpha = 0.7f))
                }
            }

            if (ap.isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wifi_connected_network_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            SignalArcGauge(quality = ap.signalQualityPercent, level = ap.signalLevel)

            HorizontalDivider()

            DetailSectionHeader(stringResource(R.string.wifi_network_info_header))
            DetailRow(stringResource(R.string.wifi_detail_band),     ap.band.displayName)
            DetailRow(stringResource(R.string.wifi_detail_channel),  "${ap.channel}  (${ap.frequency} MHz)")
            DetailRow(stringResource(R.string.wifi_detail_width),    "${ap.channelWidthMhz} MHz")
            DetailRow(stringResource(R.string.wifi_detail_standard), "${ap.standard.generationLabel}  (${ap.standard.protocolName})")
            DetailRow(stringResource(R.string.wifi_detail_max_speed), ap.standard.maxSpeedLabel)
            if (ap.vendor.isNotBlank()) DetailRow(stringResource(R.string.wifi_detail_vendor), ap.vendor)
            if (ap.centerFrequency0 != 0) DetailRow("Center Freq 0", "${ap.centerFrequency0} MHz")
            if (ap.centerFrequency1 != 0) DetailRow("Center Freq 1", "${ap.centerFrequency1} MHz (80+80)")

            HorizontalDivider()

            DetailSectionHeader(stringResource(R.string.wifi_detail_security))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ap.security.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                    null, Modifier.size(18.dp),
                    tint = if (ap.security.isEncrypted) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary
                )
                Text(ap.security.displayName, style = MaterialTheme.typography.bodyMedium)
            }
            val tokens = ap.capabilities
                .removePrefix("[").removeSuffix("]")
                .split("][")
                .filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) { tokens.forEach { token -> ApBadge(token) } }
            }

            if (ap.isConnected && connectedInfo != null) {
                HorizontalDivider()
                DetailSectionHeader(stringResource(R.string.wifi_live_connection_header))
                DetailRow(stringResource(R.string.wifi_detail_ip),          connectedInfo.ipAddress.ifBlank { "—" })
                DetailRow(stringResource(R.string.wifi_detail_link_speed),  "${connectedInfo.linkSpeedMbps} Mbps")
                if (connectedInfo.txLinkSpeedMbps >= 0)
                    DetailRow(stringResource(R.string.wifi_detail_tx_speed), "${connectedInfo.txLinkSpeedMbps} Mbps ↑")
                if (connectedInfo.rxLinkSpeedMbps >= 0)
                    DetailRow(stringResource(R.string.wifi_detail_rx_speed), "${connectedInfo.rxLinkSpeedMbps} Mbps ↓")
                DetailRow(stringResource(R.string.wifi_detail_signal),
                    "${connectedInfo.rssi} dBm  (${connectedInfo.signalQualityPercent}%)")
            }
        }
    }
}

// ── Signal arc gauge ──────────────────────────────────────────────────────────

@Composable private fun SignalArcGauge(quality: Int, level: net.aieat.netswissknife.core.network.wifi.SignalLevel) {
    val fillColor  = signalLevelColor(level)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density    = LocalDensity.current
    val labelPx: Float
    val bigPx: Float
    with(density) { labelPx = 11.sp.toPx(); bigPx = 22.sp.toPx() }
    val labelArgb  = MaterialTheme.colorScheme.onSurface.toArgb()
    val subArgb    = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp, 100.dp)) {
            val strokeWidth = 18f
            val radius = size.width / 2f - strokeWidth / 2f
            val cx = size.width / 2f
            val cy = size.height
            val arcTopLeft = Offset(cx - radius, cy - radius)
            val arcSize    = Size(radius * 2, radius * 2)

            drawArc(trackColor, 180f, 180f, useCenter = false, topLeft = arcTopLeft, size = arcSize,
                style = Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(fillColor, 180f, 180f * (quality / 100f), useCenter = false, topLeft = arcTopLeft, size = arcSize,
                style = Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round))

            drawContext.canvas.nativeCanvas.drawText("$quality%", cx, cy - radius / 2.5f,
                android.graphics.Paint().apply {
                    color = labelArgb; textSize = bigPx; textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true; isAntiAlias = true
                })
            drawContext.canvas.nativeCanvas.drawText("Signal Quality", cx, cy - radius / 2.5f + labelPx * 1.6f,
                android.graphics.Paint().apply {
                    color = subArgb; textSize = labelPx; textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                })
        }
    }
}

// ── Detail section helpers ────────────────────────────────────────────────────

@Composable private fun DetailSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}
