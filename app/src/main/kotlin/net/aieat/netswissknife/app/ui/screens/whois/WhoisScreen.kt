package net.aieat.netswissknife.app.ui.screens.whois

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.whois.WhoisProtocol
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.answeringProtocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.text.format.DateUtils
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoisScreen(viewModel: WhoisViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lookupResult = uiState.result
    val announcementPhase = when {
        uiState.isLoading -> ToolAnnouncementPhase.RUNNING
        uiState.error != null -> ToolAnnouncementPhase.ERROR
        uiState.isCanceled -> if (uiState.hopStates.isEmpty()) {
            ToolAnnouncementPhase.CANCELED
        } else {
            ToolAnnouncementPhase.PARTIAL
        }
        lookupResult != null -> {
            if (lookupResult.hops.any { it.error != null }) ToolAnnouncementPhase.PARTIAL
            else ToolAnnouncementPhase.FINISHED
        }
        else -> null
    }
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Rotation also reaches ON_STOP and disposes this screen. The Activity reports it through
    // isChangingConfigurations, and the lookup must keep running across that recreation.
    val activity = context as? Activity
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
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

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val canRefresh = uiState.result != null || uiState.error != null

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { if (canRefresh) viewModel.lookup() },
        modifier = Modifier.fillMaxSize()
    ) {
    ToolStateAnnouncer(stringResource(R.string.help_whois_title), announcementPhase)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero header ────────────────────────────────────────────────────
            WhoisHeroHeader(onHelpClick = { showHelp = true })
            NetworkStatusBanner(networkStatus, scope = NetworkStatusScope.INTERNET)

            // ── Input card ─────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val queryInvalidText = stringResource(R.string.whois_query_invalid)
                        val queryInvalidSupport: @Composable () -> Unit = { Text(queryInvalidText) }
                        val queryKeyboard =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Go,
                            )
                        val queryActions =
                            KeyboardActions(
                                onGo = { if (!uiState.isLoading && !uiState.queryInvalid) viewModel.lookup() },
                            )
                    ToolTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.whois_query_label)) },
                        placeholder = { Text(stringResource(R.string.whois_query_placeholder)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                        singleLine = true,
                            enabled = !uiState.isLoading,
                            isError = uiState.queryInvalid,
                            supportingText = if (uiState.queryInvalid) queryInvalidSupport else null,
                            // Domain names are not sentences: no capital letters or autocorrected spaces (PHB-10).
                            keyboardOptions = queryKeyboard,
                            keyboardActions = queryActions,
                    )
                    RecentHostsRow(
                        recentHosts = recentHosts,
                        onHostSelected = viewModel::onQueryChange,
                        onRemoveHost = viewModel::removeRecentHost,
                        onClearAll = viewModel::clearRecentHosts,
                        selectionEnabled = !uiState.isLoading && !uiState.isCanceling,
                    )
                    Text(
                        text = stringResource(R.string.whois_protocol_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            WhoisProtocol.AUTO to R.string.whois_protocol_auto,
                            WhoisProtocol.RDAP to R.string.whois_protocol_rdap,
                            WhoisProtocol.WHOIS to R.string.whois_protocol_whois,
                        ).forEach { (protocol, label) ->
                            FilterChip(
                                selected = uiState.protocol == protocol,
                                onClick = { viewModel.onProtocolChange(protocol) },
                                enabled = !uiState.isLoading && !uiState.isCanceling,
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.whois_protocol_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = hapticAction(viewModel::lookup),
                        modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading && uiState.query.isNotBlank() && !uiState.queryInvalid,
                    ) {
                        Text(stringResource(R.string.whois_lookup_button))
                    }
                    if (uiState.isLoading) {
                        TextButton(
                            onClick = viewModel::stopLookup,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isCanceling,
                        ) {
                            Text(
                                stringResource(
                                    if (uiState.isCanceling) R.string.whois_canceling_button
                                    else R.string.whois_stop_button
                                )
                            )
                        }
                    }
                }
            }

            // ── Relay chain (shown when hops exist or loading) ─────────────────
            AnimatedVisibility(visible = uiState.hopStates.isNotEmpty() || uiState.isLoading) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.whois_relay_chain),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.hopStates.isNotEmpty()) {
                            RelayChainVisualiser(hopStates = uiState.hopStates)
                        } else if (uiState.isCanceling) {
                            Text(
                                text = stringResource(R.string.whois_canceling_status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Show loading indicator when waiting for first hop
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        if (uiState.isLoading && !uiState.isCanceling) {
                            val currentServer = uiState.hopStates
                                .lastOrNull { it.status == HopStatus.QUERYING || it.status == HopStatus.DONE }
                                ?.server?.host ?: ""
                            if (currentServer.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.whois_querying_server, currentServer),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Main content area (Idle / Error / Success) ─────────────────────
            AnimatedContent(
                targetState = Triple(
                    uiState.isLoading,
                    uiState.isCanceled,
                    Triple(uiState.result, uiState.error, uiState.isLifecyclePaused)
                ),
                transitionSpec = {
                    fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 8 } togetherWith
                            fadeOut(AppMotion.exit(200))
                },
                label = "whois-content-state"
            ) { (isLoading, isCanceled, content) ->
                val (result, error, isLifecyclePaused) = content
                when {
                    result != null -> {
                        val shareSubject = stringResource(R.string.share_subject_whois, result.query)
                        WhoisResultsSection(
                            result = result,
                            showRaw = uiState.showRawResponse,
                            onToggleRaw = viewModel::onToggleRawResponse,
                            onShare = {
                                context.shareText(
                                    text = buildWhoisShareText(result),
                                    subject = shareSubject
                                )
                            },
                            onOpenUrl = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    isCanceled -> {
                        CanceledLookupCard(
                            isLifecyclePaused = isLifecyclePaused,
                            onRetry = viewModel::lookup,
                        )
                    }
                    error != null -> {
                        ErrorCard(
                                message = error,
                            onRetry = viewModel::lookup,
                            hopStates = uiState.hopStates
                        )
                    }
                    isLoading -> {
                        // Chain visualiser is shown above; no additional content needed during loading
                        Box(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        IdleCard(onExampleSelected = viewModel::onQueryChange)
                    }
                }
            }
        }
    }
    } // end PullToRefreshBox

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_whois_title),
            conceptHeading = stringResource(R.string.help_whois_concept_heading),
            conceptBody = stringResource(R.string.help_whois_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_whois_what_heading), stringResource(R.string.help_whois_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_whois_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_whois_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_whois_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_whois_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun CanceledLookupCard(isLifecyclePaused: Boolean, onRetry: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.whois_canceled_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (isLifecyclePaused) R.string.whois_lifecycle_paused_message
                    else R.string.whois_canceled_message
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            ToolRetryButton(text = stringResource(R.string.whois_retry), onClick = onRetry)
        }
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun WhoisHeroHeader(onHelpClick: () -> Unit) {
    ToolHeroHeader(
        title = stringResource(R.string.whois_screen_title),
        subtitle = stringResource(R.string.whois_screen_subtitle),
        icon = Icons.AutoMirrored.Filled.ManageSearch,
        onHelpClick = onHelpClick
    )
}

// ── Relay Chain Visualiser ────────────────────────────────────────────────────

private val HopNodeSize = 40.dp
private val HopNodeRadius = HopNodeSize / 2
private val HopConnectorStroke = 2.dp

@Composable
fun RelayChainVisualiser(hopStates: List<HopUiState>) {
    if (hopStates.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        // The querying node pulses to 1.1x, so leave room for the enlarged
        // circle rather than letting it touch the labels below.
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // The circles get their own fixed-height row so the connector canvas can
        // use size.height / 2 as the node centre. Drawing circles and labels in
        // one row made the line drift downwards as per-hop timings appeared
        // mid-scan and grew the row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HopNodeSize)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val centerY = size.height / 2f
                val stroke = HopConnectorStroke.toPx()
                RelayChainGeometry
                    .connectorSegments(hopStates.size, size.width, HopNodeRadius.toPx())
                    .forEach { segment ->
                        drawLine(
                            color = connectorColor,
                            start = Offset(segment.startX, centerY),
                            end = Offset(segment.endX, centerY),
                            strokeWidth = stroke
                        )
                    }
            }

            // Equal-weight cells, no arrangement spacing and no padding: this must
            // stay in lockstep with RelayChainGeometry.nodeCenterX.
            Row(modifier = Modifier.fillMaxSize()) {
                hopStates.forEach { hop ->
                    HopNodeCircle(
                        hop = hop,
                        infiniteTransition = infiniteTransition,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            hopStates.forEach { hop ->
                HopNodeLabels(
                    hop = hop,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HopNodeCircle(
    hop: HopUiState,
    infiniteTransition: InfiniteTransition,
    modifier: Modifier = Modifier
) {
    // Every completed hop uses the same success colour; the role is shown in the label below.
    val doneColor = MaterialTheme.colorScheme.primaryContainer
    val doneOnColor = MaterialTheme.colorScheme.onPrimaryContainer

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when (hop.status) {
            HopStatus.PENDING -> MaterialTheme.colorScheme.surface
            HopStatus.QUERYING -> MaterialTheme.colorScheme.primaryContainer
            HopStatus.DONE -> doneColor
            HopStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            HopStatus.SKIPPED -> MaterialTheme.colorScheme.surface
        },
        label = "node_bg"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(HopNodeSize)
                .scale(if (hop.status == HopStatus.QUERYING) pulseScale else 1f)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            when (hop.status) {
                HopStatus.DONE ->
                    Icon(
                        Icons.Default.Check, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = doneOnColor
                    )
                HopStatus.FAILED ->
                    Icon(
                        Icons.Default.Close, contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                HopStatus.QUERYING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                else -> {}
            }
        }
    }
}

@Composable
private fun HopNodeLabels(hop: HopUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = hop.server.host,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = hop.server.role.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        if (hop.status == HopStatus.DONE && hop.queryTimeMs != null) {
            Text(
                text = stringResource(R.string.whois_hop_latency_ms, hop.queryTimeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Idle Card ─────────────────────────────────────────────────────────────────

@Composable
fun IdleCard(onExampleSelected: (String) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ManageSearch,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.whois_idle_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { onExampleSelected("example.com") },
                    label = { Text(stringResource(R.string.whois_example_chip_domain)) }
                )
                FilterChip(
                    selected = false,
                    onClick = { onExampleSelected("8.8.8.8") },
                    label = { Text(stringResource(R.string.whois_example_chip_ip)) }
                )
                FilterChip(
                    selected = false,
                    onClick = { onExampleSelected("AS15169") },
                    label = { Text(stringResource(R.string.whois_example_chip_asn)) }
                )
            }
        }
    }
}

// ── Error Card ────────────────────────────────────────────────────────────────

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit, hopStates: List<HopUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hopStates.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                RelayChainVisualiser(hopStates = hopStates)
            }
        }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(R.string.whois_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                ToolRetryButton(text = stringResource(R.string.whois_retry), onClick = onRetry)
            }
        }
    }
}

// ── Results Section ───────────────────────────────────────────────────────────

@Composable
fun WhoisResultsSection(
    result: WhoisResult,
    showRaw: Boolean,
    onToggleRaw: () -> Unit,
    onShare: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The query chain already names the answering server, so this line says only the protocol.
        result.answeringProtocol()?.let { answeredBy ->
            val labelRes = if (answeredBy == WhoisProtocol.RDAP) {
                R.string.whois_answered_by_rdap
            } else {
                R.string.whois_answered_by_whois
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.rdapFallbackReason?.let { reason ->
            Text(
                text = stringResource(R.string.whois_rdap_fallback_note, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (result.queryType) {
            WhoisQueryType.DOMAIN -> DomainResultCard(result, onOpenUrl)
            WhoisQueryType.IPV4, WhoisQueryType.IPV6, WhoisQueryType.ASN -> IpAsnResultCard(result)
        }

        // Raw response accordion
        RawResponseAccordion(result = result, showRaw = showRaw, onToggle = onToggleRaw)
        ResultActionsRow(
            copyText = buildWhoisShareText(result),
            onShare = onShare,
        )
    }
}

// ── Domain Result Card ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomainResultCard(result: WhoisResult, onOpenUrl: (String) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Gradient header
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
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = result.domainName ?: result.query,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val registrar = result.registrar
                    if (registrar != null) {
                        Text(
                            text = registrar,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    val registrarUrl = result.registrarUrl
                    if (registrarUrl != null) {
                        TextButton(onClick = { onOpenUrl(registrarUrl) }) {
                            Text(
                                text = registrarUrl,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Lifecycle timeline
            if (result.registeredOn != null || result.expiresOn != null || result.updatedOn != null) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.whois_lifecycle),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DateColumn(
                            icon = Icons.Default.CalendarToday,
                            label = stringResource(R.string.whois_registered),
                            epochMs = result.registeredOn,
                            colorOverride = null
                        )
                        DateColumn(
                            icon = Icons.Default.Update,
                            label = stringResource(R.string.whois_updated),
                            epochMs = result.updatedOn,
                            colorOverride = null
                        )
                        DateColumn(
                            icon = Icons.Default.EventBusy,
                            label = stringResource(R.string.whois_expires),
                            epochMs = result.expiresOn,
                            colorOverride = expiryColor(result.expiresOn)
                        )
                    }
                }
                HorizontalDivider()
            }

            // Status codes
            if (result.statusCodes.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.whois_domain_status),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        result.statusCodes.forEach { status ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(whoisStatusLabel(status), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            // Name servers
            if (result.nameServers.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.whois_name_servers),
                        style = MaterialTheme.typography.titleMedium
                    )
                    result.nameServers.forEach { ns ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(text = ns, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HorizontalDivider()
            }

            // DNSSEC
            val dnssec = result.dnssec
            if (dnssec != null) {
                LabeledRow(label = stringResource(R.string.whois_dnssec), value = dnssecText(dnssec))
                HorizontalDivider()
            }

            // Registrant
            val registrantOrg = result.registrantOrg
            val registrantCountry = result.registrantCountry
            if (registrantOrg != null || registrantCountry != null) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.whois_registrant),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (registrantOrg != null) {
                        Text(text = registrantOrg, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (registrantCountry != null) {
                        Text(
                            text = "${flagEmoji(registrantCountry)} $registrantCountry",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ── IP/ASN Result Card ────────────────────────────────────────────────────────

@Composable
fun IpAsnResultCard(result: WhoisResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
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
                    .padding(16.dp)
            ) {
                Text(
                    text = result.netName ?: result.query,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            HorizontalDivider()
            val netRange = result.netRange
            val orgName = result.orgName
            val country = result.country
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (netRange != null) LabeledRow(stringResource(R.string.whois_net_range), netRangeDisplay(netRange))
                if (orgName != null) LabeledRow(stringResource(R.string.whois_org), orgName)
                if (country != null) {
                    LabeledRow(
                        label = stringResource(R.string.whois_country),
                        value = "${flagEmoji(country)} $country"
                    )
                }
            }
        }
    }
}

// ── Raw Response Accordion ────────────────────────────────────────────────────

@Composable
fun RawResponseAccordion(result: WhoisResult, showRaw: Boolean, onToggle: () -> Unit) {
    if (result.hops.isEmpty()) return
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.whois_raw_responses),
                    style = MaterialTheme.typography.titleMedium
                )
                // One affordance for one action: the whole row toggles, the chevron shows the state.
                Icon(
                    imageVector = if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = showRaw) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.hops.forEach { hop ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = hop.server.host,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SelectionContainer {
                                Text(
                                    text = hop.rawResponse,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
fun DateColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    epochMs: Long?,
    colorOverride: Color?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colorOverride ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val formatted = epochMs?.let { formatDate(it) } ?: "–"
        Text(
            text = formatted,
            style = MaterialTheme.typography.bodyMedium,
            color = colorOverride ?: MaterialTheme.colorScheme.onSurface
        )
        if (epochMs != null) {
            Text(
                text = relativeDate(epochMs),
                style = MaterialTheme.typography.labelSmall,
                color = colorOverride ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        SelectionContainer(modifier = Modifier.weight(0.6f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
    }
}

// ── Utility functions ─────────────────────────────────────────────────────────

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))

private fun relativeDate(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.DAY_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

@Composable
private fun expiryColor(epochMs: Long?): Color? {
    if (epochMs == null) return null
    val now = System.currentTimeMillis()
    val daysLeft = TimeUnit.MILLISECONDS.toDays(epochMs - now)
    return when {
        epochMs < now -> MaterialTheme.colorScheme.error
        daysLeft < 30 -> MaterialTheme.colorScheme.error
        daysLeft < 90 -> MaterialTheme.colorScheme.tertiary
        else -> null
    }
}

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val base = 0x1F1E6 - 'A'.code
    return countryCode.uppercase()
        .map { char -> String(Character.toChars(base + char.code)) }
        .joinToString("")
}

internal fun buildWhoisShareText(result: net.aieat.netswissknife.core.network.whois.WhoisResult): String = buildString {
    appendLine("WHOIS – ${result.query}")
    appendLine()
    result.registrar?.let { appendLine("Registrar: $it") }
    result.registeredOn?.let { appendLine("Created: ${formatDate(it)}") }
    result.updatedOn?.let { appendLine("Updated: ${formatDate(it)}") }
    result.expiresOn?.let { appendLine("Expires: ${formatDate(it)}") }
    if (result.nameServers.isNotEmpty()) {
        appendLine("Name Servers: ${result.nameServers.joinToString(", ")}")
    }
    if (result.statusCodes.isNotEmpty()) {
        appendLine("Status: ${result.statusCodes.joinToString(", ")}")
    }
    val rawWhois = result.hops.lastOrNull()?.rawResponse?.trim() ?: ""
    if (rawWhois.isNotBlank()) {
        appendLine()
        appendLine("--- Raw WHOIS ---")
        append(rawWhois)
    }
}

/** The DNSSEC row text: one localised label for each known state, humanized text otherwise. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun dnssecText(raw: String): String {
    val signed = stringResource(R.string.whois_dnssec_signed)
    val unsigned = stringResource(R.string.whois_dnssec_unsigned)
    return when (val label = whoisDnssecLabel(raw)) {
        is WhoisDnssecLabel.Text -> label.text
        is WhoisDnssecLabel.Known -> if (label.state == WhoisDnssecState.SIGNED) signed else unsigned
    }
}
