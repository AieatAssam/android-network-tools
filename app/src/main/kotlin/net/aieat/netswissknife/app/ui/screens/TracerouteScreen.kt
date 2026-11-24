
package net.aieat.netswissknife.app.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.DisabledActionHint
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolStopButton
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.format.humanDurationText
import net.aieat.netswissknife.app.ui.format.resolvedIpToShow
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.app.ui.screens.traceroute.HopListRow
import net.aieat.netswissknife.app.ui.screens.traceroute.SliderScale
import net.aieat.netswissknife.app.ui.screens.traceroute.TraceSummary
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteUiState
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewMode
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewModel
import net.aieat.netswissknife.app.ui.screens.traceroute.hopListRows
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.StatusBad
import net.aieat.netswissknife.app.ui.theme.StatusCritical
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.app.ui.theme.StatusLime
import net.aieat.netswissknife.app.ui.theme.StatusUnknown
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.traceroute.CountryFlags
import net.aieat.netswissknife.core.network.traceroute.GeoPlausibility
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1_000.0

// Above this many stops a slider snaps without drawing a tick mark for each one.
private const val MAX_SLIDER_TICKS = 20

// ── Screen entry point ────────────────────────────────────────────────────────

@Composable
fun TracerouteScreen(viewModel: TracerouteViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester(viewModel::startTrace)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val announcementPhase =
        when (val state = uiState) {
            TracerouteUiState.Idle -> {
                null
            }

            is TracerouteUiState.Running, is TracerouteUiState.Canceling -> {
                ToolAnnouncementPhase.RUNNING
            }

            is TracerouteUiState.Canceled -> {
                if (state.result.hops.isNotEmpty()) {
                    ToolAnnouncementPhase.PARTIAL
                } else {
                    ToolAnnouncementPhase.CANCELED
                }
            }

            is TracerouteUiState.Finished -> {
                if (state.timeLimitReached || !state.result.reachedDestination) {
                    ToolAnnouncementPhase.PARTIAL
                } else {
                    ToolAnnouncementPhase.FINISHED
                }
            }

            is TracerouteUiState.Error -> {
                ToolAnnouncementPhase.ERROR
            }
        }
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val maxHops by viewModel.maxHops.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.timeoutMs.collectAsStateWithLifecycle()
    val probesPerHop by viewModel.probesPerHop.collectAsStateWithLifecycle()
    val probeType by viewModel.probeType.collectAsStateWithLifecycle()
    val packetSize by viewModel.packetSize.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    // Rotation also reaches ON_STOP; the trace must survive a configuration change.
                    val changingConfigurations = (context as? Activity)?.isChangingConfigurations == true
                    viewModel.onLifecycleStop(configurationChange = changingConfigurations)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // animateFloatAsState instead of AnimatedVisibility: both AnimatedVisibility and the
    // inner AnimatedContent use SubcomposeLayout.  Two simultaneously-animating nested
    // SubcomposeLayouts throw IllegalStateException (e.g. when a running trace completes
    // during the 400 ms entrance animation on navigation return).
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label = "screen-alpha",
    )
    var showHelp by remember { mutableStateOf(false) }
    // Once a result is shown the configuration folds to a one-line summary so the hops are near the top (PHA-16).
    var settingsExpanded by rememberSaveable { mutableStateOf(true) }
    val resultShown = uiState.isResultState()
    LaunchedEffect(resultShown) { if (resultShown) settingsExpanded = false }
    val settingsCollapsed = !settingsExpanded && uiState !is TracerouteUiState.Idle

    LazyColumn(
        modifier = Modifier.fillMaxSize().alpha(screenAlpha).testTag("traceroute_content_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box {
                ToolStateAnnouncer(stringResource(R.string.help_traceroute_title), announcementPhase)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TracerouteHeroHeader(onHelpClick = { showHelp = true })
                    NetworkStatusBanner(networkStatus, scope = NetworkStatusScope.ANY_NETWORK)
                }
            }
        }

        item {
            TracerouteInputCard(
                host = host,
                maxHops = maxHops,
                timeoutMs = timeoutMs,
                probesPerHop = probesPerHop,
                probeType = probeType,
                packetSize = packetSize,
                isRunning = uiState is TracerouteUiState.Running,
                isCanceling = uiState is TracerouteUiState.Canceling,
                recentHosts = recentHosts,
                onHostChange = viewModel::onHostChange,
                onMaxHopsChange = viewModel::onMaxHopsChange,
                onTimeoutChange = viewModel::onTimeoutChange,
                onProbesPerHopChange = viewModel::onProbesPerHopChange,
                onProbeTypeChange = viewModel::onProbeTypeChange,
                onPacketSizeChange = viewModel::onPacketSizeChange,
                onToggleMtuDiscovery = viewModel::onToggleMtuDiscovery,
                onStart = { requestLocalNetworkPermission(host) },
                onStop = viewModel::onStop,
                onRemoveRecentHost = viewModel::removeRecentHost,
                onClearRecentHosts = viewModel::clearRecentHosts,
                settingsCollapsed = settingsCollapsed,
                onExpandSettings = { settingsExpanded = true },
            )
        }

        item {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    (fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 6 })
                        .togetherWith(fadeOut(AppMotion.exit(200)))
                },
                // One trace keeps one panel from its first hop to its result, so the chosen view, the
                // map and the hop rows carry over instead of being rebuilt when the trace ends.
                contentKey = { it.panelKey() },
                label = "traceroute-state",
            ) { state ->
                val session = state.session()
                when {
                    session != null -> {
                        TraceSessionPanel(
                            session = session,
                            onSelectMode = viewModel::onSelectViewMode,
                            onClear = viewModel::onClear,
                        )
                    }

                    state is TracerouteUiState.Error -> {
                        TracerouteErrorPanel(
                            state = state,
                            onRetry = { requestLocalNetworkPermission(host) },
                            onClear = viewModel::onClear,
                        )
                    }

                    else -> {
                        TracerouteIdlePrompt()
                    }
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_traceroute_title),
            conceptHeading = stringResource(R.string.help_traceroute_concept_heading),
            conceptBody = stringResource(R.string.help_traceroute_concept_body),
            sections =
                listOf(
                    HelpSection(stringResource(R.string.help_traceroute_what_heading), stringResource(R.string.help_traceroute_what_body)),
                    HelpSection(
                        heading = stringResource(R.string.help_traceroute_params_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_traceroute_params_bullets).toList(),
                    ),
                    HelpSection(
                        heading = stringResource(R.string.help_traceroute_results_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_traceroute_results_bullets).toList(),
                    ),
                ),
            onDismiss = { showHelp = false },
        )
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteHeroHeader(onHelpClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hero-alpha",
    )

    ToolHeroHeader(
        title = stringResource(R.string.traceroute_screen_title),
        subtitle = stringResource(R.string.traceroute_screen_subtitle),
        icon = Icons.Default.Public,
        onHelpClick = onHelpClick,
        iconContent = {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ).alpha(pulseAlpha),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    )
}

/** Total time as a duration of work, or a plain note when no time was measured. */
@Composable
private fun totalTimeText(totalTimeMs: Long): String =
    if (totalTimeMs > 0) {
        humanDurationText(totalTimeMs)
    } else {
        stringResource(R.string.traceroute_stat_time_unknown)
    }

/** A finished, cancelled or failed trace: the point at which the settings fold to a summary. */
private fun TracerouteUiState.isResultState(): Boolean =
    this is TracerouteUiState.Finished ||
        this is TracerouteUiState.Canceled ||
        this is TracerouteUiState.Error

// ── Collapsed settings ────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun TracerouteSettingsSummary(
    host: String,
    maxHops: Int,
    probesPerHop: Int,
    timeoutMs: Int,
    onExpand: () -> Unit,
) {
    val probesText = pluralStringResource(R.plurals.traceroute_probes_per_hop, probesPerHop, probesPerHop)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.traceroute_settings_summary, maxHops, probesText, timeoutMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onExpand) {
                Text(stringResource(R.string.traceroute_settings_show))
            }
        }
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun TracerouteInputCard(
    host: String,
    maxHops: Int,
    timeoutMs: Int,
    probesPerHop: Int,
    probeType: TracerouteProbeType,
    packetSize: Int,
    isRunning: Boolean,
    isCanceling: Boolean,
    recentHosts: List<String>,
    onHostChange: (String) -> Unit,
    onMaxHopsChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onProbesPerHopChange: (Int) -> Unit,
    onProbeTypeChange: (TracerouteProbeType) -> Unit,
    onPacketSizeChange: (Int) -> Unit,
    onToggleMtuDiscovery: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit,
    settingsCollapsed: Boolean = false,
    onExpandSettings: () -> Unit = {},
) {
    if (settingsCollapsed) {
        TracerouteSettingsSummary(
            host = host,
            maxHops = maxHops,
            probesPerHop = probesPerHop,
            timeoutMs = timeoutMs,
            onExpand = onExpandSettings,
        )
        return
    }
    val dismissKeyboard = rememberDismissKeyboard()
    val mtuDiscovery = packetSize == 0
    val normalizedHost = HostValidator.normalize(host)
    // Blank input is not an error yet; a non-blank host gets a specific reason when it is invalid.
    val hostProblem = if (host.isNotBlank()) HostValidator.problemOf(host) else null
    val isHostInvalid = hostProblem != null
    val isBusy = isRunning || isCanceling
    val canStart = normalizedHost != null

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.traceroute_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )

            // Host field
            ToolTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.traceroute_host_label)) },
                placeholder = { Text(stringResource(R.string.traceroute_host_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Router, null) },
                isError = isHostInvalid,
                supportingText =
                    if (hostProblem != null) {
                        { Text(stringResource(hostProblem.messageRes())) }
                    } else {
                        null
                    },
                singleLine = true,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions =
                    KeyboardActions(onGo = {
                        dismissKeyboard()
                        if (!isBusy && canStart) onStart()
                    }),
            )

            if (!isBusy) {
                RecentHostsRow(
                    recentHosts = recentHosts,
                    onHostSelected = onHostChange,
                    onRemoveHost = onRemoveRecentHost,
                    onClearAll = onClearRecentHosts,
                )
            }

            // Probe protocol selector (ICMP / UDP)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.traceroute_probe_type_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = probeType == TracerouteProbeType.ICMP,
                        onClick = { if (!isBusy) onProbeTypeChange(TracerouteProbeType.ICMP) },
                        label = { Text(stringResource(R.string.traceroute_probe_icmp)) },
                        enabled = !isBusy,
                    )
                    FilterChip(
                        selected = probeType == TracerouteProbeType.UDP,
                        onClick = { if (!isBusy) onProbeTypeChange(TracerouteProbeType.UDP) },
                        label = { Text(stringResource(R.string.traceroute_probe_udp)) },
                        enabled = !isBusy,
                    )
                }
            }

            SliderRow(
                label = stringResource(R.string.traceroute_max_hops_label),
                value = maxHops,
                scale = SliderScale.MAX_HOPS,
                display = { "$it" },
                enabled = !isBusy,
                onValueChangeFinished = onMaxHopsChange,
            )
            SliderRow(
                label = stringResource(R.string.traceroute_timeout_label),
                value = timeoutMs,
                scale = SliderScale.TIMEOUT_MS,
                display = { stringResource(R.string.traceroute_timeout_value, it / MS_PER_SECOND) },
                enabled = !isBusy,
                onValueChangeFinished = onTimeoutChange,
            )
            SliderRow(
                label = stringResource(R.string.traceroute_probes_per_hop_label),
                value = probesPerHop,
                scale = SliderScale.PROBES,
                display = { "$it" },
                enabled = !isBusy,
                onValueChangeFinished = onProbesPerHopChange,
            )

            // Packet size: MTU discovery toggle + fixed-size slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.traceroute_mtu_discovery_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Switch(
                    checked = mtuDiscovery,
                    onCheckedChange = { if (!isBusy) onToggleMtuDiscovery(it) },
                    enabled = !isBusy,
                )
            }

            AnimatedVisibility(visible = !mtuDiscovery) {
                SliderRow(
                    label = stringResource(R.string.traceroute_packet_size_label),
                    value = packetSize,
                    scale = SliderScale.PACKET_SIZE,
                    display = { stringResource(R.string.traceroute_packet_size_value, it) },
                    enabled = !isBusy,
                    onValueChangeFinished = onPacketSizeChange,
                )
            }

            // Action button
            if (isBusy) {
                if (isCanceling) {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.traceroute_canceling_button))
                    }
                } else {
                    ToolStopButton(
                        text = stringResource(R.string.traceroute_stop_button),
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                TracerouteStartAction(
                    canStart = canStart,
                    hostBlank = host.isBlank(),
                    isBusy = isBusy,
                    onStart = {
                        dismissKeyboard()
                        onStart()
                    },
                )
            }
        }
    }
}

@Composable
private fun TracerouteStartAction(
    canStart: Boolean,
    hostBlank: Boolean,
    isBusy: Boolean,
    onStart: () -> Unit,
) {
    Button(
        onClick = hapticAction(onStart),
        enabled = canStart && !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Public, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.traceroute_start_button))
    }
    // A disabled button needs its reason next to it (V6A-12). An invalid host already shows its reason
    // under the field, so the hint is only for an empty one.
    if (!canStart && !isBusy && hostBlank) {
        DisabledActionHint(stringResource(R.string.traceroute_start_disabled_hint))
    }
}

/**
 * A setting slider that only lands on the values [scale] allows, showing the value under the thumb while
 * it is dragged. Scales with too many values for tick marks snap without drawing them.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    scale: SliderScale,
    display: @Composable (Int) -> String,
    enabled: Boolean,
    onValueChangeFinished: (Int) -> Unit,
) {
    var localValue by remember(value) { mutableIntStateOf(scale.snap(value.toFloat())) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                display(localValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = localValue.toFloat(),
            onValueChange = { localValue = scale.snap(it) },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = scale.range.first.toFloat()..scale.range.last.toFloat(),
            steps = scale.steps.takeIf { it <= MAX_SLIDER_TICKS } ?: 0,
            enabled = enabled,
        )
    }
}

// ── Idle prompt ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteIdlePrompt() {
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
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.traceroute_idle_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.traceroute_idle_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Trace session panel ───────────────────────────────────────────────────────

/** A trace from its first hop to its result, in whichever phase it is in. */
private data class TraceSession(
    val host: String,
    val hops: List<HopResult>,
    val viewMode: TracerouteViewMode,
    val rawOutput: String,
    val live: Boolean,
    val canceling: Boolean = false,
    val result: TracerouteResult? = null,
    val canceled: Boolean = false,
    val stoppedWhenLeft: Boolean = false,
    val timeLimitReached: Boolean = false,
) {
    /** Stopped before its end, by the user, by leaving the screen or by the time limit. */
    val stopped: Boolean get() = canceling || canceled || stoppedWhenLeft || timeLimitReached
}

private fun TracerouteUiState.session(): TraceSession? =
    when (this) {
        TracerouteUiState.Idle, is TracerouteUiState.Error -> {
            null
        }

        is TracerouteUiState.Running -> {
            TraceSession(host, hops, viewMode, rawOutput, live = true)
        }

        is TracerouteUiState.Canceling -> {
            TraceSession(host, hops, viewMode, rawOutput, live = true, canceling = true)
        }

        is TracerouteUiState.Canceled -> {
            TraceSession(
                host = result.host,
                hops = result.hops,
                viewMode = viewMode,
                rawOutput = result.rawOutput,
                live = false,
                result = result,
                canceled = true,
                stoppedWhenLeft = stoppedWhenLeft,
            )
        }

        is TracerouteUiState.Finished -> {
            TraceSession(
                host = result.host,
                hops = result.hops,
                viewMode = viewMode,
                rawOutput = result.rawOutput,
                live = false,
                result = result,
                timeLimitReached = timeLimitReached,
            )
        }
    }

private fun TracerouteUiState.panelKey(): Any =
    when (this) {
        is TracerouteUiState.Running -> "trace-$traceId"
        is TracerouteUiState.Canceling -> "trace-$traceId"
        is TracerouteUiState.Canceled -> "trace-$traceId"
        is TracerouteUiState.Finished -> "trace-$traceId"
        else -> this::class
    }

@Composable
private fun TraceSessionPanel(
    session: TraceSession,
    onSelectMode: (TracerouteViewMode) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val result = session.result
        if (result == null) {
            TraceProgressHeader(host = session.host, hopCount = session.hops.size, canceling = session.canceling)
        } else {
            TraceOutcomeNotes(session, result)
            TraceStatsSummary(result = result, onClear = onClear)
        }
        TraceViewTabs(viewMode = session.viewMode, onSelectMode = onSelectMode)
        // Crossfade (= AnimatedContent internally, uses SubcomposeLayout) must NOT be
        // nested inside the outer AnimatedContent: when both run at once the outgoing
        // subcomposition reads a disposed State<T> and throws IllegalStateException.
        // A plain when-branch with no animation avoids the nesting entirely.
        when (session.viewMode) {
            TracerouteViewMode.Visual -> {
                HopDetailList(session.hops, finished = !session.live)
            }

            TracerouteViewMode.Map -> {
                TracerouteMapCard(
                    hops = session.hops,
                    live = session.live,
                    stopped = session.stopped,
                    colorFor = ::rttColor,
                )
            }

            TracerouteViewMode.Raw -> {
                RawOutputCard(session.rawOutput)
            }
        }
        // Route geography comes after the hops, which are the answer (PHA-16).
        result?.let { RouteGeographyCard(it) }
    }
}

@Composable
private fun TraceProgressHeader(
    host: String,
    hopCount: Int,
    canceling: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val loadingCd = stringResource(R.string.a11y_loading)
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).semantics { contentDescription = loadingCd },
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text =
                        if (canceling) {
                            stringResource(R.string.traceroute_canceling_title)
                        } else {
                            stringResource(R.string.traceroute_running_title, host)
                        },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text =
                        if (canceling) {
                            stringResource(R.string.traceroute_canceling_subtitle)
                        } else {
                            pluralStringResource(R.plurals.traceroute_running_subtitle, hopCount, hopCount)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TraceOutcomeNotes(
    session: TraceSession,
    result: TracerouteResult,
) {
    if (result.unreachableFromThisDevice) {
        Text(
            text = stringResource(R.string.traceroute_unreachable_from_device),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("traceroute_unreachable_from_device"),
        )
    }
    if (session.canceled) {
        val status =
            if (session.stoppedWhenLeft) {
                R.plurals.traceroute_stopped_when_left_status
            } else {
                R.plurals.traceroute_canceled_partial_status
            }
        Text(
            text = pluralStringResource(status, result.hops.size, result.hops.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.testTag("traceroute_canceled_partial_status"),
        )
    }
    if (session.timeLimitReached) {
        Text(
            text = stringResource(R.string.traceroute_time_limit_reached),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.testTag("traceroute_time_limit_reached"),
        )
    }
}

/**
 * Hop details, map and raw output. TabRow uses SubcomposeLayout, which throws when nested inside the
 * outer AnimatedContent during a state change, so the tabs are a plain Row of clickable surfaces.
 */
@Composable
private fun TraceViewTabs(
    viewMode: TracerouteViewMode,
    onSelectMode: (TracerouteViewMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // One tab per mode, in TracerouteViewMode order: Hop details, Map, Raw output.
        tracerouteViewTabs(modes = TracerouteViewMode.entries).forEachIndexed { index, (icon, label) ->
            val mode = TracerouteViewMode.entries[index]
            val selected = mode == viewMode
            val tint =
                if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { if (!selected) onSelectMode(mode) }
                        .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}

// ── Stats summary ─────────────────────────────────────────────────────────────

@Composable
private fun TraceStatsSummary(
    result: TracerouteResult,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.share_subject_traceroute, result.host)
    val summary = remember(result.hops) { TraceSummary.of(result.hops) }
    val reachedText = if (result.reachedDestination) R.string.traceroute_stat_yes else R.string.traceroute_stat_no
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.traceroute_result_header),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip(
                    label = stringResource(R.string.traceroute_stat_replied),
                    value =
                        stringResource(
                            R.string.traceroute_stat_replied_value,
                            summary.repliedHops,
                            summary.probedHops,
                        ),
                )
                StatChip(
                    label = stringResource(R.string.traceroute_stat_time),
                    value = totalTimeText(result.totalTimeMs),
                )
                StatChip(
                    label = stringResource(R.string.traceroute_stat_reached),
                    value = stringResource(reachedText),
                )
            }
            TraceSummaryLines(summary)
            // An IP target resolves to itself; showing it again repeats the target.
            resolvedIpToShow(result.host, result.resolvedIp)?.let { ip ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.traceroute_resolved_ip, ip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ResultActionsRow(
                copyText = result.rawOutput,
                onShare = { context.shareText(text = result.rawOutput, subject = shareSubject) },
                extraActions = {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.traceroute_clear_button))
                    }
                },
            )
        }
    }
}

/** The RTT worth quoting (the destination's, or else the last reply's) and where replies stopped. */
@Composable
private fun TraceSummaryLines(summary: TraceSummary) {
    val lastReply = summary.lastReply
    val rttLine =
        summary.destination?.let { destination ->
            destination.rttMedianMs?.let {
                val basis = rttBasis(destination.answeredProbes)
                stringResource(R.string.traceroute_summary_destination_rtt, it, basis, destination.hopNumber)
            }
        } ?: lastReply?.let { hop ->
            hop.rttMedianMs?.let {
                stringResource(R.string.traceroute_summary_last_reply, hop.hopNumber, it, rttBasis(hop.answeredProbes))
            }
        }
    val silence =
        summary.silentAfterLastReply?.let { range ->
            when {
                lastReply == null -> {
                    pluralStringResource(R.plurals.traceroute_summary_none_replied, range.last, range.last)
                }

                range.first == range.last -> {
                    stringResource(R.string.traceroute_summary_silent_after_one, lastReply.hopNumber, range.first)
                }

                else -> {
                    stringResource(
                        R.string.traceroute_summary_silent_after,
                        lastReply.hopNumber,
                        range.first,
                        range.last,
                    )
                }
            }
        }
    rttLine?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("traceroute_summary_rtt"),
        )
    }
    silence?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("traceroute_summary_silence"),
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Route geography card ──────────────────────────────────────────────────────

/** Where the located hops are: distance, countries and networks. RTTs are in the summary and hop list. */
@Composable
private fun RouteGeographyCard(result: TracerouteResult) {
    val geoHops = result.geoLocatedHops
    // GeoIP positions contradicted by the measured RTTs make distance and route meaningless.
    val geoConflict = remember(result.hops) { GeoPlausibility.implausibleHops(result.hops).isNotEmpty() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(durationMillis = 500),
        label = "route-geography-alpha",
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth().alpha(cardAlpha).testTag("traceroute_route_geography")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteGeographyHeader()
            HorizontalDivider()
            if (geoHops.isEmpty()) {
                NoGeographyNote()
            } else {
                RouteGeographyStats(geoHops, geoConflict)
            }
        }
    }
}

@Composable
private fun RouteGeographyHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Timeline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.traceroute_stats_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun NoGeographyNote() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.FmdGood,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.traceroute_stats_no_geo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteGeographyStats(
    geoHops: List<HopResult>,
    geoConflict: Boolean,
) {
    val totalDistanceKm: Double =
        remember(geoHops, geoConflict) {
            if (geoConflict || geoHops.size < 2) {
                0.0
            } else {
                geoHops.zipWithNext().sumOf { (a, b) -> GeoPlausibility.distanceKm(a.geoLocation!!, b.geoLocation!!) }
            }
        }
    val countries =
        remember(geoHops) {
            geoHops
                .mapNotNull { it.geoLocation }
                .distinctBy { CountryFlags.countryName(it.countryCode) ?: it.country }
                .map { CountryFlags.label(it.countryCode, it.country) }
        }
    val isps = remember(geoHops) { geoHops.mapNotNull { it.geoLocation?.isp }.distinct() }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (totalDistanceKm > 0) {
            JourneyStatBox(
                icon = Icons.Default.TravelExplore,
                label = stringResource(R.string.traceroute_stats_distance),
                value = formatDistance(totalDistanceKm),
                modifier = Modifier.weight(1f),
            )
        }
        JourneyStatBox(
            icon = Icons.Default.Public,
            label = stringResource(R.string.traceroute_stats_countries),
            value = "${countries.size}",
            modifier = Modifier.weight(1f),
        )
        if (isps.isNotEmpty()) {
            JourneyStatBox(
                icon = Icons.Default.Router,
                label = stringResource(R.string.traceroute_stats_isps),
                value = "${isps.size}",
                modifier = Modifier.weight(1f),
            )
        }
    }
    RouteGeographyNotes(countries.takeIf { it.size > 1 && !geoConflict }, geoConflict)
}

@Composable
private fun RouteGeographyNotes(
    countryRoute: List<String>?,
    geoConflict: Boolean,
) {
    countryRoute?.let {
        Text(
            stringResource(R.string.traceroute_stats_country_path, it.joinToString(" → ")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.traceroute_stats_geo_approximate),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (geoConflict) {
        Text(
            stringResource(R.string.traceroute_stats_geo_conflict),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun JourneyStatBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDistance(km: Double): String =
    when {
        km >= 10_000 -> "${(km / 1_000).roundToInt()}k km"
        km >= 1_000 -> "${"%.1f".format(km / 1_000)}k km"
        else -> "${km.roundToInt()} km"
    }

private fun rttColor(rtTimeMs: Long?): Color =
    when (TraceLatencyBand.of(rtTimeMs)) {
        TraceLatencyBand.UNKNOWN -> StatusUnknown
        TraceLatencyBand.GOOD -> StatusGood
        TraceLatencyBand.FAIR -> StatusLime
        TraceLatencyBand.SLOW -> StatusBad
        TraceLatencyBand.CRITICAL -> StatusCritical
    }

// ── Hop detail list ───────────────────────────────────────────────────────────

/**
 * One row per hop in TTL order, with runs of silent hops folded into one row. Rows are keyed so a
 * running trace only fades in what is new, and the same rows carry over when the trace finishes.
 */
@Composable
private fun HopDetailList(
    hops: List<HopResult>,
    finished: Boolean,
) {
    val rows = remember(hops, finished) { hopListRows(hops, finished) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, row ->
            key(row.key) {
                // animateFloatAsState rather than AnimatedVisibility: a plain value animation is safe to
                // interrupt when the outer AnimatedContent exits mid-animation.
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val rowAlpha by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = AppMotion.enter(durationMillis = 250),
                    label = "hop-alpha",
                )
                Box(Modifier.alpha(rowAlpha)) {
                    when (row) {
                        is HopListRow.Hop -> HopCard(hop = row.hop, index = index)
                        is HopListRow.NoReplyRun -> NoReplyRunCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoReplyRunCard(run: HopListRow.NoReplyRun) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("traceroute_no_reply_run")) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "* * *",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text =
                        if (run.count == 1) {
                            stringResource(R.string.traceroute_run_one, run.firstHop)
                        } else {
                            stringResource(R.string.traceroute_run_range, run.firstHop, run.lastHop)
                        },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    text =
                        if (run.afterLastReply) {
                            stringResource(R.string.traceroute_run_after_last_reply)
                        } else {
                            pluralStringResource(R.plurals.traceroute_run_no_reply, run.count, run.count)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HopCard(
    hop: HopResult,
    index: Int,
) {
    val hopColor = rttColor(hop.rttMedianMs)
    var expanded by remember(hop.hopNumber) { mutableStateOf(false) }
    // animateFloatAsState + animateContentSize replace AnimatedVisibility(expandVertically/shrinkVertically).
    // AnimatedVisibility with size enter/exit specs uses SubcomposeLayout internally; nested inside
    // AnimatedContent (and Crossfade which is itself AnimatedContent) it causes IllegalStateException
    // when the outer animation exits while the inner expand/collapse animation is in progress.
    // animateContentSize is a plain Modifier that never uses SubcomposeLayout.
    val expandedAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = AppMotion.effect(200),
        label = "expanded-alpha",
    )

    OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { role = Role.Button },
    ) {
        Column(modifier = Modifier.animateContentSize(animationSpec = AppMotion.effect(200))) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hop number badge
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(hopColor.copy(alpha = 0.15f))
                            .border(1.5.dp, hopColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${hop.hopNumber}",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = hopColor,
                            ),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Main info
                Column(modifier = Modifier.weight(1f)) {
                    when (hop.status) {
                        HopStatus.TIMEOUT -> {
                            Text(
                                text = "* * *",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            )
                            Text(
                                stringResource(R.string.traceroute_hop_timeout),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HopStatus.ERROR -> {
                            Text(
                                hop.ip ?: stringResource(R.string.traceroute_hop_detail_status_error),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        HopStatus.SUCCESS -> {
                            Text(
                                hop.ip ?: "",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hop.hostname != null) {
                                Text(
                                    hop.hostname!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (hop.geoLocation != null) {
                                GeoLocationChip(hop.geoLocation!!)
                            }
                        }
                    }
                    if (hop.probeRttsMs.size > 1) {
                        val probeRttFormat = stringResource(R.string.traceroute_probe_rtt_value)
                        val probeNoReply = stringResource(R.string.traceroute_probe_no_reply)
                        val probeLabel = stringResource(R.string.traceroute_hop_probe_rtts)
                        val listSeparator = stringResource(R.string.traceroute_list_separator)
                        val probeSuccessColor = MaterialTheme.colorScheme.primary
                        val probeTimeoutColor = MaterialTheme.colorScheme.outline
                        val probeDescriptions = mutableListOf<String>()
                        for ((index, rtt) in hop.probeRttsMs.withIndex()) {
                            probeDescriptions +=
                                if (rtt == null) {
                                    stringResource(R.string.traceroute_probe_no_reply_description, index + 1)
                                } else {
                                    stringResource(R.string.traceroute_probe_success_description, index + 1, rtt)
                                }
                        }
                        val probesText =
                            buildAnnotatedString {
                                append(probeLabel)
                                hop.probeRttsMs.forEach { rtt ->
                                    append(listSeparator)
                                    withStyle(
                                        SpanStyle(
                                            color =
                                                if (rtt == null) {
                                                    probeTimeoutColor
                                                } else {
                                                    probeSuccessColor
                                                },
                                        ),
                                    ) {
                                        append(if (rtt == null) "○" else "●")
                                    }
                                    append(" ")
                                    append(
                                        rtt?.let {
                                            java.lang.String.format(java.util.Locale.getDefault(), probeRttFormat, it)
                                        } ?: probeNoReply,
                                    )
                                }
                            }
                        Text(
                            text = probesText,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = probeDescriptions.joinToString(", ")
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val min = hop.rttMinMs
                        val avg = hop.rttAvgMs
                        val max = hop.rttMaxMs
                        if (min != null && avg != null && max != null) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.traceroute_hop_rtt_stats,
                                        min,
                                        avg,
                                        max,
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // RTT badge + expand chevron
                Column(horizontalAlignment = Alignment.End) {
                    hop.rttMedianMs?.let { median ->
                        Surface(
                            color = hopColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "${median}ms",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = hopColor,
                                    ),
                            )
                        }
                        Text(
                            text = rttBasis(hop.answeredProbes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expanded detail section – rendered while alpha > 0 so the fade-out
            // plays fully; animateContentSize on the parent Column handles height.
            if (expanded || expandedAlpha > 0f) {
                Column(
                    modifier =
                        Modifier
                            .alpha(expandedAlpha)
                            .padding(start = 60.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                    hop.ip?.let {
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_ip), it)
                    }
                    hop.hostname?.let {
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_hostname), it)
                    }
                    hop.rttMedianMs?.let {
                        val rtt = "${it}ms (${rttBasis(hop.answeredProbes)})"
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_rtt), rtt)
                    }
                    HopDetailRow(
                        label = stringResource(R.string.traceroute_hop_detail_status),
                        value =
                            when (hop.status) {
                                HopStatus.SUCCESS -> stringResource(R.string.traceroute_hop_detail_status_success)
                                HopStatus.TIMEOUT -> stringResource(R.string.traceroute_hop_detail_status_timeout)
                                HopStatus.ERROR -> stringResource(R.string.traceroute_hop_detail_status_error)
                            },
                    )
                    hop.geoLocation?.let { geo ->
                        val location =
                            hopLocationLabel(
                                // Shared with the route map callout, so both show the same text.
                                geo,
                            )
                        HopDetailRow(stringResource(R.string.traceroute_hop_detail_location), location)
                        geo.isp?.let { HopDetailRow(stringResource(R.string.traceroute_hop_detail_isp), it) }
                        geo.asn?.let { HopDetailRow(stringResource(R.string.traceroute_hop_detail_asn), it) }
                        HopDetailRow(
                            label = stringResource(R.string.traceroute_hop_detail_coordinates),
                            value = "%.4f, %.4f".format(geo.lat, geo.lon),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HopDetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GeoLocationChip(geo: HopGeoLocation) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            Icons.Default.FmdGood,
            null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(3.dp))
        val location =
            hopLocationLabel(
                // Shared with the route map callout, so both show the same text.
                geo,
            )
        Text(
            text = location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (geo.asn != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "· ${geo.asn}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Raw output card ───────────────────────────────────────────────────────────

@Composable
private fun RawOutputCard(rawOutput: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.traceroute_raw_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Icon(
                    Icons.Default.Info,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.heightIn(max = 280.dp)) {
                SelectionContainer {
                    Text(
                        text = rawOutput,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ── Error panel ───────────────────────────────────────────────────────────────

@Composable
private fun TracerouteErrorPanel(
    state: TracerouteUiState.Error,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    ToolErrorCard(
        title = stringResource(R.string.traceroute_error_title),
        message = state.text.asString(),
    ) {
        FilledTonalButton(onClick = onClear, modifier = Modifier.testTag("traceroute_error_clear")) {
            Text(stringResource(R.string.traceroute_clear_button))
        }
        ToolRetryButton(
            text = stringResource(R.string.traceroute_retry_button),
            onClick = onRetry,
            modifier = Modifier.testTag("traceroute_error_retry"),
        )
    }
}
