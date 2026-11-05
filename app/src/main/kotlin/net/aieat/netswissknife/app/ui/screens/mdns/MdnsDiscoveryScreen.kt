package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.HeroTitleText
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolStopButton
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.screens.mdnsAnnouncementPhase
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsDiscoveryLimits
import net.aieat.netswissknife.core.network.mdns.MdnsTruncationReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdnsDiscoveryScreen(
    viewModel: MdnsDiscoveryViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester()
    LaunchedEffect(Unit) { requestLocalNetworkPermission() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val announcementPhase = mdnsAnnouncementPhase(uiState)
    ToolStateAnnouncer(stringResource(R.string.help_mdns_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    // The scan has its own status in the hero and list; the pull indicator here would sit over the title (PHB-13).
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { viewModel.startScan() },
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { 40 },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroCard(uiState, onHelpClick = { showHelp = true })

                NetworkStatusBanner(
                    status = networkStatus,
                    scope = NetworkStatusScope.LOCAL_NETWORK,
                    permissionDenied = uiState.networkErrorKind == NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
                    onGrantPermission = requestLocalNetworkPermission,
                )

                if (uiState.truncationReasons.isNotEmpty()) {
                    TruncationBanner(uiState.truncationReasons, scanComplete = uiState.scanComplete)
                }

                if (uiState.networkInterrupted) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.mdns_interrupted_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ControlRow(
                    isScanning = uiState.isScanning,
                    isCanceling = uiState.isCanceling,
                    onScan = { viewModel.startScan() },
                    onStop = { viewModel.stopScan() },
                    onReset = { viewModel.reset() },
                    hasPriorResults = uiState.services.isNotEmpty() || uiState.scanComplete || uiState.scanCanceled,
                )

                AnimatedContent(
                    targetState =
                        when {
                            uiState.error != null -> "error"
                            uiState.services.isEmpty() && uiState.isCanceling -> "canceling_empty"
                            uiState.services.isEmpty() && uiState.scanCanceled -> "canceled_empty"
                            uiState.services.isEmpty() && !uiState.isScanning && !uiState.scanComplete -> "idle"
                            uiState.services.isEmpty() && uiState.isScanning -> "scanning_empty"
                            uiState.services.isEmpty() && uiState.scanComplete -> "empty_done"
                            else -> "results"
                        },
                    transitionSpec = {
                        fadeIn(AppMotion.enter(300)) togetherWith fadeOut(AppMotion.exit(200))
                    },
                    label = "mdns-state",
                    modifier = Modifier.fillMaxWidth(),
                ) { state ->
                    when (state) {
                        "idle" -> {
                            IdleHint()
                        }

                        "canceling_empty" -> {
                            CancelingPlaceholder()
                        }

                        "canceled_empty" -> {
                            CanceledEmptyHint()
                        }

                        "scanning_empty" -> {
                            ScanningPlaceholder()
                        }

                        "empty_done" -> {
                            EmptyResultHint()
                        }

                        "error" -> {
                            ErrorCard(stringResource(uiState.errorReason.messageRes())) { viewModel.reset() }
                        }

                        else -> {
                            ServiceList(
                                servicesByType = uiState.servicesByType,
                                isScanning = uiState.isScanning,
                                isCanceling = uiState.isCanceling,
                                scanCanceled = uiState.scanCanceled,
                                truncationReasons = uiState.truncationReasons,
                                onPingHost = { host ->
                                    ToolHost.parse(host)?.let { validatedHost ->
                                        onNavigate(
                                            NavRoutes.Ping.createRoute(
                                                ToolIntent(
                                                    destination =
                                                        ToolDestination.HostTarget(
                                                            HostTool.PING,
                                                            validatedHost,
                                                        ),
                                                    source = ToolSource.MDNS,
                                                ),
                                            ),
                                        )
                                    }
                                },
                                onHttpProbe = { service ->
                                    mdnsHttpIntent(service)?.let { intent ->
                                        onNavigate(NavRoutes.HttpProbe.createRoute(intent))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    } // end PullToRefreshBox

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_mdns_title),
            conceptHeading = stringResource(R.string.help_mdns_concept_heading),
            conceptBody = stringResource(R.string.help_mdns_concept_body),
            sections =
                listOf(
                    HelpSection(stringResource(R.string.help_mdns_what_heading), stringResource(R.string.help_mdns_what_body)),
                    HelpSection(
                        heading = stringResource(R.string.help_mdns_params_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_mdns_params_bullets).toList(),
                    ),
                    HelpSection(
                        heading = stringResource(R.string.help_mdns_results_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_mdns_results_bullets).toList(),
                    ),
                ),
            onDismiss = { showHelp = false },
        )
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(
    state: MdnsDiscoveryUiState,
    onHelpClick: () -> Unit,
) {
    val gradient =
        Brush.linearGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                ),
        )

    // Once results exist the hero folds to its title and counts, so the service list starts above the fold (V6B-07).
    val compact = state.services.isNotEmpty()
    ElevatedCard(
        shape = AppShapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(gradient)
                    .padding(if (compact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(if (compact) 40.dp else 52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compact) 22.dp else 28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                HeroTitleText(
                    text = stringResource(R.string.mdns_screen_title),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    style =
                        if (compact) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.displaySmall
                        },
                )
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_help),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (!compact) {
                Text(
                    text = stringResource(R.string.mdns_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }

            // Stats badges
            if (state.services.isNotEmpty() || state.isScanning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatBadge(
                        value = state.services.size.toString(),
                        label = stringResource(R.string.mdns_stat_found),
                    )
                    if (state.isScanning) {
                        StatBadge(
                            value = "${state.elapsedMs / 1000}s",
                            label = stringResource(R.string.mdns_stat_elapsed),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/**
 * Informational note shown only when a configured limit was actually reached during the scan.
 * It is neutral (not error-styled) and names each limit, so a complete scan never looks like a failure.
 */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun TruncationBanner(
    reasons: Set<MdnsTruncationReason>,
    scanComplete: Boolean,
) {
    val partial = stringResource(R.string.mdns_partial_results_status, limitsText(reasons))
    val showing =
        stringResource(
            if (scanComplete) R.string.mdns_showing_all else R.string.mdns_showing_so_far,
        )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.mdns_partial_results_notice, partial, showing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun MdnsScanFailure?.messageRes(): Int =
    when (this) {
        MdnsScanFailure.PERMISSION_DENIED -> R.string.mdns_error_permission
        MdnsScanFailure.NO_LOCAL_NETWORK -> R.string.mdns_error_no_local_network
        MdnsScanFailure.NETWORK_FAILED -> R.string.mdns_error_network_failed
        MdnsScanFailure.GENERAL, null -> R.string.mdns_error_general
    }

@Composable
private fun limitsText(reasons: Set<MdnsTruncationReason>): String {
    val labels = reasons.sortedBy { it.ordinal }.map { limitLabel(it) }
    return labels.joinToString(", ")
}

@Composable
private fun limitLabel(reason: MdnsTruncationReason): String =
    when (reason) {
        MdnsTruncationReason.SERVICE_TYPE_LIMIT -> {
            stringResource(R.string.mdns_limit_service_types, MdnsDiscoveryLimits.DEFAULT_MAX_SERVICE_TYPES)
        }

        MdnsTruncationReason.INSTANCE_LIMIT -> {
            stringResource(R.string.mdns_limit_services, MdnsDiscoveryLimits.DEFAULT_MAX_INSTANCES)
        }

        MdnsTruncationReason.ADDRESS_LIMIT -> {
            stringResource(
                R.string.mdns_limit_addresses,
                MdnsDiscoveryLimits.DEFAULT_MAX_ADDRESSES_PER_SERVICE,
            )
        }

        MdnsTruncationReason.TXT_BYTES_LIMIT -> {
            stringResource(R.string.mdns_limit_txt, MdnsDiscoveryLimits.DEFAULT_MAX_TXT_BYTES_PER_SERVICE)
        }

        MdnsTruncationReason.QUERY_LIMIT -> {
            stringResource(R.string.mdns_limit_queries, MdnsDiscoveryLimits.DEFAULT_MAX_QUERIES)
        }

        MdnsTruncationReason.PACKET_RECORD_LIMIT -> {
            stringResource(R.string.mdns_limit_records, MdnsDiscoveryLimits.DEFAULT_MAX_RECORDS_PER_PACKET)
        }
    }

// ── Control row ───────────────────────────────────────────────────────────────

@Composable
private fun ControlRow(
    isScanning: Boolean,
    isCanceling: Boolean,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    hasPriorResults: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            targetState = isScanning,
            label = "scan-btn",
            modifier = Modifier.weight(1f),
        ) { scanning ->
            if (scanning) {
                if (isCanceling) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mdns_canceling_button))
                    }
                } else {
                    ToolStopButton(
                        text = stringResource(R.string.mdns_stop_button),
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Button(
                    onClick = hapticAction(onScan),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mdns_scan_button))
                }
            }
        }

        if (hasPriorResults && !isScanning) {
            FilledTonalButton(onClick = onReset) {
                Text(stringResource(R.string.mdns_clear_button))
            }
        }
    }
}

// ── States ────────────────────────────────────────────────────────────────────

@Composable
private fun IdleHint() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NetworkPing,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.mdns_idle_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.mdns_idle_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun ScanningPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-alpha",
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scanningCd = stringResource(R.string.a11y_scanning)
        CircularProgressIndicator(
            modifier =
                Modifier
                    .size(52.dp)
                    .semantics { contentDescription = scanningCd },
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulse),
        )
        Text(
            text = stringResource(R.string.mdns_scanning_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CancelingPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeCap = StrokeCap.Round)
        Text(stringResource(R.string.mdns_canceling_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CanceledEmptyHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.mdns_canceled_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.mdns_canceled_empty_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    ElevatedCard(
        shape = AppShapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.mdns_error_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.mdns_error_dismiss)) }
        }
    }
}

// ── Service list ──────────────────────────────────────────────────────────────

internal fun mdnsHttpIntent(service: DiscoveredService): ToolIntent? {
    if (!service.serviceType.equals("_http._tcp", ignoreCase = true)) return null
    val host = ToolHost.parse(service.hostname) ?: return null
    val port = ToolPort.parse(service.port) ?: return null
    return ToolIntent(
        destination = ToolDestination.HostTarget(HostTool.HTTP, host, port),
        source = ToolSource.MDNS,
    )
}

internal fun buildMdnsShareText(
    services: List<DiscoveredService>,
    isScanning: Boolean,
    isCanceling: Boolean,
    scanCanceled: Boolean,
    truncationNote: String?,
    filterQuery: String = "",
): String =
    buildString {
        appendLine("mDNS services: ${services.size}")
        if (filterQuery.isNotBlank()) appendLine("Filter: ${filterQuery.trim()}")
        when {
            isCanceling -> appendLine("Discovery stopping; partial results.")
            isScanning -> appendLine("Discovery in progress; partial results.")
            scanCanceled -> appendLine("Discovery canceled; partial results.")
            else -> appendLine("Discovery window complete.")
        }
        truncationNote?.let { appendLine(it) }
        services.sortedWith(compareBy({ it.serviceType }, { it.instanceName })).forEach { service ->
            appendLine("${service.displayName} (${service.serviceType}) · ${service.hostname}:${service.port}")
            if (service.ipAddresses.isNotEmpty()) appendLine("Addresses: ${service.ipAddresses.joinToString()}")
            service.txtRecords.toSortedMap().forEach { (key, value) -> appendLine("TXT $key=$value") }
        }
    }

@Composable
private fun ServiceList(
    servicesByType: Map<String, List<DiscoveredService>>,
    isScanning: Boolean,
    isCanceling: Boolean,
    scanCanceled: Boolean,
    truncationReasons: Set<MdnsTruncationReason>,
    onPingHost: (String) -> Unit,
    onHttpProbe: (DiscoveredService) -> Unit,
) {
    val expandedTypes = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredByType = filterMdnsServices(servicesByType, searchQuery)
    // Copy and Share export exactly what the list shows, so the filter is part of the report.
    val truncationNote =
        truncationReasons.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.mdns_partial_results_status, limitsText(it))
        }
    val report =
        buildMdnsShareText(
            services = filteredByType.values.flatten(),
            isScanning = isScanning,
            isCanceling = isCanceling,
            scanCanceled = scanCanceled,
            truncationNote = truncationNote,
            filterQuery = searchQuery,
        )
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.mdns_screen_title)
    val noMatch = searchQuery.isNotBlank() && filteredByType.isEmpty() && servicesByType.isNotEmpty()

    // Plain Column, not a LazyColumn: the whole screen scrolls and the result count is bounded
    // (each service keeps at most 16 addresses), so nested scrolling is not needed.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceSearchToolbar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
        )
        if (noMatch) {
            NoMatchHint(query = searchQuery, onClear = { searchQuery = "" })
        }
        if (scanCanceled) {
            Text(
                text = stringResource(R.string.mdns_canceled_partial),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (isScanning) {
            ServiceScanningStatus(isCanceling)
        }
        ServiceGroups(filteredByType, expandedTypes, onPingHost, onHttpProbe)
        ResultActionsRow(
            copyText = report,
            onShare = { context.shareText(report, shareSubject) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

internal fun filterMdnsServices(
    servicesByType: Map<String, List<DiscoveredService>>,
    searchQuery: String,
): Map<String, List<DiscoveredService>> =
    servicesByType
        .mapValues { (_, services) ->
            services.filter { service ->
                searchQuery.isBlank() ||
                    listOf(service.serviceType, service.instanceName, service.displayName, service.hostname)
                        .any { it.contains(searchQuery, ignoreCase = true) } ||
                    service.ipAddresses.any { it.contains(searchQuery, ignoreCase = true) } ||
                    service.txtRecords.any { (key, value) ->
                        key.contains(searchQuery, true) || value.contains(searchQuery, true)
                    }
            }
        }.filterValues { it.isNotEmpty() }

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun ServiceSearchToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    ToolTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text(stringResource(R.string.mdns_search_label)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun ServiceScanningStatus(isCanceling: Boolean) {
    val scanningCd = stringResource(R.string.a11y_scanning)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp).semantics { contentDescription = scanningCd },
            strokeWidth = 2.dp,
        )
        // While scanning, the hero already shows the count and elapsed time and the Stop button
        // says the scan is running, so the status line only names a cancel in progress (PHB-14).
        if (isCanceling) {
            Text(
                stringResource(R.string.mdns_canceling_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun ServiceGroups(
    servicesByType: Map<String, List<DiscoveredService>>,
    expandedTypes: MutableMap<String, Boolean>,
    onPingHost: (String) -> Unit,
    onHttpProbe: (DiscoveredService) -> Unit,
) {
    servicesByType.forEach { (serviceType, services) ->
        val isExpanded = expandedTypes[serviceType] != false
        ServiceTypeHeader(
            serviceType = serviceType,
            count = services.size,
            isExpanded = isExpanded,
            onClick = { expandedTypes[serviceType] = !isExpanded },
        )
        if (isExpanded) {
            services.forEach { service ->
                key(service.instanceName) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ServiceItem(service, onPingHost, onHttpProbe)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTypeHeader(
    serviceType: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = AppMotion.effect(200),
        label = "chevron",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onClick)
                .semantics { role = Role.Button }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Router,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = serviceType,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.rotate(rotationAngle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceItem(
    service: DiscoveredService,
    onPingHost: (String) -> Unit,
    onHttpProbe: (DiscoveredService) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = AppShapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
                .animateContentSize()
                .clickable { expanded = !expanded }
                .semantics { role = Role.Button },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (service.port > 0) {
                    Text(
                        text = ":${service.port}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            val validHostname =
                ToolHost.parse(service.hostname)?.takeUnless {
                    HostValidator.isValidIpv4(it.value) || HostValidator.isValidIpv6(it.value)
                }
            if (mdnsHttpIntent(service) != null) {
                val httpProbeDescription =
                    stringResource(
                        R.string.mdns_http_probe_description,
                        service.displayName,
                    )
                FilledTonalButton(
                    onClick = { onHttpProbe(service) },
                    modifier = Modifier.semantics { contentDescription = httpProbeDescription },
                ) {
                    Icon(Icons.Default.Http, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mdns_http_probe))
                }
            }
            if (validHostname != null) {
                val pingHostnameDescription =
                    stringResource(
                        R.string.mdns_ping_hostname_description,
                        validHostname.value,
                    )
                Text(
                    text = validHostname.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = { onPingHost(validHostname.value) },
                    modifier = Modifier.semantics { contentDescription = pingHostnameDescription },
                ) {
                    Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mdns_ping_hostname))
                }
            }

            val validAddresses =
                service.ipAddresses
                    .mapNotNull { address ->
                        ToolHost.parse(address)?.takeIf {
                            HostValidator.isValidIpv4(it.value) || HostValidator.isValidIpv6(it.value)
                        }
                    }.distinctBy { it.canonical }
            if (validAddresses.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    validAddresses.forEach { ip ->
                        SuggestionChip(
                            onClick = { onPingHost(ip.value) },
                            label = {
                                Text(
                                    stringResource(R.string.mdns_ping_address, ip.value),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (service.txtRecords.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            stringResource(R.string.mdns_txt_records_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        service.txtRecords.entries.take(10).forEach { (key, value) ->
                            Row {
                                Text(
                                    "$key=",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NoMatchHint(
    query: String,
    onClear: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(R.string.mdns_no_match_title, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            FilledTonalButton(onClick = onClear) {
                Text(stringResource(R.string.mdns_no_match_clear))
            }
        }
    }
}

@Composable
private fun EmptyResultHint() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.NetworkPing,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(R.string.mdns_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(R.string.mdns_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
