package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.ui.components.HeroTitleText
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.components.ToolStopButton
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.network.HostValidator

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

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = uiState.isScanning,
        onRefresh = { viewModel.startScan(8_000L) },
        modifier = Modifier.fillMaxSize()
    ) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { 40 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroCard(uiState, onHelpClick = { showHelp = true })

            NetworkStatusBanner(
                status = networkStatus,
                scope = NetworkStatusScope.LOCAL_NETWORK,
                permissionDenied = uiState.networkErrorKind == NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
                onGrantPermission = requestLocalNetworkPermission,
            )

            ControlRow(
                isScanning = uiState.isScanning,
                isCanceling = uiState.isCanceling,
                onScan = { viewModel.startScan(8_000L) },
                onStop = { viewModel.stopScan() },
                onReset = { viewModel.reset() },
                hasPriorResults = uiState.services.isNotEmpty() || uiState.scanComplete || uiState.scanCanceled
            )

            AnimatedContent(
                targetState = when {
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
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    "idle" -> IdleHint()
                    "canceling_empty" -> CancelingPlaceholder()
                    "canceled_empty" -> CanceledEmptyHint()
                    "scanning_empty" -> ScanningPlaceholder()
                    "empty_done" -> EmptyResultHint()
                    "error" -> ErrorCard(uiState.error ?: "Unknown error") { viewModel.reset() }
                    else -> ServiceList(
                        servicesByType = uiState.servicesByType,
                        isScanning = uiState.isScanning,
                        isCanceling = uiState.isCanceling,
                        scanCanceled = uiState.scanCanceled,
                        onPingHost = { host ->
                            ToolHost.parse(host)?.let { validatedHost ->
                                onNavigate(
                                    NavRoutes.Ping.createRoute(
                                        ToolIntent(
                                            destination = ToolDestination.HostTarget(HostTool.PING, validatedHost),
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
    } // end PullToRefreshBox

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_mdns_title),
            conceptHeading = stringResource(R.string.help_mdns_concept_heading),
            conceptBody = stringResource(R.string.help_mdns_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_mdns_what_heading), stringResource(R.string.help_mdns_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_mdns_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_mdns_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_mdns_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_mdns_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(state: MdnsDiscoveryUiState, onHelpClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )

    ElevatedCard(
        shape = AppShapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    HeroTitleText(
                        text = stringResource(R.string.mdns_screen_title),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.mdns_screen_subtitle),
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

            // Stats badges
            if (state.services.isNotEmpty() || state.isScanning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatBadge(
                        value = state.services.size.toString(),
                        label = stringResource(R.string.mdns_stat_found)
                    )
                    if (state.isScanning) {
                        StatBadge(
                            value = "${state.elapsedMs / 1000}s",
                            label = stringResource(R.string.mdns_stat_elapsed)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
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
    hasPriorResults: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            targetState = isScanning,
            label = "scan-btn",
            modifier = Modifier.weight(1f)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Button(
                    onClick = hapticAction(onScan),
                    modifier = Modifier.fillMaxWidth()
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NetworkPing,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.mdns_idle_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.mdns_idle_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
        label = "pulse-alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val scanningCd = stringResource(R.string.a11y_scanning)
        CircularProgressIndicator(
            modifier = Modifier
                .size(52.dp)
                .semantics { contentDescription = scanningCd },
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulse)
        )
        Text(
            text = stringResource(R.string.mdns_scanning_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CancelingPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.mdns_canceled_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(stringResource(R.string.mdns_canceled_empty_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    ElevatedCard(
        shape = AppShapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.mdns_error_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
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

@Composable
private fun ServiceList(
    servicesByType: Map<String, List<DiscoveredService>>,
    isScanning: Boolean,
    isCanceling: Boolean,
    scanCanceled: Boolean,
    onPingHost: (String) -> Unit,
    onHttpProbe: (DiscoveredService) -> Unit,
) {
    val expandedTypes = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scanCanceled) {
            item {
                Text(
                    text = stringResource(R.string.mdns_canceled_partial),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        if (isScanning) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val scanningCd = stringResource(R.string.a11y_scanning)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(14.dp)
                            .semantics { contentDescription = scanningCd },
                        strokeWidth = 2.dp
                    )
                    Text(
                        stringResource(if (isCanceling) R.string.mdns_canceling_hint else R.string.mdns_scanning_inline_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        servicesByType.entries.forEach { (serviceType, services) ->
            val isExpanded = expandedTypes[serviceType] != false

            item(key = serviceType) {
                ServiceTypeHeader(
                    serviceType = serviceType,
                    count = services.size,
                    isExpanded = isExpanded,
                    onClick = { expandedTypes[serviceType] = !isExpanded }
                )
            }

            if (isExpanded) {
                items(services, key = { it.instanceName }) { service ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ServiceItem(service, onPingHost, onHttpProbe)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ServiceTypeHeader(
    serviceType: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = AppMotion.effect(200),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Router,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = serviceType,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.rotate(rotationAngle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .animateContentSize()
            .clickable { expanded = !expanded }
            .semantics { role = Role.Button }
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
                    overflow = TextOverflow.Ellipsis
                )
                if (service.port > 0) {
                    Text(
                        text = ":${service.port}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            val validHostname = ToolHost.parse(service.hostname)?.takeUnless {
                HostValidator.isValidIpv4(it.value) || HostValidator.isValidIpv6(it.value)
            }
            if (mdnsHttpIntent(service) != null) {
                val httpProbeDescription = stringResource(
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
                val pingHostnameDescription = stringResource(
                    R.string.mdns_ping_hostname_description,
                    validHostname.value,
                )
                Text(
                    text = validHostname.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

            val validAddresses = service.ipAddresses.mapNotNull { address ->
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
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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
                            fontWeight = FontWeight.SemiBold
                        )
                        service.txtRecords.entries.take(10).forEach { (key, value) ->
                            Row {
                                Text(
                                    "$key=",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
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
private fun EmptyResultHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NetworkPing,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(R.string.mdns_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(R.string.mdns_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
