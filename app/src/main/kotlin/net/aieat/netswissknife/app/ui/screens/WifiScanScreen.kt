package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.AppModalBottomSheet
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.SpectrumLabelBox
import net.aieat.netswissknife.app.ui.screens.wifi.WifiConnectedNetworkCard
import net.aieat.netswissknife.app.ui.screens.wifi.WifiLocationDisabledScreen
import net.aieat.netswissknife.app.ui.screens.wifi.WifiRefreshIntervalPicker
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanFreshnessStatus
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.app.ui.screens.wifi.placeSpectrumLabels
import net.aieat.netswissknife.app.ui.screens.wifi.signalLevelLabelRes
import net.aieat.netswissknife.app.ui.screens.wifi.spectrumBandChoices
import net.aieat.netswissknife.app.ui.screens.wifi.spectrumBandCounts
import net.aieat.netswissknife.app.ui.screens.wifi.spectrumLabelCandidates
import net.aieat.netswissknife.app.ui.screens.wifi.spectrumPlotBand
import net.aieat.netswissknife.app.ui.screens.wifi.wifiNetworksMatching
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.SpectrumPalette
import net.aieat.netswissknife.app.ui.theme.StatusBad
import net.aieat.netswissknife.app.ui.theme.StatusCritical
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.app.ui.theme.StatusOk
import net.aieat.netswissknife.app.ui.theme.StatusWarn
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.wifi.SignalLevel
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import kotlin.math.roundToInt

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

private fun networkColor(colorIndex: Int): Color = SpectrumPalette[colorIndex % SpectrumPalette.size]

object WifiScreenTestTags {
    const val CONTENT_LIST = "wifi_content_list"
    const val UNKNOWN_SECURITY_ICON = "wifi_unknown_security_icon"
    const val UNKNOWN_SECURITY_LABEL = "wifi_unknown_security_label"
    const val AP_DETAIL_CONTENT = "wifi_ap_detail_content"
    const val SPECTRUM_TOGGLE = "wifi_spectrum_toggle"
    const val SPECTRUM_BAND_LABEL = "wifi_spectrum_band_label"

    /** The results header (hero, status, search, bands, spectrum, sort, count) is one item, so networks follow it. */
    const val NETWORKS_START_INDEX = 1
}

/** Permissions needed by WifiManager scan APIs for the given platform SDK. */
fun requiredWifiPermissions(sdkInt: Int): List<String> =
    buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            // Kept as a string so API 26-32 never resolve an API 33 permission field.
            add("android.permission.NEARBY_WIFI_DEVICES")
        }
    }

// ── Screen root ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScanScreen(viewModel: WifiScanViewModel = hiltViewModel()) {
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

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (requiredPermissions.all { grants[it] == true }) {
                viewModel.onPermissionGranted()
            } else {
                viewModel.onPermissionDenied()
            }
        }

    LaunchedEffect(Unit) {
        if (uiState is WifiScanUiState.Idle) {
            permissionLauncher.launch(requiredPermissions)
        } else if (uiState is WifiScanUiState.Success) {
            viewModel.startAutoRefresh()
        }
    }

    // Rotation pauses and disposes this screen too. The Activity reports it through
    // isChangingConfigurations; a scan that is in flight must survive that recreation.
    val activity = context as? Activity
    DisposableEffect(Unit) {
        onDispose { viewModel.onLifecyclePause(configurationChange = activity?.isChangingConfigurations == true) }
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onLifecycleResume()
        } else {
            // A screen composed below RESUMED after a configuration change keeps the scan that
            // survived it; ON_RESUME (or the scan completing) takes it from there.
            viewModel.onLifecyclePause(configurationChange = true)
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        viewModel.onLifecyclePause(configurationChange = activity?.isChangingConfigurations == true)
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        viewModel.onLifecycleResume()
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLifecyclePause(configurationChange = activity?.isChangingConfigurations == true)
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label = "screen-alpha",
    )

    val isRefreshing = uiState is WifiScanUiState.Scanning
    val canRefresh = uiState is WifiScanUiState.Success || uiState is WifiScanUiState.Error

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize().alpha(screenAlpha),
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { if (canRefresh) viewModel.startScan() },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                modifier = Modifier.fillMaxSize(),
                label = "wifi_state",
            ) { state ->
                when (state) {
                    is WifiScanUiState.Idle -> {
                        WifiIdleScreen()
                    }

                    is WifiScanUiState.NoPermission -> {
                        WifiNoPermissionScreen(
                            onRequest = { permissionLauncher.launch(requiredPermissions) },
                        )
                    }

                    is WifiScanUiState.NotSupported -> {
                        WifiNotSupportedScreen()
                    }

                    is WifiScanUiState.WifiDisabled -> {
                        WifiDisabledScreen(onRetry = { viewModel.startScan() })
                    }

                    is WifiScanUiState.LocationDisabled -> {
                        WifiLocationDisabledScreen(
                            onOpenSettings = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                        )
                    }

                    is WifiScanUiState.Scanning -> {
                        WifiScanningScreen(onCancel = { viewModel.cancelScan() })
                    }

                    is WifiScanUiState.Cancelled -> {
                        WifiCancelledScreen(onRetry = { viewModel.startScan() })
                    }

                    is WifiScanUiState.Paused -> {
                        WifiPausedScreen(onRetry = { viewModel.startScan() })
                    }

                    is WifiScanUiState.Success -> {
                        WifiSuccessScreen(
                            state = state,
                            autoRefresh = autoRefresh,
                            refreshIntervalMs = refreshIntervalMs,
                            expandedNetworks = expandedNetworks,
                            onScan = { viewModel.startScan() },
                            onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
                            onRefreshInterval = { viewModel.setRefreshInterval(it) },
                            onBandFilter = { viewModel.setBandFilter(it) },
                            onSortOrder = { viewModel.setSortOrder(it) },
                            onSelectAp = { viewModel.selectAccessPoint(it) },
                            onToggleNetworkExpanded = { viewModel.toggleNetworkExpanded(it) },
                        )
                    }

                    is WifiScanUiState.Error -> {
                        WifiErrorScreen(
                            bodyRes = state.bodyRes,
                            onRetry = {
                                viewModel.onRetry()
                                permissionLauncher.launch(requiredPermissions)
                            },
                        )
                    }
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
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Empty / error state helper ────────────────────────────────────────────────

@Composable private fun WifiStatusCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.wifi_screen_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    icon()
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (action != null) {
                        Spacer(Modifier.height(4.dp))
                        action()
                    }
                }
            }
        }
    }
}

@Composable private fun WifiNoPermissionScreen(onRequest: () -> Unit) {
    WifiStatusCard(
        icon = { Icon(Icons.Default.LocationOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        title = stringResource(R.string.wifi_no_permission_title),
        body = stringResource(R.string.wifi_no_permission_body),
        action = { Button(onRequest) { Text(stringResource(R.string.wifi_grant_permission)) } },
    )
}

@Composable private fun WifiNotSupportedScreen() {
    WifiStatusCard(
        icon = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = stringResource(R.string.wifi_not_supported_title),
        body = stringResource(R.string.wifi_not_supported_body),
    )
}

@Composable private fun WifiDisabledScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon = { Icon(Icons.Default.SignalWifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary) },
        title = stringResource(R.string.wifi_disabled_title),
        body = stringResource(R.string.wifi_disabled_body),
        action = { ToolRetryButton(text = stringResource(R.string.wifi_retry), onClick = onRetry) },
    )
}

@Composable private fun WifiErrorScreen(
    @StringRes bodyRes: Int,
    onRetry: () -> Unit,
) {
    WifiStatusCard(
        icon = { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        title = stringResource(R.string.wifi_error_title),
        body = stringResource(bodyRes),
        action = { ToolRetryButton(text = stringResource(R.string.wifi_retry), onClick = onRetry) },
    )
}

// ── Shimmer ───────────────────────────────────────────────────────────────────

@Composable private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val colors =
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    Box(
        modifier.background(
            Brush.linearGradient(colors, start = Offset(shimmerX * 600f, 0f), end = Offset(shimmerX * 600f + 600f, 0f)),
        ),
    )
}

@Composable private fun WifiScanningScreen(onCancel: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        icon = {
            Icon(
                Icons.Default.Stop,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = stringResource(R.string.wifi_scan_cancelled_title),
        body = stringResource(R.string.wifi_scan_cancelled_body),
        action = { ToolRetryButton(text = stringResource(R.string.wifi_retry), onClick = onRetry) },
    )
}

@Composable private fun WifiPausedScreen(onRetry: () -> Unit) {
    WifiStatusCard(
        icon = {
            Icon(
                Icons.Default.Pause,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = stringResource(R.string.wifi_scan_paused_title),
        body = stringResource(R.string.wifi_scan_paused_body),
        action = { ToolRetryButton(text = stringResource(R.string.wifi_retry), onClick = onRetry) },
    )
}

// ── Success screen ────────────────────────────────────────────────────────────

/** Localised pieces of the Wi-Fi share and copy text. Resolved only when the user taps Share or Copy. */
internal data class WifiShareLabels(
    val accessPoints: (Int) -> String,
    val freshness: (fresh: Boolean) -> String,
    val sampleAge: (ageMs: Long?) -> String,
    val refreshStatus: (WifiScanRefreshStatus) -> String,
    val sampleTime: (timestampMs: Long) -> String,
    val wifiDisabled: String,
    val locationDisabled: String,
    val rssi: (dbm: Int) -> String,
    val bandChannel: (band: WifiBand, channel: Int) -> String,
)

internal fun wifiShareLabels(resources: Resources): WifiShareLabels =
    WifiShareLabels(
        accessPoints = { count -> resources.getQuantityString(R.plurals.wifi_share_access_points, count, count) },
        freshness = { fresh ->
            resources.getString(
                R.string.wifi_share_freshness,
                resources.getString(if (fresh) R.string.wifi_share_fresh else R.string.wifi_share_cached),
            )
        },
        sampleAge = { ageMs ->
            resources.getString(
                R.string.wifi_share_sample_age,
                ageMs?.let { resources.getString(R.string.wifi_share_age_ms, it) }
                    ?: resources.getString(R.string.wifi_share_unknown),
            )
        },
        refreshStatus = { status ->
            resources.getString(R.string.wifi_share_refresh_status, resources.getString(refreshStatusRes(status)))
        },
        sampleTime = { timestampMs ->
            resources.getString(
                R.string.wifi_share_sample_time,
                java.text.DateFormat
                    .getDateTimeInstance()
                    .format(java.util.Date(timestampMs)),
            )
        },
        wifiDisabled = resources.getString(R.string.wifi_share_wifi_disabled),
        locationDisabled = resources.getString(R.string.wifi_share_location_disabled),
        rssi = { dbm -> resources.getString(R.string.wifi_rssi_dbm, dbm) },
        bandChannel = { band, channel ->
            resources.getString(R.string.wifi_band_channel_value, band.ghzLabel, channel)
        },
    )

@StringRes
private fun refreshStatusRes(status: WifiScanRefreshStatus): Int =
    when (status) {
        WifiScanRefreshStatus.NOT_REQUESTED -> R.string.wifi_share_status_not_requested
        WifiScanRefreshStatus.UPDATED -> R.string.wifi_share_status_updated
        WifiScanRefreshStatus.NOT_UPDATED -> R.string.wifi_scan_not_updated
        WifiScanRefreshStatus.TIMED_OUT -> R.string.wifi_scan_timed_out
        WifiScanRefreshStatus.REJECTED -> R.string.wifi_scan_rejected
        WifiScanRefreshStatus.FAILED -> R.string.wifi_scan_failed_cached
        WifiScanRefreshStatus.PERMISSION_DENIED -> R.string.wifi_scan_permission_denied_cached
    }

internal fun buildWifiShareText(
    result: WifiScanResult,
    labels: WifiShareLabels,
): String =
    buildString {
        appendLine(labels.accessPoints(result.accessPoints.size))
        appendLine(labels.freshness(result.isFresh))
        appendLine(labels.sampleAge(result.scanAgeMs))
        appendLine(labels.refreshStatus(result.refreshStatus))
        if (result.scanTimestampMs > 0) appendLine(labels.sampleTime(result.scanTimestampMs))
        if (!result.isWifiEnabled) appendLine(labels.wifiDisabled)
        if (!result.locationEnabled) appendLine(labels.locationDisabled)
        result.accessPoints.sortedBy { it.displaySsid.lowercase() }.forEach { ap ->
            appendLine(
                listOf(
                    ap.displaySsid,
                    ap.bssid,
                    labels.rssi(ap.rssi),
                    labels.bandChannel(ap.band, ap.channel),
                    ap.security.displayName,
                ).joinToString(" · "),
            )
        }
    }

@Composable
private fun wifiApCountText(
    shownApCount: Int,
    totalApCount: Int,
): String =
    if (shownApCount == totalApCount) {
        pluralStringResource(R.plurals.wifi_aps_count, totalApCount, totalApCount)
    } else {
        pluralStringResource(R.plurals.wifi_aps_of_total, totalApCount, shownApCount, totalApCount)
    }

/** Copy and Share actions for the whole scan result. */
@Composable
private fun WifiShareActionsRow(result: WifiScanResult) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.lan_copied)
    val shareSubject = stringResource(R.string.wifi_screen_title)
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = buildWifiShareText(result, wifiShareLabels(context.resources))
            clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi scan", text))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.action_copy)) }
        TextButton(onClick = {
            context.shareText(
                buildWifiShareText(result, wifiShareLabels(context.resources)),
                shareSubject,
            )
        }) { Text(stringResource(R.string.action_share)) }
    }
}

/** The best 2.4 GHz channel, shown only while the 2.4 GHz band is selected and a best channel is known. */
@Composable
private fun WifiBestChannelSection(state: WifiScanUiState.Success) {
    if (state.bandFilter == WifiBand.BAND_2_4GHZ) {
        val bestCh = state.result.bestChannel24GHz
        if (bestCh != null) {
            WifiBestChannelCallout(channel = bestCh)
        }
    }
}

/** The show/hide toggle for the channel spectrum and, when expanded, the spectrum card. */
@Composable
private fun WifiSpectrumSection(
    band: WifiBand,
    aps: List<WifiAccessPoint>,
    colorNetworks: List<WifiNetwork>,
    bandChoices: List<Pair<WifiBand, Int>>,
    onChooseBand: (WifiBand) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.testTag(WifiScreenTestTags.SPECTRUM_TOGGLE),
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(
                if (expanded) R.string.wifi_spectrum_hide else R.string.wifi_spectrum_show,
            ),
        )
    }
    if (expanded) {
        WifiSpectrumCard(
            band = band,
            aps = aps,
            colorNetworks = colorNetworks,
            bandChoices = bandChoices,
            onChooseBand = onChooseBand,
        )
    }
}

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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var spectrumExpanded by rememberSaveable { mutableStateOf(false) }
    // The band the spectrum plots while the band tab is All; a band tab decides it otherwise.
    var spectrumChoice by rememberSaveable { mutableStateOf<WifiBand?>(null) }
    val networks = wifiNetworksMatching(state.filteredNetworks, searchQuery)
    // The spectrum shows the same access points as the list, so search narrows both.
    val visibleBssids = networks.flatMap { network -> network.accessPoints.map { it.bssid } }.toSet()
    val shownApCount = state.filteredAccessPoints.count { it.bssid in visibleBssids }
    val totalApCount = state.result.accessPoints.size
    val apCountText = wifiApCountText(shownApCount, totalApCount)
    val spectrumBandCounts = spectrumBandCounts(state.filteredAccessPoints.filter { it.bssid in visibleBssids })
    val spectrumBand =
        spectrumPlotBand(
            state.bandFilter,
            spectrumChoice,
            state.result.connectedNetwork?.band,
            state.result.detectedBands,
            spectrumBandCounts,
        )
    val spectrumAps = state.filteredAccessPoints.filter { it.bssid in visibleBssids && it.band == spectrumBand }
    // With the All tab the chart plots one band at a time, so it offers the others; a band tab already chose one.
    val spectrumBandChoices = spectrumBandChoices(state.bandFilter, spectrumBandCounts)
    val spectrumAvailable = spectrumBand != null && bandFreqRange(spectrumBand) != null && spectrumAps.isNotEmpty()
    var showHelp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag(WifiScreenTestTags.CONTENT_LIST),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The results header is a single item so the network list always starts at NETWORKS_START_INDEX.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))

                WifiHeader(
                    result = state.result,
                    apCountText = apCountText,
                    autoRefresh = autoRefresh,
                    onScan = onScan,
                    onHelpClick = { showHelp = true },
                )

                WifiScanFreshnessStatus(state.result, onRequestPermission)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.wifi_refresh_interval_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WifiRefreshIntervalPicker(
                        selectedIntervalMs = refreshIntervalMs,
                        onSelected = onRefreshInterval,
                    )
                }

                ToolTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.wifi_search_label)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Band tabs and the spectrum toggle sit directly under the search box so they are reachable
                // at phone width without scrolling past the connected card. All first, then one tab per band seen.
                if (state.result.detectedBands.isNotEmpty()) {
                    WifiBandTabRow(
                        detectedBands = state.result.detectedBands,
                        selected = state.bandFilter,
                        onSelect = { onBandFilter(it) },
                    )
                }

                if (spectrumBand != null && spectrumAvailable) {
                    WifiSpectrumSection(
                        band = spectrumBand,
                        aps = spectrumAps,
                        colorNetworks = state.filteredNetworks,
                        bandChoices = spectrumBandChoices,
                        onChooseBand = { spectrumChoice = it },
                        expanded = spectrumExpanded,
                        onToggle = { spectrumExpanded = !spectrumExpanded },
                    )
                }

                // Best-channel callout (2.4 GHz only)
                WifiBestChannelSection(state)

                WifiShareActionsRow(state.result)

                state.result.connectedNetwork?.let { connectionInfo ->
                    WifiConnectedNetworkCard(connectionInfo)
                }

                WifiSortRow(
                    current = state.sortOrder,
                    onSelect = onSortOrder,
                )

                Text(
                    pluralStringResource(R.plurals.wifi_networks_count, networks.size, networks.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (networks.isEmpty() && searchQuery.isNotBlank()) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.wifi_search_no_match, searchQuery.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { searchQuery = "" }) {
                                Text(stringResource(R.string.wifi_search_clear))
                            }
                        }
                    }
                }
            }
        }

        // Network cards (SSID grouped, expandable). animateItem lets a reorder from a silent
        // refresh move cards smoothly instead of jumping under the user's finger.
        items(
            count = networks.size,
            key = { i -> networks[i].id },
        ) { i ->
            val network = networks[i]
            val networkId = network.id
            Box(Modifier.fillMaxWidth().animateItem()) {
                WifiNetworkCard(
                    network = network,
                    showBand = state.bandFilter == null,
                    expanded = networkId in expandedNetworks,
                    onToggleExpanded = { onToggleNetworkExpanded(networkId) },
                    onSelectAp = onSelectAp,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (state.selectedAp != null) {
        WifiApDetailSheet(
            ap = state.selectedAp,
            onDismiss = { onSelectAp(null) },
        )
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_wifi_title),
            conceptHeading = stringResource(R.string.help_wifi_concept_heading),
            conceptBody = stringResource(R.string.help_wifi_concept_body),
            sections =
                listOf(
                    HelpSection(stringResource(R.string.help_wifi_what_heading), stringResource(R.string.help_wifi_what_body)),
                    HelpSection(
                        heading = stringResource(R.string.help_wifi_params_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_wifi_params_bullets).toList(),
                    ),
                    HelpSection(
                        heading = stringResource(R.string.help_wifi_results_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_wifi_results_bullets).toList(),
                    ),
                ),
            onDismiss = { showHelp = false },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable private fun WifiHeader(
    result: net.aieat.netswissknife.core.network.wifi.WifiScanResult,
    apCountText: String,
    autoRefresh: Boolean,
    onScan: () -> Unit,
    onHelpClick: () -> Unit,
) {
    val gradient =
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
        )
    // Only run the infinite spin transition while it's actually visible (autoRefresh
    // on) -- rememberInfiniteTransition/animateFloat keeps recomposing every frame
    // for as long as it's composed, regardless of whether the rotation it drives is
    // ever applied, which wastes CPU/battery when idle and (as a side effect) means
    // an instrumented test with autoRefresh off never sees the UI settle.
    val spinAngle =
        if (autoRefresh) {
            val spinTransition = rememberInfiniteTransition(label = "refresh_spin")
            val angle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                label = "spin_angle",
            )
            angle
        } else {
            0f
        }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(Modifier.background(gradient)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.wifi_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_help),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Network count is in the list header and the time is in the Updated line, so the hero
                // states only the access-point count (PHA-20).
                Text(
                    apCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                // With several bands the band tabs name them, so the hero names a band only when there is one.
                if (result.detectedBands.size == 1) {
                    Text(
                        result.detectedBands.single().displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // One labelled action: an icon-only refresh was the only scan control (PHA-20).
                FilledTonalButton(onClick = hapticAction(onScan)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = spinAngle
                                },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wifi_scan_button))
                }
            }
        }
    }
}

// ── Band TabRow ───────────────────────────────────────────────────────────────

@Composable private fun WifiBandTabRow(
    detectedBands: List<WifiBand>,
    selected: WifiBand?,
    onSelect: (WifiBand?) -> Unit,
) {
    if (detectedBands.isEmpty()) return
    val allLabel = stringResource(R.string.wifi_band_all)
    val selectedIndex = if (selected == null) 0 else detectedBands.indexOf(selected) + 1
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onSelect(null) },
            text = { Text(allLabel) },
        )
        detectedBands.forEachIndexed { index, band ->
            Tab(
                selected = index + 1 == selectedIndex,
                onClick = { onSelect(band) },
                text = { Text("${band.ghzLabel} GHz") },
            )
        }
    }
}

// ── Best-channel callout ──────────────────────────────────────────────────────

@Composable private fun WifiBestChannelCallout(channel: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Star,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column {
                Text(
                    stringResource(R.string.wifi_best_channel_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.wifi_best_channel_body, channel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ── Spectrum analyser chart ───────────────────────────────────────────────────

private data class BandFreqRange(
    val min: Float,
    val max: Float,
)

private fun bandFreqRange(band: WifiBand): BandFreqRange? =
    when (band) {
        WifiBand.BAND_2_4GHZ -> BandFreqRange(2395f, 2495f)
        WifiBand.BAND_5GHZ -> BandFreqRange(5150f, 5895f)
        WifiBand.BAND_6GHZ -> BandFreqRange(5925f, 7125f)
        else -> null
    }

private fun bandChannelLabels(band: WifiBand): List<Pair<Int, Float>> =
    when (band) {
        WifiBand.BAND_2_4GHZ -> listOf(1 to 2412f, 6 to 2437f, 11 to 2462f, 14 to 2484f)
        WifiBand.BAND_5GHZ -> listOf(36 to 5180f, 64 to 5320f, 100 to 5500f, 149 to 5745f, 165 to 5825f)
        WifiBand.BAND_6GHZ -> listOf(1 to 5955f, 37 to 6135f, 73 to 6315f, 117 to 6545f, 181 to 6885f)
        else -> emptyList()
    }

/** Centre frequency of the access point's channel, or its primary frequency when unset. */
private fun spectrumCenterFrequency(ap: WifiAccessPoint): Float {
    val centre = ap.centerFrequency0.takeIf { it != 0 } ?: ap.frequency
    return centre.toFloat()
}

@Composable private fun WifiSpectrumCard(
    band: WifiBand,
    aps: List<WifiAccessPoint>,
    colorNetworks: List<WifiNetwork>,
    bandChoices: List<Pair<WifiBand, Int>>,
    onChooseBand: (WifiBand) -> Unit,
) {
    val range = bandFreqRange(band) ?: return
    if (aps.isEmpty()) return

    // Build bssid→color map from grouped networks so same SSID = same color
    val apColors =
        remember(colorNetworks) {
            buildMap<String, Color> {
                colorNetworks.forEach { network ->
                    val color = networkColor(network.colorIndex)
                    network.accessPoints.forEach { ap -> put(ap.bssid, color) }
                }
            }
        }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val textMeasurer = rememberTextMeasurer()
    // Spectrum labels sit on a backing of the card colour so they stay readable where a curve crosses them (PHA-20).
    val labelBacking = MaterialTheme.colorScheme.surface
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val strongestAp = aps.maxByOrNull { it.rssi }
    val spectrumDescription =
        stringResource(
            R.string.wifi_spectrum_a11y,
            aps.size,
            band.displayName,
            strongestAp?.displaySsid ?: stringResource(R.string.wifi_unknown_network),
            strongestAp?.rssi ?: 0,
        )

    val channelLabels = remember(band) { bandChannelLabels(band) }
    val axisUnit = stringResource(R.string.wifi_spectrum_axis_unit)

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.wifi_spectrum_title), style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.wifi_spectrum_band_label, aps.size, band.displayName, aps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(WifiScreenTestTags.SPECTRUM_BAND_LABEL),
            )
            if (bandChoices.isNotEmpty()) SpectrumBandChoice(band, bandChoices, onChooseBand)
            Spacer(Modifier.height(8.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .semantics { contentDescription = spectrumDescription },
            ) {
                val leftPad = 36.dp.toPx()
                val bottomPad = 20.dp.toPx()
                // Room above the plot for the axis unit, so it never sits on the top gridline label.
                val topPad = 14.dp.toPx()
                val chartW = size.width - leftPad
                val chartH = size.height - bottomPad - topPad

                val rssiMin = -100f
                val rssiMax = -30f

                fun chartX(x: Float) = if (isRtl) size.width - x else x

                fun freqToX(f: Float) = chartX(leftPad + (f - range.min) / (range.max - range.min) * chartW)

                fun rssiToY(r: Float) = topPad + chartH - ((r - rssiMin) / (rssiMax - rssiMin)) * chartH

                val bottomY = topPad + chartH
                val leftAxisX = chartX(leftPad)
                val rightAxisX = chartX(size.width)
                val plotBounds = wifiSpectrumPlotBounds(size.width, leftPad, isRtl)
                val plotMinX = plotBounds.start
                val plotMaxX = plotBounds.endInclusive

                // Y-axis gridlines: -90, -70, -50, -30 dBm
                listOf(-90f, -70f, -50f, -30f).forEach { rssi ->
                    val y = rssiToY(rssi)
                    drawLine(gridColor, Offset(leftAxisX, y), Offset(rightAxisX, y), strokeWidth = 1f)
                    val label =
                        textMeasurer.measure(
                            text = "${rssi.toInt()}",
                            style =
                                TextStyle(
                                    color = labelColor,
                                    fontSize = 9.sp,
                                    textAlign = if (isRtl) TextAlign.Start else TextAlign.End,
                                ),
                        )
                    val labelX = if (isRtl) leftAxisX + 4.dp.toPx() else leftAxisX - 4.dp.toPx() - label.size.width
                    drawText(label, topLeft = Offset(labelX, y - label.size.height / 2f))
                }

                val unitLabel =
                    textMeasurer.measure(
                        text = axisUnit,
                        style = TextStyle(color = labelColor, fontSize = 9.sp, textAlign = TextAlign.End),
                    )
                val unitX = if (isRtl) leftAxisX + 4.dp.toPx() else leftAxisX - 4.dp.toPx() - unitLabel.size.width
                drawText(unitLabel, topLeft = Offset(unitX, 0f))

                // Baseline
                drawLine(gridColor, Offset(plotMinX, bottomY), Offset(plotMaxX, bottomY), strokeWidth = 1f)

                // Channel labels on X axis (subtle vertical guides)
                channelLabels.forEach { (ch, freq) ->
                    val x = freqToX(freq)
                    if (x in plotMinX..plotMaxX) {
                        drawLine(
                            gridColor.copy(alpha = 0.35f),
                            Offset(x, 0f),
                            Offset(x, bottomY),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                        val label =
                            textMeasurer.measure(
                                text = "$ch",
                                style = TextStyle(color = labelColor, fontSize = 9.sp, textAlign = TextAlign.Center),
                            )
                        drawText(label, topLeft = Offset(x - label.size.width / 2f, size.height - 2.dp.toPx() - label.size.height))
                    }
                }

                // Draw AP triangles — weakest first so strongest renders on top
                aps.sortedBy { it.rssi }.forEach { ap ->
                    val color = apColors[ap.bssid] ?: Color.Gray
                    val centerFreq = (ap.centerFrequency0.takeIf { it != 0 } ?: ap.frequency).toFloat()
                    val halfMhz = ap.channelWidthMhz / 2f

                    val xCenter = freqToX(centerFreq)
                    val xOne = freqToX(centerFreq - halfMhz)
                    val xTwo = freqToX(centerFreq + halfMhz)
                    val xLeft = minOf(xOne, xTwo).coerceIn(plotMinX, plotMaxX)
                    val xRight = maxOf(xOne, xTwo).coerceIn(plotMinX, plotMaxX)
                    val peakY = rssiToY(ap.rssi.toFloat()).coerceIn(4f, bottomY - 4f)

                    val path =
                        Path().apply {
                            moveTo(xLeft, bottomY)
                            lineTo(xCenter, peakY)
                            lineTo(xRight, bottomY)
                            close()
                        }
                    drawPath(path, color.copy(alpha = 0.45f))
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }

                // One SSID label per network at its strongest peak. Ellipsised to a fixed width;
                // colliding labels stack upward and are dropped past the tier limit, because the
                // network list below doubles as the legend.
                val labelMaxWidthPx = 96.dp.toPx().roundToInt()
                val measuredLabels =
                    spectrumLabelCandidates(aps).map { ap ->
                        ap to
                            textMeasurer.measure(
                                text = ap.displaySsid,
                                style =
                                    TextStyle(
                                        color = apColors[ap.bssid] ?: Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                constraints = Constraints(maxWidth = labelMaxWidthPx),
                            )
                    }
                val placements =
                    placeSpectrumLabels(
                        labels =
                            measuredLabels.mapIndexed { index, (ap, text) ->
                                SpectrumLabelBox(
                                    id = index,
                                    centerX = freqToX(spectrumCenterFrequency(ap)),
                                    width = text.size.width.toFloat(),
                                    height = text.size.height.toFloat(),
                                    peakY = rssiToY(ap.rssi.toFloat()).coerceIn(4f, bottomY - 4f),
                                )
                            },
                        plotBounds = plotBounds,
                    )
                placements.forEach { placement ->
                    val text = measuredLabels[placement.id].second
                    val pad = 2.dp.toPx()
                    drawRoundRect(
                        color = labelBacking.copy(alpha = 0.85f),
                        topLeft = Offset(placement.left - pad, placement.top - pad / 2f),
                        size = Size(text.size.width + pad * 2f, text.size.height + pad),
                        cornerRadius = CornerRadius(pad, pad),
                    )
                    drawText(text, topLeft = Offset(placement.left, placement.top))
                }
            }
        }
    }
}

/** Which band the spectrum plots while the band tab is All, each with the access points it would plot. */
@Composable
private fun SpectrumBandChoice(
    selected: WifiBand,
    choices: List<Pair<WifiBand, Int>>,
    onChoose: (WifiBand) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (band, count) ->
            FilterChip(
                selected = band == selected,
                onClick = { onChoose(band) },
                label = { Text(stringResource(R.string.wifi_spectrum_band_choice, band.displayName, count)) },
            )
        }
    }
}

// ── Sort row ──────────────────────────────────────────────────────────────────

@Composable
private fun WifiSortRow(
    current: ApSortOrder,
    onSelect: (ApSortOrder) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.wifi_sort_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ApSortOrder.entries.forEach { order ->
            FilterChip(
                selected = current == order,
                onClick = { onSelect(order) },
                label = { Text(stringResource(order.labelRes), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ── Network card (SSID grouped, expandable) ───────────────────────────────────

@Composable private fun WifiNetworkCard(
    network: WifiNetwork,
    showBand: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectAp: (WifiAccessPoint?) -> Unit,
) {
    val accentColor = networkColor(network.colorIndex)

    ElevatedCard(
        onClick = onToggleExpanded,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("wifi_network_card_${network.displaySsid}")
                .semantics { role = Role.Button },
    ) {
        Column {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Colour stripe
                Box(
                    Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .background(accentColor, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))

                SignalBarsIcon(level = network.signalLevel)
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(network.displaySsid, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (network.isConnected) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(
                                    stringResource(R.string.wifi_connected_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        // A hidden network's name already reads "<Hidden: BSSID>", so no separate badge (V7A-04).
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (showBand) {
                            network.sortedBands.forEach { band -> ApBadge("${band.ghzLabel} GHz") }
                        }
                        if (network.bssidCount > 1) {
                            val count = network.bssidCount
                            ApBadge(pluralStringResource(R.plurals.wifi_aps_count, count, count))
                        }
                        ApBadge(network.security.displayName)
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val levelColor = signalLevelColor(network.signalLevel)
                    Text(
                        stringResource(R.string.wifi_rssi_dbm, network.bestRssi),
                        style = MaterialTheme.typography.labelMedium,
                        color = levelColor,
                    )
                    Text(
                        stringResource(signalLevelLabelRes(network.signalLevel)),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Expanded: per-BSSID rows ──────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    network.accessPoints.forEach { ap ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAp(ap) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SignalBarsIcon(level = ap.signalLevel)
                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    ap.bssid,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (showBand) ApBadge("${ap.band.ghzLabel} GHz")
                                    ApBadge(stringResource(R.string.wifi_channel_badge, ap.channel))
                                    ApBadge("${ap.channelWidthMhz} MHz")
                                    if (ap.vendor.isNotBlank()) ApBadge(ap.vendor)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val c = signalLevelColor(ap.signalLevel)
                                Text(
                                    stringResource(R.string.wifi_rssi_dbm, ap.rssi),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c,
                                )
                                Text(
                                    stringResource(signalLevelLabelRes(ap.signalLevel)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c,
                                )
                            }

                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable private fun SignalBarsIcon(level: SignalLevel) {
    val color = signalLevelColor(level)
    val bars =
        when (level) {
            SignalLevel.EXCELLENT -> 4
            SignalLevel.GOOD -> 3
            SignalLevel.FAIR -> 2
            SignalLevel.WEAK -> 1
            SignalLevel.POOR -> 0
        }
    Row(
        modifier = Modifier.width(24.dp).height(24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(6.dp, 10.dp, 15.dp, 20.dp).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (i < bars) color else color.copy(alpha = 0.2f)),
            )
        }
    }
}

private fun signalLevelColor(level: net.aieat.netswissknife.core.network.wifi.SignalLevel): Color =
    when (level) {
        net.aieat.netswissknife.core.network.wifi.SignalLevel.EXCELLENT -> StatusGood
        net.aieat.netswissknife.core.network.wifi.SignalLevel.GOOD -> StatusOk
        net.aieat.netswissknife.core.network.wifi.SignalLevel.FAIR -> StatusWarn
        net.aieat.netswissknife.core.network.wifi.SignalLevel.WEAK -> StatusBad
        net.aieat.netswissknife.core.network.wifi.SignalLevel.POOR -> StatusCritical
    }

// ── AP Detail Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiApDetailSheet(
    ap: WifiAccessPoint,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag(WifiScreenTestTags.AP_DETAIL_CONTENT)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBarsIcon(ap.signalLevel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ap.displaySsid, style = MaterialTheme.typography.titleMedium)
                    Text(
                        ap.bssid,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val levelColor = signalLevelColor(ap.signalLevel)
                    Text(
                        stringResource(R.string.wifi_rssi_dbm, ap.rssi),
                        style = MaterialTheme.typography.titleMedium,
                        color = levelColor,
                    )
                    Text(
                        stringResource(signalLevelLabelRes(ap.signalLevel)),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                    )
                }
            }

            if (ap.isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wifi_connected_network_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            HorizontalDivider()

            DetailSectionHeader(stringResource(R.string.wifi_network_info_header))
            DetailRow(stringResource(R.string.wifi_detail_band), ap.band.displayName)
            DetailRow(stringResource(R.string.wifi_detail_channel), "${ap.channel}  (${ap.frequency} MHz)")
            DetailRow(stringResource(R.string.wifi_detail_width), "${ap.channelWidthMhz} MHz")
            DetailRow(stringResource(R.string.wifi_detail_standard), "${ap.standard.generationLabel}  (${ap.standard.protocolName})")
            DetailRow(stringResource(R.string.wifi_detail_max_speed), maxRateText(ap))
            if (ap.vendor.isNotBlank()) DetailRow(stringResource(R.string.wifi_detail_vendor), ap.vendor)
            if (ap.centerFrequency0 != 0) {
                DetailRow(
                    stringResource(R.string.wifi_detail_center_freq_0),
                    stringResource(R.string.wifi_center_freq_value, ap.centerFrequency0),
                )
            }
            if (ap.centerFrequency1 != 0) {
                DetailRow(
                    stringResource(R.string.wifi_detail_center_freq_1),
                    stringResource(R.string.wifi_center_freq_80_80_value, ap.centerFrequency1),
                )
            }

            HorizontalDivider()

            DetailSectionHeader(stringResource(R.string.wifi_detail_security))
            WifiSecurityIndicator(ap.security)
            val tokens =
                ap.capabilities
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split("][")
                    .filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) { tokens.forEach { token -> ApBadge(token) } }
            }
        }
    }
}

@Composable
internal fun WifiSecurityIndicator(security: WifiSecurity) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val securityIcon =
            when (security) {
                WifiSecurity.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
                WifiSecurity.OPEN -> Icons.Default.LockOpen
                else -> Icons.Default.Lock
            }
        Icon(
            securityIcon,
            if (security == WifiSecurity.UNKNOWN) stringResource(R.string.wifi_security_unknown) else null,
            Modifier.size(18.dp).then(
                if (security == WifiSecurity.UNKNOWN) {
                    Modifier.testTag(WifiScreenTestTags.UNKNOWN_SECURITY_ICON)
                } else {
                    Modifier
                },
            ),
            tint =
                when (security) {
                    WifiSecurity.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    WifiSecurity.OPEN -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
        )
        Text(
            security.displayName,
            Modifier.then(
                if (security == WifiSecurity.UNKNOWN) {
                    Modifier.testTag(WifiScreenTestTags.UNKNOWN_SECURITY_LABEL)
                } else {
                    Modifier
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ── Detail section helpers ────────────────────────────────────────────────────

@Composable private fun maxRateText(ap: WifiAccessPoint): String {
    val ceiling = ap.standard.phyRateCeiling(ap.channelWidthMhz)
    val width = ceiling.widthMhz
    val streams = ceiling.streams
    return when {
        width == null -> stringResource(R.string.wifi_max_rate_ceiling, ceiling.label)
        streams == null -> stringResource(R.string.wifi_max_rate_at_width, ceiling.label, width)
        else -> stringResource(R.string.wifi_max_rate_at_width_streams, ceiling.label, width, streams)
    }
}

@Composable private fun DetailSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}
