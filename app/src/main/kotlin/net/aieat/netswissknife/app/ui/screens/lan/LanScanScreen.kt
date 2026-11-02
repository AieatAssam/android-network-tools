package net.aieat.netswissknife.app.ui.screens.lan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.format.humanDurationText
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.components.ToolStopButton
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.MacSource
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
// collectAsState replaced by collectAsStateWithLifecycle below

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScreen(
    viewModel: LanScanViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    // Rotation also stops and disposes this screen. The Activity reports it through
    // isChangingConfigurations, and the scan must keep running across that recreation.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.onLifecyclePause(configurationChange = activity?.isChangingConfigurations == true)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLifecyclePause(configurationChange = activity?.isChangingConfigurations == true)
        }
    }

    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester()
    LaunchedEffect(Unit) { requestLocalNetworkPermission() }
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is LanNavEvent.NavigateToPorts -> {
                    ToolHost.parse(event.host)?.let { host ->
                        onNavigate(
                            NavRoutes.Ports.createRoute(
                                ToolIntent(
                                    destination = ToolDestination.HostTarget(HostTool.PORTS, host),
                                    source = ToolSource.LAN,
                                ),
                            ),
                        )
                    }
                }

                is LanNavEvent.NavigateToPing -> {
                    ToolHost.parse(event.host)?.let { host ->
                        onNavigate(
                            NavRoutes.Ping.createRoute(
                                ToolIntent(
                                    destination = ToolDestination.HostTarget(HostTool.PING, host),
                                    source = ToolSource.LAN,
                                ),
                            ),
                        )
                    }
                }

                is LanNavEvent.NavigateToHttp -> {
                    val host = ToolHost.parse(event.host)
                    val port = ToolPort.parse(event.port)
                    if (host != null && port != null) {
                        onNavigate(
                            NavRoutes.HttpProbe.createRoute(
                                ToolIntent(
                                    destination = ToolDestination.HostTarget(HostTool.HTTP, host, port),
                                    source = ToolSource.LAN,
                                ),
                            ),
                        )
                    }
                }

                is LanNavEvent.NavigateToTls -> {
                    val host = ToolHost.parse(event.host)
                    val port = ToolPort.parse(event.port)
                    if (host != null && port != null) {
                        onNavigate(
                            NavRoutes.TlsInspector.createRoute(
                                ToolIntent(
                                    destination = ToolDestination.HostTarget(HostTool.TLS, host, port),
                                    source = ToolSource.LAN,
                                ),
                            ),
                        )
                    }
                }

                is LanNavEvent.NavigateToWakeOnLan -> {
                    onNavigate(
                        NavRoutes.WakeOnLan.createRoute(
                            ToolIntent(
                                destination = ToolDestination.WakeOnLan(event.mac),
                                source = ToolSource.LAN,
                            ),
                        ),
                    )
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val subnet by viewModel.subnet.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val concurrency by viewModel.concurrency.collectAsStateWithLifecycle()
    val isSubnetLoading by viewModel.isSubnetLoading.collectAsStateWithLifecycle()
    val subnetNarrowedFrom by viewModel.subnetNarrowedFrom.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val hostSortOrder by viewModel.hostSortOrder.collectAsStateWithLifecycle()
    val recentSubnets by viewModel.recentSubnets.collectAsStateWithLifecycle()

    // Back closes an open host row first, so the scan results stay on screen.
    val expandedHostIp =
        when (val state = uiState) {
            is LanScanUiState.Scanning -> state.expandedHostIp
            is LanScanUiState.Finished -> state.expandedHostIp
            is LanScanUiState.Canceled -> state.expandedHostIp
            else -> null
        }
    BackHandler(enabled = expandedHostIp != null) { viewModel.collapseExpandedHost() }

    val announcementPhase =
        when (val state = uiState) {
            LanScanUiState.Idle -> {
                null
            }

            is LanScanUiState.Scanning, is LanScanUiState.Canceling -> {
                ToolAnnouncementPhase.RUNNING
            }

            is LanScanUiState.Canceled -> {
                ToolAnnouncementPhase.PARTIAL
            }

            is LanScanUiState.Finished -> {
                if (state.partial) {
                    ToolAnnouncementPhase.PARTIAL
                } else {
                    ToolAnnouncementPhase.FINISHED
                }
            }

            is LanScanUiState.Error -> {
                ToolAnnouncementPhase.ERROR
            }
        }
    ToolStateAnnouncer(stringResource(R.string.help_lan_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val isRefreshing = uiState is LanScanUiState.Scanning || uiState is LanScanUiState.Canceling
    var editSettings by rememberSaveable { mutableStateOf(false) }
    var activeFilter by rememberSaveable { mutableStateOf(HostFilter.All) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) editSettings = false
    }

    val listState = rememberLazyListState()
    val resultView by viewModel.resultView.collectAsStateWithLifecycle()
    val completedSummary =
        when (val state = uiState) {
            is LanScanUiState.Finished -> state.summary
            is LanScanUiState.Canceled -> state.summary
            else -> null
        }
    val completedExpandedIp =
        when (val state = uiState) {
            is LanScanUiState.Finished -> state.expandedHostIp
            is LanScanUiState.Canceled -> state.expandedHostIp
            else -> null
        }
    // A map tap expands the device and switches to the list. The scroll waits for the rows to compose,
    // so it runs in an effect after that switch. Expanding never collapses.
    var pendingRevealIp by remember { mutableStateOf<String?>(null) }
    val revealHost: (String) -> Unit = { ip ->
        pendingRevealIp = ip
        viewModel.onRevealHost(ip)
    }
    LaunchedEffect(pendingRevealIp, resultView, completedSummary, searchQuery, activeFilter, hostSortOrder) {
        val ip = pendingRevealIp ?: return@LaunchedEffect
        if (resultView == LanResultView.LIST) {
            completedSummary?.let { summary ->
                val index =
                    lanVisibleHosts(summary.hosts, searchQuery, activeFilter, hostSortOrder)
                        .indexOfFirst { it.ip == ip }
                if (index >= 0) listState.animateScrollToItem(LAN_HOST_ITEMS_FIRST_INDEX + index)
            }
            pendingRevealIp = null
        }
    }

    // No pull-to-refresh here: a swipe at the top used to restart the scan and discard the
    // results without any confirmation. Re-scan is an explicit action on the results.
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { it / 6 },
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("lan_content"),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "lan_header") { LanHeaderCard(onHelpClick = { showHelp = true }) }
                item(key = "lan_network_status") {
                    NetworkStatusBanner(
                        status = networkStatus,
                        scope = NetworkStatusScope.LOCAL_NETWORK,
                        permissionDenied =
                            (uiState as? LanScanUiState.Error)?.networkErrorKind ==
                                NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
                        onGrantPermission = requestLocalNetworkPermission,
                    )
                }
                item(key = "lan_configuration") {
                    if (!editSettings && uiState !is LanScanUiState.Idle && uiState !is LanScanUiState.Error) {
                        LanScanSettingsSummary(
                            title = if (isRefreshing) subnet else stringResource(R.string.lan_config_title),
                            timeoutMs = timeoutMs,
                            concurrency = concurrency,
                            canExpand = true,
                            onExpand = { editSettings = true },
                        )
                    } else {
                        LanInputCard(
                            subnet = subnet,
                            timeoutMs = timeoutMs,
                            concurrency = concurrency,
                            isSubnetLoading = isSubnetLoading,
                            subnetNarrowedFrom = subnetNarrowedFrom,
                            isScanning = uiState is LanScanUiState.Scanning,
                            isCanceling = uiState is LanScanUiState.Canceling,
                            recentSubnets = recentSubnets,
                            onSubnetChange = viewModel::onSubnetChange,
                            onTimeoutChange = viewModel::onTimeoutChange,
                            onConcurrencyChange = viewModel::onConcurrencyChange,
                            onRefreshSubnet = viewModel::refreshSubnet,
                            onStartScan = viewModel::startScan,
                            onStopScan = viewModel::onStopScan,
                            onRemoveRecentSubnet = viewModel::removeRecentSubnet,
                            onClearRecentSubnets = viewModel::clearRecentSubnets,
                        )
                    }
                }
                when (val state = uiState) {
                    LanScanUiState.Idle -> {
                        item(key = "lan_idle") { LanIdleContent() }
                    }

                    is LanScanUiState.Scanning -> {
                        item(key = "lan_progress") { LanScanningContent(state) }
                        if (state.hosts.isNotEmpty()) {
                            item(key = "lan_live_hosts_header") {
                                Text(
                                    text = stringResource(R.string.lan_live_hosts_header),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            items(
                                items = sortLanHosts(state.hosts, hostSortOrder),
                                key = { "host-${it.ip}" },
                            ) { host ->
                                HostCard(
                                    host = host,
                                    expanded = host.ip == state.expandedHostIp,
                                    onClick = { viewModel.onToggleHostExpanded(host.ip) },
                                    macResolutionSupported = true,
                                    onScanPorts = viewModel::onScanPorts,
                                    onPingHost = viewModel::onPingHost,
                                    onProbeHttp = viewModel::onProbeHttp,
                                    onInspectTls = viewModel::onInspectTls,
                                    onWakeDevice = viewModel::onWakeDevice,
                                )
                            }
                        }
                    }

                    is LanScanUiState.Canceling -> {
                        item(key = "lan_canceling") {
                            LanCancelingContent()
                        }
                    }

                    is LanScanUiState.Canceled -> {
                        item(key = "lan_canceled") {
                            LanFinishedContent(
                                summary = state.summary,
                                showDiagnostics = state.showDiagnostics,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                onFilterChange = { activeFilter = it },
                                sortOrder = hostSortOrder,
                                onSortOrderChange = viewModel::onHostSortOrderChange,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onClear = viewModel::onClear,
                                onRescan = viewModel::startScan,
                                onToggleDiagnostics = viewModel::onToggleDiagnostics,
                                onRevealHost = revealHost,
                                resultView = resultView,
                                onResultViewChange = viewModel::onResultViewChange,
                                title = stringResource(R.string.lan_scan_canceled_title),
                                subtitle = stringResource(R.string.lan_scan_canceled_subtitle),
                                isCanceled = true,
                            )
                        }
                    }

                    is LanScanUiState.Finished -> {
                        item(key = "lan_finished") {
                            LanFinishedContent(
                                summary = state.summary,
                                showDiagnostics = state.showDiagnostics,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                onFilterChange = { activeFilter = it },
                                sortOrder = hostSortOrder,
                                onSortOrderChange = viewModel::onHostSortOrderChange,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onClear = viewModel::onClear,
                                onRescan = viewModel::startScan,
                                onToggleDiagnostics = viewModel::onToggleDiagnostics,
                                onRevealHost = revealHost,
                                resultView = resultView,
                                onResultViewChange = viewModel::onResultViewChange,
                                title =
                                    stringResource(
                                        if (state.partial) {
                                            R.string.lan_scan_paused_title
                                        } else {
                                            R.string.lan_scan_complete_title
                                        },
                                    ),
                                subtitle =
                                    when {
                                        state.timeLimitReached -> stringResource(R.string.lan_scan_time_limit_subtitle)
                                        state.partial -> stringResource(R.string.lan_scan_paused_subtitle)
                                        else -> null
                                    },
                            )
                        }
                    }

                    is LanScanUiState.Error -> {
                        item(key = "lan_error") {
                            LanErrorContent(
                                message =
                                    if (state.isBudgetLimit) {
                                        stringResource(R.string.lan_scan_estimate_too_large)
                                    } else {
                                        state.message
                                    },
                                onRetry = viewModel::startScan,
                                onClear = viewModel::onClear,
                                suggestedSubnet = state.suggestedSubnet,
                                onScanSuggested = viewModel::scanSubnet,
                            )
                        }
                    }
                }
                // The map and the list show the same filtered hosts, so only the selected view is composed.
                completedSummary?.takeIf { resultView == LanResultView.LIST }?.let { summary ->
                    items(
                        items = lanVisibleHosts(summary.hosts, searchQuery, activeFilter, hostSortOrder),
                        key = { "host-${it.ip}" },
                    ) { host ->
                        HostCard(
                            host = host,
                            expanded = host.ip == completedExpandedIp,
                            onClick = { viewModel.onToggleHostExpanded(host.ip) },
                            macResolutionSupported = summary.macResolutionSupported,
                            onScanPorts = viewModel::onScanPorts,
                            onPingHost = viewModel::onPingHost,
                            onProbeHttp = viewModel::onProbeHttp,
                            onInspectTls = viewModel::onInspectTls,
                            onWakeDevice = viewModel::onWakeDevice,
                        )
                    }
                }
            }
        }
    } // end Box

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_lan_title),
            conceptHeading = stringResource(R.string.help_lan_concept_heading),
            conceptBody = stringResource(R.string.help_lan_concept_body),
            sections =
                listOf(
                    HelpSection(stringResource(R.string.help_lan_what_heading), stringResource(R.string.help_lan_what_body)),
                    HelpSection(
                        heading = stringResource(R.string.help_lan_params_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_lan_params_bullets).toList(),
                    ),
                    HelpSection(
                        heading = stringResource(R.string.help_lan_results_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_lan_results_bullets).toList(),
                    ),
                ),
            onDismiss = { showHelp = false },
        )
    }
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun LanHeaderCard(onHelpClick: () -> Unit) {
    ToolHeroHeader(
        title = stringResource(R.string.lan_screen_title),
        subtitle = stringResource(R.string.lan_screen_subtitle),
        icon = Icons.Default.Lan,
        onHelpClick = onHelpClick,
    )
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun LanInputCard(
    subnet: String,
    timeoutMs: Int,
    concurrency: Int,
    isSubnetLoading: Boolean,
    subnetNarrowedFrom: String?,
    isScanning: Boolean,
    isCanceling: Boolean,
    recentSubnets: List<String>,
    onSubnetChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onRefreshSubnet: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRemoveRecentSubnet: (String) -> Unit,
    onClearRecentSubnets: () -> Unit,
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
                    if (subnet.isNotEmpty()) {
                        IconButton(onClick = { onSubnetChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    } else if (isSubnetLoading) {
                        val loadingCd = stringResource(R.string.a11y_loading)
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp)
                                    .semantics { contentDescription = loadingCd },
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
                },
                supportingText =
                    subnetNarrowedFrom?.let { network ->
                        { Text(stringResource(R.string.lan_subnet_narrowed_hint, network)) }
                    },
                singleLine = true,
                enabled = !isScanning && !isCanceling,
                modifier = Modifier.fillMaxWidth(),
            )

            RecentHostsRow(
                recentHosts = recentSubnets,
                onHostSelected = onSubnetChange,
                onRemoveHost = onRemoveRecentSubnet,
                onClearAll = onClearRecentSubnets,
                selectionEnabled = !isScanning && !isCanceling,
                actionsEnabled = !isScanning && !isCanceling,
                removeDescription = stringResource(R.string.lan_recent_remove),
                clearDescription = stringResource(R.string.lan_recent_clear),
            )

            // Timeout slider
            LabeledSlider(
                label = stringResource(R.string.lan_timeout_label, timeoutMs),
                value = timeoutMs.toFloat(),
                onValueChange = { onTimeoutChange(it.toInt()) },
                valueRange = 100f..10_000f,
                steps = 0,
                enabled = !isScanning && !isCanceling,
            )

            // Concurrency slider
            LabeledSlider(
                label = stringResource(R.string.lan_concurrency_label, concurrency),
                value = concurrency.toFloat(),
                onValueChange = { onConcurrencyChange(it.toInt()) },
                valueRange = 1f..500f,
                steps = 0,
                enabled = !isScanning && !isCanceling,
            )

            // Action button
            if (isCanceling) {
                FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lan_canceling_title))
                }
            } else if (isScanning) {
                ToolStopButton(
                    text = stringResource(R.string.lan_stop_button),
                    onClick = onStopScan,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(
                    onClick = hapticAction(onStartScan),
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
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanScanSettingsSummary(
    title: String,
    timeoutMs: Int,
    concurrency: Int,
    canExpand: Boolean,
    onExpand: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LanFactRow(
                    facts =
                        listOf(
                            stringResource(R.string.lan_timeout_label, timeoutMs),
                            stringResource(R.string.lan_concurrency_label, concurrency),
                        ),
                )
            }
            if (canExpand) {
                TextButton(onClick = onExpand) {
                    Text(stringResource(R.string.lan_edit_settings))
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
            modifier =
                Modifier
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
                        text =
                            if (state.totalCount > 0) {
                                stringResource(R.string.lan_progress_format, state.scannedCount, state.totalCount)
                            } else {
                                "…"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val animProgress by animateFloatAsState(
                    targetValue = state.progress,
                    animationSpec = AppMotion.effect(300),
                    label = "scan_progress",
                )
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = pluralStringResource(R.plurals.lan_hosts_found_format, state.hosts.size, state.hosts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanCancelingContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.lan_canceling_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PulsingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_alpha",
    )
    Box(
        modifier =
            Modifier
                .size(16.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

// ── Finished ──────────────────────────────────────────────────────────────────

private enum class HostFilter { All, Gateway, HasPorts }

internal fun sortLanHosts(
    hosts: List<LanHost>,
    order: LanHostSortOrder,
): List<LanHost> =
    when (order) {
        LanHostSortOrder.IP -> {
            hosts.sortedBy { ipSortKey(it.ip) }
        }

        LanHostSortOrder.LATENCY -> {
            // Hosts without an ICMP reply have no latency, so they sort after every measured host.
            hosts.sortedWith(
                compareBy<LanHost> { it.pingTimeMs == null }
                    .thenBy { it.pingTimeMs ?: 0L }
                    .thenBy { ipSortKey(it.ip) },
            )
        }

        LanHostSortOrder.HOSTNAME -> {
            hosts.sortedWith(compareBy<LanHost> { it.hostname.orEmpty().lowercase() }.thenBy { ipSortKey(it.ip) })
        }

        LanHostSortOrder.VENDOR -> {
            hosts.sortedWith(compareBy<LanHost> { it.vendor.orEmpty().lowercase() }.thenBy { ipSortKey(it.ip) })
        }

        LanHostSortOrder.OPEN_PORTS -> {
            hosts.sortedWith(compareByDescending<LanHost> { it.openPorts.size }.thenBy { ipSortKey(it.ip) })
        }
    }

/** Sort key for ascending numeric IPv4 order (dotted-decimal string sort puts .2 after .10). */
private fun ipSortKey(ip: String): Long {
    return ip.split('.').fold(0L) { acc, part -> acc * 256L + (part.toLongOrNull() ?: 0L) }
}

@Composable
private fun HostFilterChips(
    activeFilter: HostFilter,
    onFilterChange: (HostFilter) -> Unit,
) {
    val labels =
        mapOf(
            HostFilter.All to R.string.lan_filter_all,
            HostFilter.Gateway to R.string.lan_filter_gateway,
            HostFilter.HasPorts to R.string.lan_filter_has_ports,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (filter, labelRes) ->
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

private val hostSortOptions =
    listOf(
        LanHostSortOrder.IP to R.string.lan_sort_ip,
        LanHostSortOrder.LATENCY to R.string.lan_sort_latency,
        LanHostSortOrder.HOSTNAME to R.string.lan_sort_hostname,
        LanHostSortOrder.VENDOR to R.string.lan_sort_vendor,
        LanHostSortOrder.OPEN_PORTS to R.string.lan_sort_open_ports,
    )

/** Sort control shown as a labelled menu so it cannot be mistaken for the filter chips above it. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostSortMenu(
    activeSort: LanHostSortOrder,
    onSortChange: (LanHostSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeLabel = hostSortOptions.first { it.first == activeSort }.second
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.lan_sort_by),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(activeLabel))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                hostSortOptions.forEach { (order, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = {
                            if (order == activeSort) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            onSortChange(order)
                        },
                    )
                }
            }
        }
    }
}

/** Visible hosts: filtered by the search and filter, then sorted. The list and the map both use it. */
private fun lanVisibleHosts(
    hosts: List<LanHost>,
    searchQuery: String,
    activeFilter: HostFilter,
    order: LanHostSortOrder,
): List<LanHost> = sortLanHosts(filterLanHosts(hosts, searchQuery, activeFilter), order)

private fun filterLanHosts(
    hosts: List<LanHost>,
    searchQuery: String,
    activeFilter: HostFilter,
): List<LanHost> =
    hosts.filter { host ->
        val passesFilter =
            when (activeFilter) {
                HostFilter.All -> true
                HostFilter.Gateway -> host.isGateway
                HostFilter.HasPorts -> host.openPorts.isNotEmpty()
            }
        val passesSearch =
            searchQuery.isBlank() ||
                listOfNotNull(host.ip, host.hostname, host.vendor, host.macAddress)
                    .any { it.contains(searchQuery, ignoreCase = true) }
        passesFilter && passesSearch
    }

private const val LAN_HOST_ITEMS_FIRST_INDEX = 4
private const val LAN_MAP_NODE_WIDTH_DP = 72

private sealed interface LanMapCell {
    data class Device(
        val host: LanHost,
    ) : LanMapCell

    data class More(
        val count: Int,
    ) : LanMapCell
}

@Composable
private fun LanFinishedContent(
    summary: LanScanSummary,
    showDiagnostics: Boolean,
    searchQuery: String,
    activeFilter: HostFilter,
    onFilterChange: (HostFilter) -> Unit,
    sortOrder: LanHostSortOrder,
    onSortOrderChange: (LanHostSortOrder) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRescan: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onRevealHost: (String) -> Unit,
    resultView: LanResultView,
    onResultViewChange: (LanResultView) -> Unit,
    title: String = stringResource(R.string.lan_scan_complete_title),
    subtitle: String? = null,
    isCanceled: Boolean = false,
) {
    val visibleHosts =
        remember(summary.hosts, searchQuery, activeFilter, sortOrder) {
            lanVisibleHosts(summary.hosts, searchQuery, activeFilter, sortOrder)
        }
    val filtered = searchQuery.isNotBlank() || activeFilter != HostFilter.All

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var entered by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        AnimatedVisibility(visible = entered, enter = fadeIn() + slideInVertically()) {
            LanSummaryCard(
                summary = summary,
                title = title,
                subtitle = subtitle,
                isCanceled = isCanceled,
                onShowUncertain = onToggleDiagnostics,
                onRescan = onRescan,
                onClear = onClear,
            )
        }

        if (summary.hosts.isEmpty()) {
            LanNoHostsCard()
        } else {
            ToolTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.lan_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HostFilterChips(activeFilter = activeFilter, onFilterChange = onFilterChange)
            LanDevicesHeader(
                shown = visibleHosts.size,
                total = summary.hosts.size,
                filtered = filtered,
                trailing = { LanResultViewToggle(selected = resultView, onSelect = onResultViewChange) },
            )
            HostSortMenu(activeSort = sortOrder, onSortChange = onSortOrderChange)

            if (visibleHosts.isEmpty()) {
                LanNoMatchCard(
                    searchQuery = searchQuery,
                    activeFilter = activeFilter,
                    onSearchQueryChange = onSearchQueryChange,
                    onFilterChange = onFilterChange,
                )
            } else if (resultView == LanResultView.MAP) {
                NetworkTopologyCard(hosts = visibleHosts, onNodeClick = onRevealHost)
            }
        }
    }

    if (showDiagnostics && summary.uncertainCount > 0) {
        LanUncertainProbesSheet(summary = summary, onDismiss = onToggleDiagnostics)
    }
}

/**
 * Status, one headline count, one meta line, the uncertain-probe count and the actions.
 * Per-host data is not repeated here; the device list and the map carry it.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanSummaryCard(
    summary: LanScanSummary,
    title: String,
    subtitle: String?,
    isCanceled: Boolean,
    onShowUncertain: () -> Unit,
    onRescan: () -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanSummaryStatus(title = title, isCanceled = isCanceled)
            LanSummaryHeadline(summary = summary, subtitle = subtitle)
            if (summary.uncertainCount > 0) {
                LanUncertainProbesButton(count = summary.uncertainCount, onClick = onShowUncertain)
            }
            // Rescan is the single primary action; Share and Clear wrap onto a
            // second line instead of squeezing into equal-width buttons.
            Button(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lan_rescan_button))
            }
            LanSummaryActions(summary = summary, onClear = onClear)
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanSummaryStatus(
    title: String,
    isCanceled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (isCanceled) Icons.Default.Stop else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanSummaryHeadline(
    summary: LanScanSummary,
    subtitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.lan_summary_headline_format,
                    summary.aliveHosts,
                    summary.aliveHosts,
                ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LanFactRow(
            facts =
                listOf(
                    summary.subnet,
                    pluralStringResource(R.plurals.lan_addresses_count, summary.totalScanned, summary.totalScanned),
                    humanDurationText(summary.scanDurationMs),
                ),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Facts on one line that wrap as whole items, so no separator is left at the start of a line. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanFactRow(facts: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        facts.forEach { fact ->
            Text(
                text = fact,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanUncertainProbesButton(
    count: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.testTag("lan_uncertain_probes"),
    ) {
        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluralStringResource(R.plurals.lan_uncertain_probes_count, count, count))
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanSummaryActions(
    summary: LanScanSummary,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.share_subject_lan, summary.subnet)
    val shareText = buildLanShareText(summary, humanDurationText(summary.scanDurationMs))
    // Same Copy / Share row as every other tool (PHB-12); Clear leads it as the row's tool action.
    ResultActionsRow(
        copyText = shareText,
        onShare = { context.shareText(text = shareText, subject = shareSubject) },
        extraActions = {
            TextButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.lan_clear_button))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanUncertainProbesSheet(
    summary: LanScanSummary,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.lan_uncertain_probes_count,
                        summary.uncertainCount,
                        summary.uncertainCount,
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.lan_uncertain_probes_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Bounded: the list scrolls inside the sheet instead of growing the summary card.
            LazyColumn(
                modifier = Modifier.heightIn(max = LAN_UNCERTAIN_LIST_MAX_HEIGHT_DP.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(summary.uncertainHosts) { diagnostic ->
                    Text(
                        text =
                            stringResource(
                                R.string.lan_diagnostic_entry,
                                diagnostic.ip,
                                stringResource(diagnosticReasonLabel(diagnostic.reason)),
                                diagnostic.port
                                    ?.let { stringResource(R.string.lan_diagnostic_port, it) }
                                    .orEmpty(),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.uncertainCount > summary.uncertainHosts.size) {
                    item {
                        Text(
                            text =
                                stringResource(
                                    R.string.lan_diagnostics_truncated,
                                    summary.uncertainHosts.size,
                                    summary.uncertainCount,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val LAN_UNCERTAIN_LIST_MAX_HEIGHT_DP = 300

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanNoHostsCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.lan_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanNoMatchCard(
    searchQuery: String,
    activeFilter: HostFilter,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (HostFilter) -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    stringResource(
                        when {
                            searchQuery.isNotBlank() && activeFilter != HostFilter.All -> {
                                R.string.lan_no_search_filter_results
                            }

                            searchQuery.isNotBlank() -> {
                                R.string.lan_no_search_results
                            }

                            else -> {
                                R.string.lan_no_filter_results
                            }
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = {
                    onSearchQueryChange("")
                    onFilterChange(HostFilter.All)
                },
            ) {
                Text(stringResource(R.string.lan_show_all_devices))
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanDevicesHeader(
    shown: Int,
    total: Int,
    filtered: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.lan_devices_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered) {
                Text(
                    text = stringResource(R.string.lan_devices_filtered_count, shown, total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

/** List or Map over the same filtered devices. One view is on screen at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LanResultViewToggle(
    selected: LanResultView,
    onSelect: (LanResultView) -> Unit,
) {
    val views = LanResultView.entries
    SingleChoiceSegmentedButtonRow {
        views.forEachIndexed { index, view ->
            SegmentedButton(
                selected = selected == view,
                onClick = { onSelect(view) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = views.size),
                label = {
                    Text(
                        stringResource(
                            if (view == LanResultView.LIST) R.string.lan_view_list else R.string.lan_view_map,
                        ),
                    )
                },
            )
        }
    }
}

private fun diagnosticReasonLabel(reason: LanScanDiagnosticReason): Int =
    when (reason) {
        LanScanDiagnosticReason.TCP_REFUSED -> R.string.lan_diagnostic_tcp_refused
        LanScanDiagnosticReason.TCP_TIMED_OUT -> R.string.lan_diagnostic_tcp_timed_out
        LanScanDiagnosticReason.TCP_UNREACHABLE -> R.string.lan_diagnostic_tcp_unreachable
        LanScanDiagnosticReason.TCP_POLICY_DENIED -> R.string.lan_diagnostic_tcp_policy_denied
        LanScanDiagnosticReason.TCP_UNKNOWN_FAILURE -> R.string.lan_diagnostic_tcp_unknown
    }

// ── Network map ──────────────────────────────────────────────────────────────

/**
 * Device nodes on a width-adaptive grid. A node shows its icon and a caption (hostname or last octet);
 * tapping it reveals that device in the list. Vendor text is left to the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NetworkTopologyCard(
    hosts: List<LanHost>,
    onNodeClick: (String) -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val preview = lanMapVisibleNodes(items = hosts, showAll = showAll)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.lan_topology_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val cells =
                preview.visible.map { LanMapCell.Device(it) } +
                    if (preview.hiddenCount > 0) listOf(LanMapCell.More(preview.hiddenCount)) else emptyList()
            // Columns come from the card width, and each cell takes an equal share of it, so the grid
            // reaches the right edge at any width instead of stopping at a fixed column count.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns =
                    lanMapColumnCount(
                        availableWidthDp = maxWidth.value,
                        nodeWidthDp = LAN_MAP_NODE_WIDTH_DP.toFloat(),
                        spacingDp = LAN_MAP_SPACING_DP.toFloat(),
                    )
                Column(verticalArrangement = Arrangement.spacedBy(LAN_MAP_SPACING_DP.dp)) {
                    cells.chunked(columns).forEach { rowCells ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(LAN_MAP_SPACING_DP.dp),
                        ) {
                            rowCells.forEach { cell ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    when (cell) {
                                        is LanMapCell.Device -> {
                                            TopologyNode(
                                                icon = hostIcon(cell.host),
                                                caption = lanMapNodeCaption(cell.host.hostname, cell.host.ip),
                                                ip = cell.host.ip,
                                                isGateway = cell.host.isGateway,
                                                onClick = { onNodeClick(cell.host.ip) },
                                            )
                                        }

                                        is LanMapCell.More -> {
                                            MoreDevicesNode(count = cell.count, onClick = { showAll = true })
                                        }
                                    }
                                }
                            }
                            repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun TopologyNode(
    icon: ImageVector,
    caption: String,
    ip: String,
    isGateway: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .width(LAN_MAP_NODE_WIDTH_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.lan_map_node_action, ip),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGateway) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (isGateway) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun MoreDevicesNode(
    count: Int,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .width(LAN_MAP_NODE_WIDTH_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.lan_map_more_action),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.lan_map_more_count, count),
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostCard(
    host: LanHost,
    expanded: Boolean,
    onClick: () -> Unit,
    macResolutionSupported: Boolean,
    onScanPorts: (String) -> Unit,
    onPingHost: (String) -> Unit,
    onProbeHttp: (String, Int) -> Unit,
    onInspectTls: (String, Int) -> Unit,
    onWakeDevice: (String) -> Unit,
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.lan_copied)
    val copyActionLabel = stringResource(R.string.lan_copy_host_action)
    var showCopyMenu by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue =
            if (expanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        animationSpec = AppMotion.effect(250),
        label = "host_card_color",
    )

    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                .testTag("lan_host_card_${host.ip}")
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = copyActionLabel,
                    onLongClick = { showCopyMenu = true },
                ),
    ) {
        Box(modifier = Modifier.background(containerColor)) {
            HostCardBody(
                host,
                expanded,
                macResolutionSupported,
                onScanPorts,
                onPingHost,
                onProbeHttp,
                onInspectTls,
                onWakeDevice,
            )
            HostCopyMenu(
                host = host,
                expanded = showCopyMenu,
                copiedMessage = copiedMessage,
                onDismiss = { showCopyMenu = false },
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostCardBody(
    host: LanHost,
    expanded: Boolean,
    macResolutionSupported: Boolean,
    onScanPorts: (String) -> Unit,
    onPingHost: (String) -> Unit,
    onProbeHttp: (String, Int) -> Unit,
    onInspectTls: (String, Int) -> Unit,
    onWakeDevice: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HostCompactHeader(host, expanded)
        // The expanded panel lists hostname, MAC and vendor, so the identity line is shown only when collapsed.
        if (!expanded) HostIdentityLine(host)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(AppMotion.enter(250)) + fadeIn(AppMotion.enter(250)),
            exit = shrinkVertically(AppMotion.exit(200)) + fadeOut(AppMotion.exit(200)),
        ) {
            HostDetailPanel(
                host = host,
                macResolutionSupported = macResolutionSupported,
                onScanPorts = onScanPorts,
                onPingHost = onPingHost,
                onProbeHttp = onProbeHttp,
                onInspectTls = onInspectTls,
                onWakeDevice = onWakeDevice,
            )
        }
    }
}

/**
 * IP, gateway tag, latency and the expand control. The port count shows only while collapsed,
 * because the expanded panel lists the ports itself.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")
private fun HostCompactHeader(
    host: LanHost,
    expanded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        // The left side fills the remaining width, so a plain SpaceBetween left no gap and the
        // gateway label touched the latency text. An explicit gap keeps them apart.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = hostIcon(host),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = host.ip,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            if (host.isGateway) {
                Text(
                    stringResource(R.string.lan_gateway_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val pingMs = host.pingTimeMs
            Text(
                text =
                    if (pingMs != null) {
                        stringResource(R.string.lan_ping_status_format, pingMs)
                    } else {
                        stringResource(R.string.lan_ping_unavailable)
                    },
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (pingMs != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (!expanded && host.openPorts.isNotEmpty()) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.lan_open_ports_count,
                            host.openPorts.size,
                            host.openPorts.size,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostIdentityLine(host: LanHost) {
    val identity =
        listOfNotNull(host.hostname, host.vendor, host.macAddress)
            .distinct()
            .ifEmpty { listOf(stringResource(R.string.lan_unknown_device)) }
            .joinToString(" · ")
    Text(
        identity,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostCopyMenu(
    host: LanHost,
    expanded: Boolean,
    copiedMessage: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        HostCopyMenuItem(R.string.lan_copy_ip, "IP", host.ip, copiedMessage, onDismiss, context)
        host.macAddress?.let { mac ->
            HostCopyMenuItem(R.string.lan_copy_mac, "MAC", mac, copiedMessage, onDismiss, context)
        }
        host.hostname?.let { hostname ->
            HostCopyMenuItem(R.string.lan_copy_hostname, "Hostname", hostname, copiedMessage, onDismiss, context)
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun HostCopyMenuItem(
    labelRes: Int,
    clipLabel: String,
    value: String,
    copiedMessage: String,
    onDismiss: () -> Unit,
    context: android.content.Context,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = {
            onDismiss()
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, value))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HostDetailPanel(
    host: LanHost,
    macResolutionSupported: Boolean,
    onScanPorts: (String) -> Unit,
    onPingHost: (String) -> Unit,
    onProbeHttp: (String, Int) -> Unit,
    onInspectTls: (String, Int) -> Unit,
    onWakeDevice: (String) -> Unit,
) {
    // IP and latency are already in the header, so this panel holds only what the header does not show.
    val wakeMac = host.macAddress?.takeIf { host.macSource == MacSource.ARP }?.let(ToolMacAddress::parse)
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

        host.hostname?.let {
            DetailRow(label = stringResource(R.string.lan_detail_hostname), value = it)
        }
        val macAddress = host.macAddress
        if (macAddress != null) {
            DetailRow(label = stringResource(R.string.lan_detail_mac), value = macAddress)
        } else if (!macResolutionSupported) {
            DetailRow(
                label = stringResource(R.string.lan_detail_mac),
                value = stringResource(R.string.lan_mac_unavailable_api29),
            )
        }
        host.vendor?.let {
            DetailRow(label = stringResource(R.string.lan_detail_vendor), value = it)
        }

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

        // One action row: device actions and the port actions share the same wrapping layout.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onPingHost(host.ip) }, modifier = Modifier.testTag("lan_action_ping")) {
                Text(stringResource(R.string.lan_action_ping))
            }
            TextButton(
                onClick = { onScanPorts(host.ip) },
                modifier = Modifier.testTag("lan_action_ports"),
            ) {
                Text(stringResource(R.string.lan_action_scan_ports))
            }
            val httpPort = preferredHttpProbePort(host.openPorts)
            TextButton(
                onClick = { onProbeHttp(host.ip, httpPort) },
                modifier = Modifier.testTag("lan_action_http"),
            ) {
                Text(stringResource(R.string.lan_action_probe_http_port, httpPort))
            }
            host.openPorts
                .distinct()
                .filter(::isLikelyTlsPort)
                .sorted()
                .forEach { port ->
                    TextButton(
                        onClick = { onInspectTls(host.ip, port) },
                        modifier = Modifier.testTag("lan_action_tls_$port"),
                    ) {
                        Text(stringResource(R.string.lan_action_inspect_tls_port, port))
                    }
                }
            if (wakeMac != null) {
                TextButton(
                    onClick = { onWakeDevice(wakeMac.value) },
                    modifier = Modifier.testTag("lan_action_wake_device"),
                ) {
                    Text(stringResource(R.string.lan_action_wake_device))
                }
            }
        }
    }
}

internal fun isLikelyTlsPort(port: Int): Boolean = port in likelyTlsPorts

@Suppress("MagicNumber")
private val likelyTlsPorts =
    setOf(
        443,
        465,
        636,
        853,
        989,
        990,
        992,
        993,
        995,
        5061,
        5223,
        8443,
        8883,
        9443,
    )

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
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
            fontFamily =
                if (label.contains("IP") || label.contains("MAC") || label.contains("Ping")) {
                    FontFamily.Monospace
                } else {
                    FontFamily.Default
                },
            modifier = Modifier.weight(0.6f),
        )
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun LanErrorContent(
    message: String,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    suggestedSubnet: String? = null,
    onScanSuggested: (String) -> Unit = {},
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
            if (suggestedSubnet != null) {
                Button(
                    onClick = { onScanSuggested(suggestedSubnet) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.lan_scan_suggested_button, suggestedSubnet))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolRetryButton(
                    text = stringResource(R.string.lan_retry_button),
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.lan_clear_button))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun hostIcon(host: LanHost): ImageVector =
    when {
        host.isGateway -> Icons.Default.Router
        host.vendor?.contains("Apple", ignoreCase = true) == true -> Icons.Default.Computer
        host.vendor?.contains("Raspberry", ignoreCase = true) == true -> Icons.Default.SmartToy
        host.openPorts.contains(80) || host.openPorts.contains(443) -> Icons.Default.Computer
        host.openPorts.contains(22) -> Icons.Default.Computer
        else -> Icons.Default.DeviceHub
    }

private fun portServiceName(port: Int): String =
    when (port) {
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
