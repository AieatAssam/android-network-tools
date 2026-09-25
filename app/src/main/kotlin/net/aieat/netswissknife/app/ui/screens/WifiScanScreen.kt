package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.testTag
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.StatusBad
import net.aieat.netswissknife.app.ui.theme.StatusCritical
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.app.ui.theme.StatusOk
import net.aieat.netswissknife.app.ui.theme.StatusWarn
import net.aieat.netswissknife.app.ui.theme.SpectrumPalette
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.app.ui.screens.wifi.WifiConnectedNetworkCard
import net.aieat.netswissknife.app.ui.screens.wifi.WifiLocationDisabledScreen
import net.aieat.netswissknife.app.ui.screens.wifi.WifiRefreshIntervalPicker
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanFreshnessStatus
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.IconButton
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiSecurity

internal fun wifiSpectrumPlotBounds(
    widthPx: Float,
    leftPadPx: Float,
    isRtl: Boolean,
): ClosedFloatingPointRange<Float> {
    val first = if (isRtl) 0f else leftPadPx
    val last = if (isRtl) widthPx - leftPadPx else widthPx
    return minOf(first, last)..maxOf(first, last)
}

internal fun wifiSpectrumLabelLeft(
    centerXPx: Float,
    labelWidthPx: Float,
    plotBounds: ClosedFloatingPointRange<Float>,
): Float {
    val maxLeft = (plotBounds.endInclusive - labelWidthPx).coerceAtLeast(plotBounds.start)
    return (centerXPx - labelWidthPx / 2f).coerceIn(plotBounds.start, maxLeft)
}

// ── Network colour palette (12 visually distinct colours) ────────────────────

private fun networkColor(colorIndex: Int): Color =
    SpectrumPalette[colorIndex % SpectrumPalette.size]

object WifiScreenTestTags {
    const val CONTENT_LIST = "wifi_content_list"
    const val UNKNOWN_SECURITY_ICON = "wifi_unknown_security_icon"
    const val UNKNOWN_SECURITY_LABEL = "wifi_unknown_security_label"
    const val NETWORKS_START_INDEX = 7
}

/** Permissions needed by WifiManager scan APIs for the given platform SDK. */
fun requiredWifiPermissions(sdkInt: Int): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        // Kept as a string so API 26-32 never resolve an API 33 permission field.
        add("android.permission.NEARBY_WIFI_DEVICES")
    }
}

// ── Screen root ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScanScreen(
    viewModel: WifiScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val autoRefresh by viewModel.autoRefresh.collectAsStateWithLifecycle()
    val refreshIntervalMs by viewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val expandedNetworks by viewModel.expandedNetworks.collectAsStateWithLifecycle()
    val apDisappearedEvent by viewModel.apDisappearedEvent.collectAsStateWithLifecycle()
    val apDisappearedMessage = apDisappearedEvent?.let { stringResource(it.messageResId) }
    val announcementPhase = wifiAnnouncementPhase(uiState)
    ToolStateAnnouncer(stringResource(R.string.help_wifi_title), announcementPhase)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(apDisappearedEvent) {
        apDisappearedMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissApDisappearedEvent()
        }
    }

    val requiredPermissions = requiredWifiPermissions(Build.VERSION.SDK_INT).toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (requiredPermissions.all { grants[it] == true }) viewModel.onPermissionGranted()
        else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (uiState is WifiScanUiState.Idle) {
            permissionLauncher.launch(requiredPermissions)
        } else if (uiState is WifiScanUiState.Success) {
            viewModel.startAutoRefresh()
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.onLifecyclePause() } }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onLifecycleResume()
        } else {
            viewModel.onLifecyclePause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onLifecyclePause()
                Lifecycle.Event.ON_RESUME -> viewModel.onLifecycleResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLifecyclePause()
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label = "screen-alpha"
    )

    val isRefreshing = uiState is WifiScanUiState.Scanning
    val canRefresh = uiState is WifiScanUiState.Success || uiState is WifiScanUiState.Error

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize().alpha(screenAlpha)
    ) { innerPadding ->
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (canRefresh) viewModel.startScan() },
        modifier = Modifier.fillMaxSize().padding(innerPadding)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it::class },
            modifier = Modifier.fillMaxSize(),
            label = "wifi_state"
        ) { state ->
            when (state) {
                is WifiScanUiState.Idle         -> WifiIdleScreen()
                is WifiScanUiState.NoPermission -> WifiNoPermissionScreen(
                    onRequest = { permissionLauncher.launch(requiredPermissions) }
                )
                is WifiScanUiState.NotSupported -> WifiNotSupportedScreen()
                is WifiScanUiState.WifiDisabled -> WifiDisabledScreen(onRetry = { viewModel.startScan() })
                is WifiScanUiState.LocationDisabled -> WifiLocationDisabledScreen(
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )
                is WifiScanUiState.Scanning     -> WifiScanningScreen(onCancel = { viewModel.cancelScan() })
                is WifiScanUiState.Cancelled    -> WifiCancelledScreen(onRetry = { viewModel.startScan() })
                is WifiScanUiState.Paused       -> WifiPausedScreen(onRetry = { viewModel.startScan() })
                is WifiScanUiState.Success      -> WifiSuccessScreen(
                    state               = state,
                    autoRefresh         = autoRefresh,
                    refreshIntervalMs   = refreshIntervalMs,
                    expandedNetworks    = expandedNetworks,
                    onScan              = { viewModel.startScan() },
                    onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
                    onRefreshInterval   = { viewModel.setRefreshInterval(it) },
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
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable private fun WifiIdleScreen() {
    val loadingCd = stringResource(R.string.a11y_loading)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = loadingCd },
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

@Composable private fun WifiScanningScreen(onCancel: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)))
        }
        item {
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
        item { ShimmerBox(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))) }
        item { ShimmerBox(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))) }
        items(4) {
            ShimmerBox(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)))
        }
    }
}

@Composable private fun WifiCancelledScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = stringResource(R.string.wifi_scan_cancelled_title),
        body = stringResource(R.string.wifi_scan_cancelled_body),
        action = { Button(onRetry) { Text(stringResource(R.string.wifi_retry)) } }
    )
}

@Composable private fun WifiPausedScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = stringResource(R.string.wifi_scan_paused_title),
        body = stringResource(R.string.wifi_scan_paused_body),
        action = { Button(onRetry) { Text(stringResource(R.string.wifi_retry)) } }
    )
}

// ── Success screen ────────────────────────────────────────────────────────────

@Composable private fun WifiSuccessScreen(
    state: WifiScanUiState.Success,
    autoRefresh: Boolean,
    refreshIntervalMs: Long?,
    expandedNetworks: Set<String>,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefreshInterval: (Long?) -> Unit,
    onBandFilter: (WifiBand?) -> Unit,
    onSortOrder: (ApSortOrder) -> Unit,
    onSelectAp: (WifiAccessPoint?) -> Unit,
    onToggleNetworkExpanded: (String) -> Unit,
) {
    val networks = state.filteredNetworks
    var showHelp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag(WifiScreenTestTags.CONTENT_LIST),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            WifiHeader(
                result = state.result,
                autoRefresh = autoRefresh,
                onScan = onScan,
                onHelpClick = { showHelp = true }
            )
        }

        item { WifiScanFreshnessStatus(state.result, onRequestPermission) }

        state.result.connectedNetwork?.let { connectionInfo ->
            item { WifiConnectedNetworkCard(connectionInfo) }
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
        item {
            WifiSortRow(
                current = state.sortOrder,
                refreshIntervalMs = refreshIntervalMs,
                onSelect = onSortOrder,
                onRefreshInterval = onRefreshInterval
            )
        }

        // Network count
        item {
            Text(
                pluralStringResource(R.plurals.wifi_networks_count, networks.size, networks.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Network cards (SSID grouped, expandable)
        items(
            count = networks.size,
            key = { i -> networks[i].id }
        ) { i ->
            val network = networks[i]
            val networkId = network.id
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
            conceptHeading = stringResource(R.string.help_wifi_concept_heading),
            conceptBody = stringResource(R.string.help_wifi_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_wifi_what_heading), stringResource(R.string.help_wifi_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_wifi_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_wifi_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_wifi_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_wifi_results_bullets).toList()
                )
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
    onHelpClick: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    )
    // Only run the infinite spin transition while it's actually visible (autoRefresh
    // on) -- rememberInfiniteTransition/animateFloat keeps recomposing every frame
    // for as long as it's composed, regardless of whether the rotation it drives is
    // ever applied, which wastes CPU/battery when idle and (as a side effect) means
    // an instrumented test with autoRefresh off never sees the UI settle.
    val spinAngle = if (autoRefresh) {
        val spinTransition = rememberInfiniteTransition(label = "refresh_spin")
        val angle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
            label = "spin_angle"
        )
        angle
    } else {
        0f
    }
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
                    if (result.scanTimestampMs > 0L) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.scanTimestampMs))
                    } else {
                        null
                    }
                }
                Text(
                    listOfNotNull("${result.networks.size} SSIDs · ${result.accessPoints.size} APs", time)
                        .joinToString(" · "),
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
                FilledTonalIconButton(onClick = hapticAction(onScan)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.wifi_scan_button),
                        modifier = Modifier.graphicsLayer {
                            rotationZ = spinAngle
                        }
                    )
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
    PrimaryTabRow(
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
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val textMeasurer = rememberTextMeasurer()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val strongestAp = aps.maxByOrNull { it.rssi }
    val spectrumDescription = stringResource(
        R.string.wifi_spectrum_a11y,
        aps.size,
        band.displayName,
        strongestAp?.displaySsid ?: stringResource(R.string.wifi_unknown_network),
        strongestAp?.rssi ?: 0,
    )

    val channelLabels = remember(band) { bandChannelLabels(band) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.wifi_spectrum_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .semantics { contentDescription = spectrumDescription },
            ) {
                val leftPad    = 36.dp.toPx()
                val bottomPad  = 20.dp.toPx()
                val chartW     = size.width - leftPad
                val chartH     = size.height - bottomPad

                val rssiMin = -100f
                val rssiMax = -30f

                fun chartX(x: Float) = if (isRtl) size.width - x else x
                fun freqToX(f: Float) = chartX(leftPad + (f - range.min) / (range.max - range.min) * chartW)
                fun rssiToY(r: Float) = chartH - ((r - rssiMin) / (rssiMax - rssiMin)) * chartH

                val bottomY = chartH
                val leftAxisX = chartX(leftPad)
                val rightAxisX = chartX(size.width)
                val plotBounds = wifiSpectrumPlotBounds(size.width, leftPad, isRtl)
                val plotMinX = plotBounds.start
                val plotMaxX = plotBounds.endInclusive

                // Y-axis gridlines: -90, -70, -50, -30 dBm
                listOf(-90f, -70f, -50f, -30f).forEach { rssi ->
                    val y = rssiToY(rssi)
                    drawLine(gridColor, Offset(leftAxisX, y), Offset(rightAxisX, y), strokeWidth = 1f)
                    val label = textMeasurer.measure(
                        text = "${rssi.toInt()}",
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 9.sp,
                            textAlign = if (isRtl) TextAlign.Start else TextAlign.End,
                        ),
                    )
                    val labelX = if (isRtl) leftAxisX + 4.dp.toPx() else leftAxisX - 4.dp.toPx() - label.size.width
                    drawText(label, topLeft = Offset(labelX, y - label.size.height / 2f))
                }

                // Baseline
                drawLine(gridColor, Offset(plotMinX, bottomY), Offset(plotMaxX, bottomY), strokeWidth = 1f)

                // Channel labels on X axis (subtle vertical guides)
                channelLabels.forEach { (ch, freq) ->
                    val x = freqToX(freq)
                    if (x in plotMinX..plotMaxX) {
                        drawLine(
                            gridColor.copy(alpha = 0.35f),
                            Offset(x, 0f), Offset(x, bottomY),
                            strokeWidth = 0.5.dp.toPx()
                        )
                        val label = textMeasurer.measure(
                            text = "$ch",
                            style = TextStyle(color = labelColor, fontSize = 9.sp, textAlign = TextAlign.Center),
                        )
                        drawText(label, topLeft = Offset(x - label.size.width / 2f, size.height - 2.dp.toPx() - label.size.height))
                    }
                }

                // Draw AP triangles — weakest first so strongest renders on top
                aps.sortedBy { it.rssi }.forEach { ap ->
                    val color      = apColors[ap.bssid] ?: Color.Gray
                    val centerFreq = (ap.centerFrequency0.takeIf { it != 0 } ?: ap.frequency).toFloat()
                    val halfMhz    = ap.channelWidthMhz / 2f

                    val xCenter = freqToX(centerFreq)
                    val xOne = freqToX(centerFreq - halfMhz)
                    val xTwo = freqToX(centerFreq + halfMhz)
                    val xLeft = minOf(xOne, xTwo).coerceIn(plotMinX, plotMaxX)
                    val xRight = maxOf(xOne, xTwo).coerceIn(plotMinX, plotMaxX)
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
                    val label = textMeasurer.measure(
                        text = ap.displaySsid.take(10),
                        style = TextStyle(
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    val labelY = (peakY - 4.dp.toPx() - label.size.height).coerceAtLeast(0f)
                    val labelX = wifiSpectrumLabelLeft(xCenter, label.size.width.toFloat(), plotBounds)
                    drawText(label, topLeft = Offset(labelX, labelY))
                }
            }
        }
    }
}

// ── Sort row ──────────────────────────────────────────────────────────────────

@Composable
private fun WifiSortRow(
    current: ApSortOrder,
    refreshIntervalMs: Long?,
    onSelect: (ApSortOrder) -> Unit,
    onRefreshInterval: (Long?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(
            stringResource(R.string.wifi_refresh_interval_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WifiRefreshIntervalPicker(
            selectedIntervalMs = refreshIntervalMs,
            onSelected = onRefreshInterval
        )
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
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
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
                .verticalScroll(rememberScrollState())
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
            WifiSecurityIndicator(ap.security)
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
                DetailRow(stringResource(R.string.wifi_detail_ip),          connectedInfo.ipAddress.ifBlank { "N/A" })
                if (connectedInfo.ipv6Addresses.isNotEmpty()) {
                    DetailRow(stringResource(R.string.wifi_detail_ipv6), connectedInfo.ipv6Addresses.joinToString("\n"))
                }
                connectedInfo.gateway?.let {
                    DetailRow(stringResource(R.string.wifi_detail_gateway), it)
                }
                if (connectedInfo.dnsServers.isNotEmpty()) {
                    DetailRow(stringResource(R.string.wifi_detail_dns), connectedInfo.dnsServers.joinToString("\n"))
                }
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

@Composable
internal fun WifiSecurityIndicator(security: WifiSecurity) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val securityIcon = when (security) {
            WifiSecurity.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
            WifiSecurity.OPEN -> Icons.Default.LockOpen
            else -> Icons.Default.Lock
        }
        Icon(
            securityIcon,
            if (security == WifiSecurity.UNKNOWN) stringResource(R.string.wifi_security_unknown) else null,
            Modifier.size(18.dp).then(
                if (security == WifiSecurity.UNKNOWN) Modifier.testTag(WifiScreenTestTags.UNKNOWN_SECURITY_ICON)
                else Modifier
            ),
            tint = when (security) {
                WifiSecurity.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                WifiSecurity.OPEN -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
        )
        Text(
            security.displayName,
            Modifier.then(
                if (security == WifiSecurity.UNKNOWN) Modifier.testTag(WifiScreenTestTags.UNKNOWN_SECURITY_LABEL)
                else Modifier
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ── Signal arc gauge ──────────────────────────────────────────────────────────

@Composable private fun SignalArcGauge(quality: Int, level: net.aieat.netswissknife.core.network.wifi.SignalLevel) {
    val fillColor  = signalLevelColor(level)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val qualityTextColor = MaterialTheme.colorScheme.onSurface
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val gaugeLabel = stringResource(R.string.wifi_signal_quality)
    val levelLabel = stringResource(
        when (level) {
            net.aieat.netswissknife.core.network.wifi.SignalLevel.EXCELLENT -> R.string.wifi_signal_level_excellent
            net.aieat.netswissknife.core.network.wifi.SignalLevel.GOOD -> R.string.wifi_signal_level_good
            net.aieat.netswissknife.core.network.wifi.SignalLevel.FAIR -> R.string.wifi_signal_level_fair
            net.aieat.netswissknife.core.network.wifi.SignalLevel.WEAK -> R.string.wifi_signal_level_weak
            net.aieat.netswissknife.core.network.wifi.SignalLevel.POOR -> R.string.wifi_signal_level_poor
        },
    )
    val gaugeDescription = stringResource(R.string.wifi_signal_gauge_a11y, quality, levelLabel)

    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = gaugeDescription },
        contentAlignment = Alignment.Center,
    ) {
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

            val qualityText = textMeasurer.measure(
                text = "$quality%",
                style = TextStyle(
                    color = qualityTextColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            drawText(
                qualityText,
                topLeft = Offset(cx - qualityText.size.width / 2f, cy - radius / 2.5f - qualityText.size.height / 2f),
            )
            val labelText = textMeasurer.measure(
                text = gaugeLabel,
                style = TextStyle(
                    color = labelTextColor,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            drawText(
                labelText,
                topLeft = Offset(
                    cx - labelText.size.width / 2f,
                    cy - radius / 2.5f + qualityText.size.height / 2f,
                ),
            )
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
