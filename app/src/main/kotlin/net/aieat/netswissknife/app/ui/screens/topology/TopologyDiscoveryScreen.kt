package net.aieat.netswissknife.app.ui.screens.topology

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.ui.components.AppModalBottomSheet
import net.aieat.netswissknife.app.ui.components.DisabledActionHint
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.components.rememberLocalNetworkPermissionRequester
import net.aieat.netswissknife.app.ui.components.scrollIntoViewOnFocus
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.topology.*
import java.util.Locale
import kotlin.math.*

@Composable
fun TopologyDiscoveryScreen(viewModel: TopologyDiscoveryViewModel = hiltViewModel()) {
    val requestLocalNetworkPermission = rememberLocalNetworkPermissionRequester()
    LaunchedEffect(Unit) { requestLocalNetworkPermission() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val recentSeeds by viewModel.recentSeeds.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { it / 8 },
    ) {
        TopologyScreenContent(
            uiState = uiState,
            networkStatus = networkStatus,
            onGrantPermission = requestLocalNetworkPermission,
            recentSeeds = recentSeeds,
            initialSeed = viewModel.restoredSeed.orEmpty(),
            onStartDiscovery = { params -> viewModel.startDiscovery(params) },
            onRemoveRecentSeed = { seed -> viewModel.removeRecentSeed(seed) },
            onClearRecentSeeds = { viewModel.clearRecentSeeds() },
            onSelectNode = { ip -> viewModel.selectNode(ip) },
            onDeselectNode = { viewModel.deselectNode() },
            onReset = { viewModel.reset() },
            onRetryDiscovery = { params -> viewModel.retryDiscovery(params) },
            onHelpClick = { showHelp = true },
        )
    }

    if (showHelp) {
        net.aieat.netswissknife.app.ui.components.ToolHelpSheet(
            title = stringResource(R.string.help_topology_title),
            conceptHeading = stringResource(R.string.help_topology_concept_heading),
            conceptBody = stringResource(R.string.help_topology_concept_body),
            sections =
                listOf(
                    net.aieat.netswissknife.app.ui.components
                        .HelpSection(
                            stringResource(R.string.help_topology_what_heading),
                            stringResource(R.string.help_topology_what_body),
                        ),
                    net.aieat.netswissknife.app.ui.components.HelpSection(
                        heading = stringResource(R.string.help_topology_params_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_topology_params_bullets).toList(),
                    ),
                    net.aieat.netswissknife.app.ui.components.HelpSection(
                        heading = stringResource(R.string.help_topology_results_heading),
                        body = "",
                        bullets = stringArrayResource(R.array.help_topology_results_bullets).toList(),
                    ),
                ),
            onDismiss = { showHelp = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopologyScreenContent(
    uiState: TopologyUiState,
    networkStatus: net.aieat.netswissknife.app.platform.NetworkStatus,
    onGrantPermission: () -> Unit,
    recentSeeds: List<String>,
    initialSeed: String,
    onStartDiscovery: (TopologyParams) -> Unit,
    onRemoveRecentSeed: (String) -> Unit,
    onClearRecentSeeds: () -> Unit,
    onSelectNode: (String) -> Unit,
    onDeselectNode: () -> Unit,
    onReset: () -> Unit,
    onRetryDiscovery: (TopologyParams) -> Unit,
    onHelpClick: () -> Unit = {},
) {
    // Non-secret form inputs survive rotation (rememberSaveable). Community string and v3
    // passwords stay in plain remember so credentials never reach the saved-state Bundle.
    var targetIp by rememberSaveable { mutableStateOf(initialSeed) }
    var snmpVersion by rememberSaveable { mutableStateOf(SnmpVersion.V2C) }
    var community by remember { mutableStateOf("public") }
    var v3Username by rememberSaveable { mutableStateOf("") }
    var v3AuthProto by rememberSaveable { mutableStateOf(V3AuthProtocol.NONE) }
    var v3AuthPassword by remember { mutableStateOf("") }
    var v3PrivProto by rememberSaveable { mutableStateOf(V3PrivProtocol.NONE) }
    var v3PrivPassword by remember { mutableStateOf("") }
    var maxHops by rememberSaveable { mutableStateOf(3f) }
    var queryDiscoveredNeighbors by rememberSaveable { mutableStateOf(false) }
    var configExpanded by rememberSaveable { mutableStateOf(true) }
    var showCommunityPassword by remember { mutableStateOf(false) }
    var showAuthPassword by remember { mutableStateOf(false) }
    var showPrivPassword by remember { mutableStateOf(false) }
    var nodeSearch by rememberSaveable { mutableStateOf("") }

    val isDiscovering = uiState is TopologyUiState.Discovering
    val isCanceling = uiState is TopologyUiState.Canceling
    val isInProgress = isDiscovering || isCanceling
    val normalizedTargetIp = HostValidator.normalize(targetIp)
    val isTargetIpInvalid = targetIp.isNotBlank() && normalizedTargetIp == null
    // Name the specific reason (spaces, bad characters, bad label...) rather than one generic message.
    val targetIpErrorRes: Int? =
        if (isTargetIpInvalid) {
            HostValidator.problemOf(targetIp)?.messageRes() ?: R.string.error_host_invalid
        } else {
            null
        }
    val dismissKeyboard = rememberDismissKeyboard()
    val canStartDiscovery =
        !isInProgress && normalizedTargetIp != null &&
            !(v3PrivProto != V3PrivProtocol.NONE && v3AuthProto == V3AuthProtocol.NONE)
    val maxConfigHeight = LocalConfiguration.current.screenHeightDp.dp * 0.4f

    fun currentTopologyParams() =
        TopologyParams(
            targetIp = normalizedTargetIp ?: targetIp,
            snmpVersion = snmpVersion,
            communityString = community,
            v3Username = v3Username.ifBlank { null },
            v3AuthPassword = v3AuthPassword.ifBlank { null },
            v3PrivPassword = v3PrivPassword.ifBlank { null },
            v3AuthProtocol = v3AuthProto,
            v3PrivProtocol = v3PrivProto,
            maxHops = maxHops.toInt(),
            queryDiscoveredNeighbors = queryDiscoveredNeighbors,
            timeoutMs = 3000,
            retries = 1,
        )

    // Shared by the Discover button and the keyboard Done action, so both start the same scan.
    fun startDiscoveryIfReady() {
        if (!canStartDiscovery) return
        onStartDiscovery(currentTopologyParams())
        configExpanded = false
    }

    val selectedNode =
        when (uiState) {
            is TopologyUiState.Discovering -> uiState.nodes.find { it.ip == uiState.selectedNodeIp }
            is TopologyUiState.Canceling -> uiState.nodes.find { it.ip == uiState.selectedNodeIp }
            is TopologyUiState.Canceled -> uiState.nodes.find { it.ip == uiState.selectedNodeIp }
            is TopologyUiState.TimeLimit -> uiState.nodes.find { it.ip == uiState.selectedNodeIp }
            is TopologyUiState.Done -> uiState.graph.nodes.find { it.ip == uiState.selectedNodeIp }
            else -> null
        }
    val announcementPhase =
        when (val state = uiState) {
            is TopologyUiState.Idle -> {
                null
            }

            is TopologyUiState.Discovering, is TopologyUiState.Canceling -> {
                ToolAnnouncementPhase.RUNNING
            }

            is TopologyUiState.Canceled -> {
                if (state.nodes.isNotEmpty() || state.links.isNotEmpty()) {
                    ToolAnnouncementPhase.PARTIAL
                } else {
                    ToolAnnouncementPhase.CANCELED
                }
            }

            is TopologyUiState.TimeLimit -> {
                ToolAnnouncementPhase.PARTIAL
            }

            is TopologyUiState.Done -> {
                if (state.graph.truncationReasons.isNotEmpty() || state.graph.hadSnmpErrors) {
                    ToolAnnouncementPhase.PARTIAL
                } else {
                    ToolAnnouncementPhase.FINISHED
                }
            }

            is TopologyUiState.Failure -> {
                ToolAnnouncementPhase.ERROR
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        ToolStateAnnouncer(stringResource(R.string.help_topology_title), announcementPhase)
        ToolHeroHeader(
            title = stringResource(R.string.topology_screen_title),
            subtitle = stringResource(R.string.topology_screen_subtitle),
            icon = Icons.Default.Hub,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            onHelpClick = onHelpClick,
        )

        NetworkStatusBanner(
            status = networkStatus,
            scope = NetworkStatusScope.LOCAL_NETWORK,
            permissionDenied = (uiState as? TopologyUiState.Failure)?.networkErrorKind == NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
            onGrantPermission = onGrantPermission,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── SNMP config card ──────────────────────────────────────────────────
        ElevatedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(max = maxConfigHeight),
            shape = AppShapes.large,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { configExpanded = !configExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.topology_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val subtitle = topologyConfigSubtitle(targetIp, snmpVersion.name.lowercase())
                        if (!configExpanded && subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                AnimatedVisibility(visible = configExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        ToolTextField(
                            value = targetIp,
                            onValueChange = { targetIp = it },
                            label = { Text(stringResource(R.string.topology_target_ip_label)) },
                            leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                            isError = isTargetIpInvalid,
                            supportingText =
                                if (targetIpErrorRes != null) {
                                    { Text(stringResource(targetIpErrorRes)) }
                                } else {
                                    null
                                },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        dismissKeyboard()
                                        startDiscoveryIfReady()
                                    },
                                ),
                        )

                        RecentHostsRow(
                            recentHosts = recentSeeds,
                            onHostSelected = { targetIp = it },
                            onRemoveHost = onRemoveRecentSeed,
                            onClearAll = onClearRecentSeeds,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.topology_snmp_version_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SnmpVersion.entries.forEachIndexed { index, version ->
                                SegmentedButton(
                                    selected = snmpVersion == version,
                                    onClick = { snmpVersion = version },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SnmpVersion.entries.size),
                                    label = {
                                        Text(
                                            when (version) {
                                                SnmpVersion.V1 -> "v1"
                                                SnmpVersion.V2C -> "v2c"
                                                SnmpVersion.V3 -> "v3"
                                            },
                                        )
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AnimatedVisibility(visible = snmpVersion != SnmpVersion.V3) {
                            OutlinedTextField(
                                value = community,
                                onValueChange = { community = it },
                                label = { Text(stringResource(R.string.topology_community_label)) },
                                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                                singleLine = true,
                                visualTransformation =
                                    if (showCommunityPassword) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                trailingIcon = {
                                    IconButton(onClick = { showCommunityPassword = !showCommunityPassword }) {
                                        Icon(
                                            if (showCommunityPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = stringResource(if (showCommunityPassword) R.string.action_hide_password else R.string.action_show_password),
                                        )
                                    }
                                },
                            )
                        }

                        AnimatedVisibility(visible = snmpVersion == SnmpVersion.V3) {
                            Column {
                                OutlinedTextField(
                                    value = v3Username,
                                    onValueChange = { v3Username = it },
                                    label = { Text(stringResource(R.string.topology_v3_username_label)) },
                                    modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                                    singleLine = true,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                V3ProtocolDropdown(
                                    label = stringResource(R.string.topology_v3_auth_proto_label),
                                    options = V3AuthProtocol.entries,
                                    selected = v3AuthProto,
                                    labelFor = { stringResource(v3AuthProtocolLabel(it)) },
                                    onSelect = { v3AuthProto = it },
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                AnimatedVisibility(visible = v3AuthProto != V3AuthProtocol.NONE) {
                                    OutlinedTextField(
                                        value = v3AuthPassword,
                                        onValueChange = { v3AuthPassword = it },
                                        label = { Text(stringResource(R.string.topology_v3_auth_password_label)) },
                                        modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                                        singleLine = true,
                                        visualTransformation =
                                            if (showAuthPassword) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                        trailingIcon = {
                                            IconButton(onClick = { showAuthPassword = !showAuthPassword }) {
                                                Icon(
                                                    if (showAuthPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = stringResource(if (showAuthPassword) R.string.action_hide_password else R.string.action_show_password),
                                                )
                                            }
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                V3ProtocolDropdown(
                                    label = stringResource(R.string.topology_v3_priv_proto_label),
                                    options = V3PrivProtocol.entries,
                                    selected = v3PrivProto,
                                    labelFor = { stringResource(v3PrivProtocolLabel(it)) },
                                    onSelect = { v3PrivProto = it },
                                )
                                AnimatedVisibility(
                                    visible = v3PrivProto != V3PrivProtocol.NONE && v3AuthProto == V3AuthProtocol.NONE,
                                ) {
                                    Text(
                                        text = stringResource(R.string.topology_v3_priv_requires_auth),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                AnimatedVisibility(visible = v3PrivProto != V3PrivProtocol.NONE) {
                                    OutlinedTextField(
                                        value = v3PrivPassword,
                                        onValueChange = { v3PrivPassword = it },
                                        label = { Text(stringResource(R.string.topology_v3_priv_password_label)) },
                                        modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                                        singleLine = true,
                                        visualTransformation =
                                            if (showPrivPassword) {
                                                VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                        trailingIcon = {
                                            IconButton(onClick = { showPrivPassword = !showPrivPassword }) {
                                                Icon(
                                                    if (showPrivPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = stringResource(if (showPrivPassword) R.string.action_hide_password else R.string.action_show_password),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.topology_max_hops_label, maxHops.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val minimumHops = TopologyParamsValidator.MIN_MAX_HOPS.toFloat()
                        val maximumHops = TopologyParamsValidator.MAX_MAX_HOPS.toFloat()
                        Slider(
                            value = maxHops,
                            onValueChange = { maxHops = it },
                            valueRange = minimumHops..maximumHops,
                            steps =
                                TopologyParamsValidator.MAX_MAX_HOPS -
                                    TopologyParamsValidator.MIN_MAX_HOPS - 1,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        NeighborQueryOption(
                            checked = queryDiscoveredNeighbors,
                            onCheckedChange = { queryDiscoveredNeighbors = it },
                        )
                    }
                }
            }
        }

        // Primary actions live outside the scrolling configuration card so Discover (and
        // Cancel while a scan runs) stay visible whatever the card's scroll position is.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Button(
                onClick = { startDiscoveryIfReady() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canStartDiscovery,
            ) {
                Text(stringResource(R.string.topology_discover_button))
            }

            if (!canStartDiscovery && !isInProgress) {
                DisabledActionHint(
                    stringResource(
                        if (normalizedTargetIp == null) {
                            R.string.topology_discover_disabled_target
                        } else {
                            R.string.topology_discover_disabled_v3
                        },
                    ),
                )
            }

            AnimatedVisibility(visible = isInProgress) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = isDiscovering,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    if (isCanceling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            if (isCanceling) {
                                R.string.topology_canceling_button
                            } else {
                                R.string.topology_cancel_button
                            },
                        ),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            val retainedGraph =
                when (val state = uiState) {
                    is TopologyUiState.Done -> state.graph
                    is TopologyUiState.Discovering -> retainedTopologyGraph(state.nodes, state.links)
                    is TopologyUiState.Canceling -> retainedTopologyGraph(state.nodes, state.links)
                    is TopologyUiState.Canceled -> retainedTopologyGraph(state.nodes, state.links)
                    is TopologyUiState.TimeLimit -> retainedTopologyGraph(state.nodes, state.links)
                    else -> null
                }
            retainedGraph?.let { graph ->
                val reportStatus =
                    when (uiState) {
                        is TopologyUiState.Discovering -> stringResource(R.string.topology_report_status_running)
                        is TopologyUiState.Canceling -> stringResource(R.string.topology_report_status_stopping)
                        is TopologyUiState.Canceled -> stringResource(R.string.topology_report_status_canceled)
                        is TopologyUiState.TimeLimit -> stringResource(R.string.topology_report_status_time_limit)
                        else -> null
                    }
                TopologyResultUtilities(
                    graph = graph,
                    reportStatus = reportStatus,
                    nodeSearch = nodeSearch,
                    onNodeSearchChange = { nodeSearch = it },
                    onSelectNode = onSelectNode,
                )
            }
            AnimatedContent(
                targetState = uiState,
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(AppMotion.enter(300)) togetherWith fadeOut(AppMotion.exit(300))
                },
                label = "topology_state",
                modifier = Modifier.weight(1f),
            ) { state ->
                when (state) {
                    is TopologyUiState.Idle -> {
                        IdleContent()
                    }

                    is TopologyUiState.Discovering -> {
                        TopologyProgressContent(
                            nodes = state.nodes,
                            links = state.links,
                            nodesDone = state.nodesDone,
                            progressMessage = state.progressMessage,
                            selectedNodeIp = state.selectedNodeIp,
                            onNodeTap = onSelectNode,
                        )
                    }

                    is TopologyUiState.Canceling -> {
                        TopologyProgressContent(
                            nodes = state.nodes,
                            links = state.links,
                            nodesDone = state.nodesDone,
                            statusMessage = stringResource(R.string.topology_canceling_status),
                            selectedNodeIp = state.selectedNodeIp,
                            onNodeTap = onSelectNode,
                        )
                    }

                    is TopologyUiState.Canceled -> {
                        TopologyProgressContent(
                            nodes = state.nodes,
                            links = state.links,
                            nodesDone = state.nodesDone,
                            statusMessage =
                                pluralStringResource(
                                    R.plurals.topology_canceled_partial_status,
                                    state.nodesDone,
                                    state.nodesDone,
                                ),
                            selectedNodeIp = state.selectedNodeIp,
                            onNodeTap = onSelectNode,
                            onClearPartialResults = onReset,
                        )
                    }

                    is TopologyUiState.TimeLimit -> {
                        TopologyProgressContent(
                            nodes = state.nodes,
                            links = state.links,
                            nodesDone = state.nodesDone,
                            statusMessage = stringResource(R.string.topology_time_limit_partial_status),
                            selectedNodeIp = state.selectedNodeIp,
                            onNodeTap = onSelectNode,
                            onClearPartialResults = onReset,
                        )
                    }

                    is TopologyUiState.Done -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            TopologyCanvas(
                                nodes = state.graph.nodes,
                                links = state.graph.links,
                                selectedNodeIp = state.selectedNodeIp,
                                onNodeTap = onSelectNode,
                            )
                            if (state.graph.truncationReasons.isNotEmpty() || state.graph.hadSnmpErrors) {
                                Surface(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.topology_partial_results),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is TopologyUiState.Failure -> {
                        ErrorContent(
                            message =
                                when {
                                    state.isBudgetLimit -> stringResource(R.string.topology_budget_exceeded)
                                    state.messageRes != null -> stringResource(state.messageRes)
                                    else -> state.message
                                },
                            retryEnabled = canStartDiscovery,
                            onRetry = {
                                onRetryDiscovery(currentTopologyParams())
                                configExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (selectedNode != null) {
        val queriedNodes =
            when (uiState) {
                is TopologyUiState.Done -> uiState.graph.nodes
                is TopologyUiState.Discovering -> uiState.nodes
                is TopologyUiState.Canceling -> uiState.nodes
                is TopologyUiState.Canceled -> uiState.nodes
                is TopologyUiState.TimeLimit -> uiState.nodes
                else -> emptyList()
            }
        NodeDetailSheet(
            node = selectedNode,
            queriedNodeIps = queriedNodes.mapTo(mutableSetOf()) { it.ip },
            links =
                when (uiState) {
                    is TopologyUiState.Done -> {
                        uiState.graph.links.filter {
                            it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                        }
                    }

                    is TopologyUiState.Discovering -> {
                        uiState.links.filter {
                            it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                        }
                    }

                    is TopologyUiState.Canceling -> {
                        uiState.links.filter {
                            it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                        }
                    }

                    is TopologyUiState.Canceled -> {
                        uiState.links.filter {
                            it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                        }
                    }

                    is TopologyUiState.TimeLimit -> {
                        uiState.links.filter {
                            it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                        }
                    }

                    else -> {
                        emptyList()
                    }
                },
            onDismiss = onDeselectNode,
            onNavigateToNeighbour = onSelectNode,
        )
    }
}

private fun retainedTopologyGraph(
    nodes: List<TopologyNode>,
    links: List<TopologyLink>,
): TopologyGraph? =
    nodes.takeIf { it.isNotEmpty() }?.let {
        TopologyGraph(nodes = nodes, links = links, seedIp = nodes.first().ip, queriedAt = 0L)
    }

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun TopologyResultUtilities(
    graph: TopologyGraph,
    reportStatus: String?,
    nodeSearch: String,
    onNodeSearchChange: (String) -> Unit,
    onSelectNode: (String) -> Unit,
) {
    val context = LocalContext.current
    val reportLabels =
        TopologyReportLabels(
            title = stringResource(R.string.topology_report_title),
            queried = stringResource(R.string.topology_report_queried),
            counts = stringResource(R.string.topology_report_counts),
            truncated = stringResource(R.string.topology_report_truncated),
            snmpErrors = stringResource(R.string.topology_report_snmp_errors),
            vendor = stringResource(R.string.topology_report_vendor),
            model = stringResource(R.string.topology_report_model),
            snmp = stringResource(R.string.topology_report_snmp),
            reachable = stringResource(R.string.topology_report_reachable),
            unavailable = stringResource(R.string.topology_report_unavailable),
            interfaceLine = stringResource(R.string.topology_report_interface),
            vlanLine = stringResource(R.string.topology_report_vlan),
        )
    val copiedMessage = stringResource(R.string.lan_copied)
    val shareSubject = stringResource(R.string.topology_screen_title)
    val clipLabel = stringResource(R.string.topology_screen_title)
    val matches =
        remember(graph, nodeSearch) {
            graph.nodes.filter { node ->
                nodeSearch.isNotBlank() &&
                    listOfNotNull(node.ip, node.sysName, node.vendor, node.model)
                        .any { it.contains(nodeSearch, ignoreCase = true) }
            }
        }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = nodeSearch,
                onValueChange = onNodeSearchChange,
                label = { Text(stringResource(R.string.topology_search_nodes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
            )
            DropdownMenu(
                expanded = matches.isNotEmpty(),
                onDismissRequest = { onNodeSearchChange("") },
                properties =
                    androidx.compose.ui.window
                        .PopupProperties(focusable = false),
            ) {
                matches.take(8).forEach { node ->
                    DropdownMenuItem(
                        text = { Text("${node.sysName ?: node.ip} · ${node.ip}") },
                        onClick = {
                            onSelectNode(node.ip)
                            onNodeSearchChange("")
                        },
                    )
                }
            }
        }
        TextButton(onClick = {
            val report = topologyReport(graph, reportStatus, reportLabels)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, report))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.action_copy)) }
        TextButton(onClick = {
            context.shareText(topologyReport(graph, reportStatus, reportLabels), shareSubject)
        }) {
            Text(stringResource(R.string.action_share))
        }
    }
}

/** Localized templates for [topologyReport]. Each template uses positional `%n$s` / `%n$d` specifiers. */
internal data class TopologyReportLabels(
    val title: String,
    val queried: String,
    val counts: String,
    val truncated: String,
    val snmpErrors: String,
    val vendor: String,
    val model: String,
    val snmp: String,
    val reachable: String,
    val unavailable: String,
    val interfaceLine: String,
    val vlanLine: String,
)

internal fun topologyReport(
    graph: TopologyGraph,
    status: String?,
    labels: TopologyReportLabels,
): String =
    buildString {
        appendLine(labels.title.format(graph.seedIp))
        if (graph.queriedAt > 0) appendLine(labels.queried.format(java.util.Date(graph.queriedAt).toString()))
        status?.let { appendLine(it) }
        appendLine(labels.counts.format(graph.nodes.size, graph.links.size))
        if (graph.truncationReasons.isNotEmpty()) {
            appendLine(labels.truncated.format(graph.truncationReasons.joinToString()))
        }
        if (graph.hadSnmpErrors) appendLine(labels.snmpErrors)
        graph.nodes.forEach { node ->
            appendTopologyNode(node, labels)
        }
        graph.links.forEach { link ->
            appendLine(
                "\n${link.fromIp}:${link.fromPort.orEmpty()} ↔ " +
                    "${link.toIp}:${link.toPort.orEmpty()} (${link.protocol})",
            )
        }
    }

private fun StringBuilder.appendTopologyNode(
    node: TopologyNode,
    labels: TopologyReportLabels,
) {
    appendLine("\n${node.sysName ?: node.ip} (${node.ip})")
    node.vendor?.let { appendLine(labels.vendor.format(it)) }
    node.model?.let { appendLine(labels.model.format(it)) }
    val reachability = if (node.snmpReachable) labels.reachable else labels.unavailable
    appendLine(labels.snmp.format(reachability))
    node.tableObservations.forEach { (table, observation) ->
        val failures =
            observation.failures
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " (", postfix = ")")
                .orEmpty()
        appendLine("${table.name}: ${observation.completeness}$failures")
    }
    node.interfaces.forEach { iface -> appendLine(labels.interfaceLine.format(iface.name, iface.operStatus.name)) }
    node.vlans.forEach { vlan -> appendLine(labels.vlanLine.format(vlan.id, vlan.name)) }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NeighborQueryOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("topology_query_discovered_neighbors")
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = stringResource(R.string.topology_query_neighbors_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.topology_query_neighbors_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopologyProgressContent(
    nodes: List<TopologyNode>,
    links: List<TopologyLink>,
    nodesDone: Int,
    progressMessage: String? = null,
    statusMessage: String? = null,
    selectedNodeIp: String? = null,
    onNodeTap: (String) -> Unit,
    onClearPartialResults: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TopologyCanvas(
            nodes = nodes,
            links = links,
            selectedNodeIp = selectedNodeIp,
            onNodeTap = onNodeTap,
        )
        if (progressMessage != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
            ) {
                ScanningBadge(
                    countMessage =
                        pluralStringResource(
                            R.plurals.topology_scanning_badge,
                            nodesDone,
                            nodesDone,
                        ),
                    progressMessage = progressMessage,
                )
            }
        }
        if (statusMessage != null) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)
                    if (onClearPartialResults != null) {
                        TextButton(
                            onClick = onClearPartialResults,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("topology_clear_partial_results"),
                        ) {
                            Text(stringResource(R.string.topology_clear_partial_results))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> V3ProtocolDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = labelFor(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun v3AuthProtocolLabel(protocol: V3AuthProtocol): Int =
    when (protocol) {
        V3AuthProtocol.NONE -> R.string.topology_v3_auth_none
        V3AuthProtocol.MD5 -> R.string.topology_v3_auth_md5
        V3AuthProtocol.SHA -> R.string.topology_v3_auth_sha1
        V3AuthProtocol.SHA256 -> R.string.topology_v3_auth_sha256
        V3AuthProtocol.SHA512 -> R.string.topology_v3_auth_sha512
    }

private fun v3PrivProtocolLabel(protocol: V3PrivProtocol): Int =
    when (protocol) {
        V3PrivProtocol.NONE -> R.string.topology_v3_priv_none
        V3PrivProtocol.DES -> R.string.topology_v3_priv_des
        V3PrivProtocol.AES128 -> R.string.topology_v3_priv_aes128
        V3PrivProtocol.AES192 -> R.string.topology_v3_priv_aes192
        V3PrivProtocol.AES256 -> R.string.topology_v3_priv_aes256
    }

@Composable
private fun IdleContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.size(120.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2,
                    ),
                radius = size.minDimension / 2,
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = size.minDimension * 0.35f,
                center = center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Icon(
            imageVector = Icons.Default.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.topology_idle_tagline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.topology_idle_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    // Top-aligned: the failure card sits directly under Discover instead of floating mid-slot.
    // Scrolls when the slot is short, so a long message is never cut off (V6A-05).
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        ElevatedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            shape = AppShapes.large,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.topology_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(16.dp))
                ToolRetryButton(
                    text = stringResource(R.string.topology_error_retry),
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier.testTag("topology_error_retry"),
                )
            }
        }
    }
}

@Composable
private fun ScanningBadge(
    countMessage: String,
    progressMessage: String,
) {
    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.widthIn(max = 240.dp)) {
                Text(
                    text = countMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (progressMessage.isNotBlank()) {
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopologyCanvas(
    nodes: List<TopologyNode>,
    links: List<TopologyLink>,
    selectedNodeIp: String?,
    onNodeTap: (String) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val pxPerDp = with(density) { 1.dp.toPx() }
    val fitMarginPx = with(density) { 24.dp.toPx() }
    val minHitRadiusPx = with(density) { TopologyCanvasGeometry.MIN_HIT_RADIUS_DP.dp.toPx() }

    var graphOffset by remember { mutableStateOf(Offset.Zero) }
    var graphScale by remember { mutableFloatStateOf(1f) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val queriedIps = nodes.map { it.ip }.toSet()
    val reportedIps =
        links
            .flatMap { listOf(it.fromIp, it.toIp) }
            .distinct()
            .filterNot { it in queriedIps }
    val visibleIps = nodes.map { it.ip } + reportedIps

    // Recomputed from the whole graph. A node's position depends only on its hop distance from
    // the seed and its address, so it stays put unless another node joins its own ring.
    val layout =
        remember(visibleIps, links) {
            TopologyCanvasGeometry.layout(
                rootIp = nodes.firstOrNull()?.ip,
                ips = visibleIps,
                edges = links.map { it.fromIp to it.toIp },
            )
        }
    val positions = remember(layout, pxPerDp) { layout.positions.mapValues { (_, dp) -> dp * pxPerDp } }
    val nodesByIp = remember(nodes) { nodes.associateBy { it.ip } }

    LaunchedEffect(fitRequest) {
        graphOffset = Offset.Zero
        graphScale =
            TopologyCanvasGeometry.fitScale(
                extentPx = layout.extentDp * pxPerDp,
                viewportPx = min(canvasSize.width, canvasSize.height),
                marginPx = fitMarginPx,
            )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            graphOffset += pan
                            graphScale = (graphScale * zoom).coerceIn(TopologyCanvasGeometry.MIN_ZOOM, 3f)
                        }
                    }.pointerInput(nodes, positions, pxPerDp) {
                        detectTapGestures { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val candidates =
                                nodes.mapNotNull { node ->
                                    positions[node.ip]?.let { node.ip to (it * graphScale + center + graphOffset) }
                                }
                            val hit =
                                TopologyCanvasGeometry.nearestNodeWithin(tapOffset, candidates) { ip ->
                                    val node = nodesByIp[ip]
                                    if (node == null) {
                                        minHitRadiusPx
                                    } else {
                                        max(minHitRadiusPx, nodeRadiusDp(node) * pxPerDp * graphScale)
                                    }
                                }
                            hit?.let(onNodeTap)
                        }
                    },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            fun screenOf(position: Offset): Offset = position * graphScale + center + graphOffset

            for (link in links) {
                val fromPos = positions[link.fromIp] ?: continue
                val toPos = positions[link.toIp] ?: continue
                val screenFrom = screenOf(fromPos)
                val screenTo = screenOf(toPos)
                drawLine(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.7f), tertiaryColor.copy(alpha = 0.7f)),
                            start = screenFrom,
                            end = screenTo,
                        ),
                    start = screenFrom,
                    end = screenTo,
                    strokeWidth = 2.dp.toPx(),
                )
            }

            for (node in nodes) {
                val pos = positions[node.ip] ?: continue
                val screenPos = screenOf(pos)
                val radius = nodeRadiusDp(node) * pxPerDp * graphScale

                if (node.ip == selectedNodeIp) {
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.4f),
                        radius = radius + 10.dp.toPx(),
                        center = screenPos,
                        blendMode = BlendMode.Screen,
                    )
                }

                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(primaryColor, tertiaryColor),
                            center = screenPos,
                            radius = radius,
                        ),
                    radius = radius,
                    center = screenPos,
                )

                if (!node.snmpReachable) {
                    drawCircle(
                        color = mutedColor,
                        radius = radius + 5.dp.toPx(),
                        center = screenPos,
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                    )
                }
            }

            for (ip in reportedIps) {
                val pos = positions[ip] ?: continue
                val screenPos = screenOf(pos)
                val radius = TopologyCanvasGeometry.REPORTED_NODE_RADIUS_DP.dp.toPx() * graphScale
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.14f),
                    radius = radius,
                    center = screenPos,
                )
                drawCircle(
                    color = tertiaryColor,
                    radius = radius,
                    center = screenPos,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        if (visibleIps.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val labelCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
                fun labelPosition(position: Offset): Offset = position * graphScale + labelCenter + graphOffset

                for (node in nodes) {
                    val pos = positions[node.ip] ?: continue
                    val screen = labelPosition(pos)
                    val radius = nodeRadiusDp(node) * pxPerDp * graphScale

                    Text(
                        text = node.sysName ?: node.ip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .absoluteOffset(
                                    x = with(density) { (screen.x - 40.dp.toPx()).toDp() },
                                    y = with(density) { (screen.y + radius + 4.dp.toPx()).toDp() },
                                ).width(80.dp),
                    )
                }
                for (ip in reportedIps) {
                    val pos = positions[ip] ?: continue
                    val screen = labelPosition(pos)
                    val radius = TopologyCanvasGeometry.REPORTED_NODE_RADIUS_DP.dp.toPx() * graphScale

                    Text(
                        text = stringResource(R.string.topology_reported_neighbor_label, ip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .width(120.dp)
                                .absoluteOffset(
                                    x = with(density) { (screen.x - 60.dp.toPx()).toDp() },
                                    y = with(density) { (screen.y + radius + 4.dp.toPx()).toDp() },
                                ),
                    )
                }
            }
        }

        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.topology_no_nodes_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TopologyLegend(
                showReported = reportedIps.isNotEmpty(),
                showUnreachable = nodes.any { !it.snmpReachable },
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
            FloatingActionButton(
                onClick = { fitRequest++ },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    Icons.Default.FitScreen,
                    contentDescription = stringResource(R.string.topology_fit_button_cd),
                )
            }
        }
    }
}

@Composable
private fun TopologyLegend(
    showReported: Boolean,
    showUnreachable: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopologyLegendRow(TopologyNodeStyle.QUERIED, stringResource(R.string.topology_legend_queried))
            if (showReported) {
                TopologyLegendRow(TopologyNodeStyle.REPORTED, stringResource(R.string.topology_legend_reported))
            }
            if (showUnreachable) {
                TopologyLegendRow(TopologyNodeStyle.UNREACHABLE, stringResource(R.string.topology_legend_unreachable))
            }
        }
    }
}

private enum class TopologyNodeStyle { QUERIED, REPORTED, UNREACHABLE }

@Composable
private fun TopologyLegendRow(
    style: TopologyNodeStyle,
    label: String,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(14.dp)) {
            when (style) {
                TopologyNodeStyle.QUERIED -> {
                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = center)
                }

                TopologyNodeStyle.REPORTED -> {
                    drawCircle(
                        color = tertiaryColor,
                        radius = 5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                TopologyNodeStyle.UNREACHABLE -> {
                    drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = center)
                    drawCircle(
                        color = mutedColor,
                        radius = 6.5f.dp.toPx(),
                        center = center,
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun nodeRadiusDp(node: TopologyNode): Float =
    when {
        node.capabilities.contains(DeviceCapability.ROUTER) -> 28f
        node.capabilities.contains(DeviceCapability.SWITCH) -> 22f
        else -> 18f
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailSheet(
    node: TopologyNode,
    links: List<TopologyLink>,
    queriedNodeIps: Set<String>,
    onDismiss: () -> Unit,
    onNavigateToNeighbour: (String) -> Unit,
) {
    var interfaceFilter by rememberSaveable { mutableStateOf("all") }
    // Skip the partially-expanded intermediate state: this sheet's content is a
    // LazyColumn, so at partial expand its small viewport means sections below the
    // header (e.g. "System") are never composed at all, not just clipped -- a plain
    // scroll can't reveal them because the lazy list never laid them out to begin
    // with. Same fix as ToolHelpSheet.kt.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = deviceIcon(node.capabilities),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = node.sysName ?: node.ip,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = node.ip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.topology_node_detail_system)) {
                    node.vendor?.let { DetailRow(stringResource(R.string.topology_node_detail_vendor), it) }
                    node.model?.let { DetailRow(stringResource(R.string.topology_node_detail_model), it) }
                    node.firmwareVersion?.let { DetailRow(stringResource(R.string.topology_node_detail_firmware), it) }
                    node.sysLocation?.let { DetailRow(stringResource(R.string.topology_node_detail_location), it) }
                    node.uptimeHuman?.let { DetailRow(stringResource(R.string.topology_node_detail_uptime), it) }
                    if (!node.snmpReachable) {
                        Text(
                            text = stringResource(R.string.topology_node_snmp_unreachable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (node.interfaces.isNotEmpty()) {
                val shownInterfaces =
                    node.interfaces
                        .filter { iface ->
                            when (interfaceFilter) {
                                "up" -> iface.operStatus == InterfaceStatus.UP
                                "down" -> iface.operStatus == InterfaceStatus.DOWN
                                else -> true
                            }
                        }.withIndex()
                        .toList()
                item(key = "interfaces_header") {
                    SectionCard(title = stringResource(R.string.topology_node_detail_interfaces)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("all", "up", "down").forEach { filter ->
                                FilterChip(
                                    selected = interfaceFilter == filter,
                                    onClick = { interfaceFilter = filter },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (filter) {
                                                    "up" -> R.string.topology_interfaces_up
                                                    "down" -> R.string.topology_interfaces_down
                                                    else -> R.string.topology_interfaces_all
                                                },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                items(
                    items = shownInterfaces,
                    key = { (position, iface) -> "interface_${position}_${iface.index}" },
                ) { (_, iface) ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = AppShapes.medium,
                    ) {
                        InterfaceRow(iface, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                item(key = "interfaces_footer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (node.vlans.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.topology_node_detail_vlans)) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            node.vlans.forEach { vlan ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                        if (vlan.active) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                ) {
                                    Text(
                                        stringResource(R.string.topology_vlan_label, vlan.id, vlan.name),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (links.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.topology_node_detail_neighbours)) {
                        links.forEach { link ->
                            NeighbourRow(
                                link = link,
                                currentNodeIp = node.ip,
                                queriedNodeIps = queriedNodeIps,
                                onTap = { ip -> onNavigateToNeighbour(ip) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InterfaceRow(
    iface: SnmpInterface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = iface.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            val mac = iface.macAddress
            if (mac != null) {
                Text(
                    text = mac,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val speed = iface.speedBps
        if (speed != null) {
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        val statusColor =
            when (iface.operStatus) {
                InterfaceStatus.UP -> MaterialTheme.colorScheme.primary
                InterfaceStatus.DOWN -> MaterialTheme.colorScheme.error
                InterfaceStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        val statusLabel =
            when (iface.operStatus) {
                InterfaceStatus.UP -> stringResource(R.string.topology_interfaces_up)
                InterfaceStatus.DOWN -> stringResource(R.string.topology_interfaces_down)
                InterfaceStatus.UNKNOWN -> stringResource(R.string.topology_interface_unknown)
            }
        Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.12f)) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NeighbourRow(
    link: TopologyLink,
    currentNodeIp: String,
    queriedNodeIps: Set<String>,
    onTap: (String) -> Unit,
) {
    val neighbourIp = if (link.fromIp == currentNodeIp) link.toIp else link.fromIp
    val localPort = if (link.fromIp == currentNodeIp) link.fromPort else link.toPort
    val remotePort = if (link.fromIp == currentNodeIp) link.toPort else link.fromPort
    val isQueried = neighbourIp in queriedNodeIps

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("topology_neighbor_row_$neighbourIp")
                .then(if (isQueried) Modifier.clickable { onTap(neighbourIp) } else Modifier)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = link.protocol.name,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val portInfo =
                when {
                    localPort != null && remotePort != null ->
                        stringResource(R.string.topology_neighbour_ports, localPort, remotePort)
                    localPort != null -> stringResource(R.string.topology_neighbour_local_port, localPort)
                    remotePort != null -> stringResource(R.string.topology_neighbour_remote_port, remotePort)
                    else -> ""
                }
            if (portInfo.isNotBlank()) {
                Text(
                    text = portInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = link.neighbourSysName ?: neighbourIp,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (!isQueried) {
                Text(
                    text = stringResource(R.string.topology_reported_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isQueried) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats a link speed with one decimal place where it adds information, e.g. 2.5 Gbps or 1.5 Mbps.
 * Whole values drop the decimal. [locale] fixes the decimal separator.
 */
internal fun formatSpeed(
    bps: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val (scaled, unit) =
        when {
            bps >= 1_000_000_000L -> bps / 1_000_000_000.0 to "Gbps"
            bps >= 1_000_000L -> bps / 1_000_000.0 to "Mbps"
            bps >= 1_000L -> bps / 1_000.0 to "Kbps"
            else -> return "$bps bps"
        }
    val rounded = Math.round(scaled * 10) / 10.0
    val digits = if (rounded % 1.0 == 0.0) "%.0f" else "%.1f"
    return String.format(locale, "$digits $unit", rounded)
}

private fun deviceIcon(capabilities: Set<DeviceCapability>): androidx.compose.ui.graphics.vector.ImageVector =
    when {
        capabilities.contains(DeviceCapability.ROUTER) -> Icons.Default.Router
        capabilities.contains(DeviceCapability.SWITCH) -> Icons.Default.Hub
        capabilities.contains(DeviceCapability.AP) -> Icons.Default.Wifi
        capabilities.contains(DeviceCapability.PHONE) -> Icons.Default.Phone
        else -> Icons.Default.Computer
    }
