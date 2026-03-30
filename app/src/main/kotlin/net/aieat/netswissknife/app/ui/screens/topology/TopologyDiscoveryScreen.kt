package net.aieat.netswissknife.app.ui.screens.topology

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.topology.*
import kotlin.math.*

@Composable
fun TopologyDiscoveryScreen(
    viewModel: TopologyDiscoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }
    ) {
        TopologyScreenContent(
            uiState = uiState,
            onStartDiscovery = { params -> viewModel.startDiscovery(params) },
            onSelectNode = { ip -> viewModel.selectNode(ip) },
            onDeselectNode = { viewModel.deselectNode() },
            onReset = { viewModel.reset() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopologyScreenContent(
    uiState: TopologyUiState,
    onStartDiscovery: (TopologyParams) -> Unit,
    onSelectNode: (String) -> Unit,
    onDeselectNode: () -> Unit,
    onReset: () -> Unit
) {
    var targetIp by remember { mutableStateOf("") }
    var snmpVersion by remember { mutableStateOf(SnmpVersion.V2C) }
    var community by remember { mutableStateOf("public") }
    var v3Username by remember { mutableStateOf("") }
    var v3AuthProto by remember { mutableStateOf(V3AuthProtocol.NONE) }
    var v3AuthPassword by remember { mutableStateOf("") }
    var v3PrivProto by remember { mutableStateOf(V3PrivProtocol.NONE) }
    var v3PrivPassword by remember { mutableStateOf("") }
    var maxHops by remember { mutableFloatStateOf(3f) }
    var configExpanded by remember { mutableStateOf(true) }
    var showCommunityPassword by remember { mutableStateOf(false) }
    var showAuthPassword by remember { mutableStateOf(false) }
    var showPrivPassword by remember { mutableStateOf(false) }

    val isDiscovering = uiState is TopologyUiState.Discovering

    val selectedNode = when (uiState) {
        is TopologyUiState.Done -> uiState.graph.nodes.find { it.ip == uiState.selectedNodeIp }
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.topology_screen_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { configExpanded = !configExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SNMP Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = configExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = targetIp,
                                onValueChange = { targetIp = it },
                                label = { Text(stringResource(R.string.topology_target_ip_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                trailingIcon = {
                                    if (targetIp.isNotEmpty()) {
                                        IconButton(onClick = { targetIp = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.topology_snmp_version_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SnmpVersion.entries.forEachIndexed { index, version ->
                                    SegmentedButton(
                                        selected = snmpVersion == version,
                                        onClick = { snmpVersion = version },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SnmpVersion.entries.size),
                                        label = {
                                            Text(when (version) {
                                                SnmpVersion.V1 -> "v1"
                                                SnmpVersion.V2C -> "v2c"
                                                SnmpVersion.V3 -> "v3"
                                            })
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            AnimatedVisibility(visible = snmpVersion != SnmpVersion.V3) {
                                OutlinedTextField(
                                    value = community,
                                    onValueChange = { community = it },
                                    label = { Text(stringResource(R.string.topology_community_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (showCommunityPassword)
                                        VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showCommunityPassword = !showCommunityPassword }) {
                                            Icon(
                                                if (showCommunityPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                )
                            }

                            AnimatedVisibility(visible = snmpVersion == SnmpVersion.V3) {
                                Column {
                                    OutlinedTextField(
                                        value = v3Username,
                                        onValueChange = { v3Username = it },
                                        label = { Text(stringResource(R.string.topology_v3_username_label)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    V3ProtocolDropdown(
                                        label = stringResource(R.string.topology_v3_auth_proto_label),
                                        options = V3AuthProtocol.entries.map { it.name },
                                        selected = v3AuthProto.name,
                                        onSelect = { v3AuthProto = V3AuthProtocol.valueOf(it) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    AnimatedVisibility(visible = v3AuthProto != V3AuthProtocol.NONE) {
                                        OutlinedTextField(
                                            value = v3AuthPassword,
                                            onValueChange = { v3AuthPassword = it },
                                            label = { Text(stringResource(R.string.topology_v3_auth_password_label)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            visualTransformation = if (showAuthPassword)
                                                VisualTransformation.None
                                            else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showAuthPassword = !showAuthPassword }) {
                                                    Icon(
                                                        if (showAuthPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    V3ProtocolDropdown(
                                        label = stringResource(R.string.topology_v3_priv_proto_label),
                                        options = V3PrivProtocol.entries.map { it.name },
                                        selected = v3PrivProto.name,
                                        onSelect = { v3PrivProto = V3PrivProtocol.valueOf(it) }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    AnimatedVisibility(visible = v3PrivProto != V3PrivProtocol.NONE) {
                                        OutlinedTextField(
                                            value = v3PrivPassword,
                                            onValueChange = { v3PrivPassword = it },
                                            label = { Text(stringResource(R.string.topology_v3_priv_password_label)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            visualTransformation = if (showPrivPassword)
                                                VisualTransformation.None
                                            else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPrivPassword = !showPrivPassword }) {
                                                    Icon(
                                                        if (showPrivPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.topology_max_hops_label, maxHops.toInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = maxHops,
                                onValueChange = { maxHops = it },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    onStartDiscovery(
                                        TopologyParams(
                                            targetIp = targetIp,
                                            snmpVersion = snmpVersion,
                                            communityString = community,
                                            v3Username = v3Username.ifBlank { null },
                                            v3AuthPassword = v3AuthPassword.ifBlank { null },
                                            v3PrivPassword = v3PrivPassword.ifBlank { null },
                                            v3AuthProtocol = v3AuthProto,
                                            v3PrivProtocol = v3PrivProto,
                                            maxHops = maxHops.toInt(),
                                            timeoutMs = 3000,
                                            retries = 1
                                        )
                                    )
                                    configExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDiscovering && targetIp.isNotBlank()
                            ) {
                                if (isDiscovering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.topology_discover_button))
                            }

                            AnimatedVisibility(visible = isDiscovering) {
                                OutlinedButton(
                                    onClick = onReset,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.topology_cancel_button))
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "topology_state"
                ) { state ->
                    when (state) {
                        is TopologyUiState.Idle -> {
                            IdleContent()
                        }
                        is TopologyUiState.Discovering -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TopologyCanvas(
                                    nodes = state.nodes,
                                    links = state.links,
                                    selectedNodeIp = null,
                                    onNodeTap = onSelectNode
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    ScanningBadge(
                                        message = stringResource(
                                            R.string.topology_scanning_badge,
                                            state.nodesDone
                                        )
                                    )
                                }
                            }
                        }
                        is TopologyUiState.Done -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TopologyCanvas(
                                    nodes = state.graph.nodes,
                                    links = state.graph.links,
                                    selectedNodeIp = state.selectedNodeIp,
                                    onNodeTap = onSelectNode
                                )
                                FloatingActionButton(
                                    onClick = { },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        Icons.Default.FitScreen,
                                        contentDescription = stringResource(R.string.topology_fit_button_cd)
                                    )
                                }
                            }
                        }
                        is TopologyUiState.Failure -> {
                            ErrorContent(
                                message = state.message,
                                onRetry = onReset
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedNode != null) {
        NodeDetailSheet(
            node = selectedNode,
            links = when (uiState) {
                is TopologyUiState.Done -> uiState.graph.links.filter {
                    it.fromIp == selectedNode.ip || it.toIp == selectedNode.ip
                }
                else -> emptyList()
            },
            onDismiss = onDeselectNode,
            onNavigateToNeighbour = onSelectNode
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V3ProtocolDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.size(120.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2
                ),
                radius = size.minDimension / 2
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = size.minDimension * 0.35f,
                center = center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Icon(
            imageVector = Icons.Default.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.topology_idle_tagline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter a seed IP and configure SNMP settings above",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Discovery Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.topology_error_retry))
                }
            }
        }
    }
}

@Composable
private fun ScanningBadge(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "badge_alpha"
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.alpha(alpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TopologyCanvas(
    nodes: List<TopologyNode>,
    links: List<TopologyLink>,
    selectedNodeIp: String?,
    onNodeTap: (String) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    var graphOffset by remember { mutableStateOf(Offset.Zero) }
    var graphScale by remember { mutableFloatStateOf(1f) }

    val nodePositions = remember(nodes) {
        computeRadialLayout(nodes)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nodes")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        graphOffset += pan
                        graphScale = (graphScale * zoom).coerceIn(0.3f, 3f)
                    }
                }
                .pointerInput(nodes) {
                    detectTapGestures { tapOffset ->
                        val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                        for (node in nodes) {
                            val pos = nodePositions[node.ip] ?: continue
                            val screenPos = Offset(
                                (pos.x * graphScale) + canvasCenter.x + graphOffset.x,
                                (pos.y * graphScale) + canvasCenter.y + graphOffset.y
                            )
                            val radius = nodeRadius(node) * graphScale
                            if ((tapOffset - screenPos).getDistance() <= radius) {
                                onNodeTap(node.ip)
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val canvasCenter = Offset(size.width / 2, size.height / 2)

            for (link in links) {
                val fromPos = nodePositions[link.fromIp] ?: continue
                val toPos = nodePositions[link.toIp] ?: continue
                val screenFrom = Offset(
                    (fromPos.x * graphScale) + canvasCenter.x + graphOffset.x,
                    (fromPos.y * graphScale) + canvasCenter.y + graphOffset.y
                )
                val screenTo = Offset(
                    (toPos.x * graphScale) + canvasCenter.x + graphOffset.x,
                    (toPos.y * graphScale) + canvasCenter.y + graphOffset.y
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.7f), tertiaryColor.copy(alpha = 0.7f)),
                        start = screenFrom,
                        end = screenTo
                    ),
                    start = screenFrom,
                    end = screenTo,
                    strokeWidth = 2.dp.toPx()
                )
            }

            for (node in nodes) {
                val pos = nodePositions[node.ip] ?: continue
                val screenPos = Offset(
                    (pos.x * graphScale) + canvasCenter.x + graphOffset.x,
                    (pos.y * graphScale) + canvasCenter.y + graphOffset.y
                )
                val radius = nodeRadius(node) * graphScale
                val isSelected = node.ip == selectedNodeIp

                if (isSelected) {
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.4f),
                        radius = radius + 10.dp.toPx(),
                        center = screenPos,
                        blendMode = BlendMode.Screen
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor, tertiaryColor),
                        center = screenPos,
                        radius = radius
                    ),
                    radius = radius,
                    center = screenPos
                )

                if (!node.snmpReachable) {
                    drawCircle(
                        color = primaryColor.copy(alpha = pulseAlpha * 0.3f),
                        radius = radius + 5.dp.toPx(),
                        center = screenPos
                    )
                }
            }
        }

        if (nodes.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
                for (node in nodes) {
                    val pos = nodePositions[node.ip] ?: continue
                    val screenX = (pos.x * graphScale) + canvasCenter.x + graphOffset.x
                    val screenY = (pos.y * graphScale) + canvasCenter.y + graphOffset.y
                    val radius = nodeRadius(node) * graphScale

                    Text(
                        text = node.sysName ?: node.ip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .absoluteOffset(
                                x = (screenX - 40.dp.value).dp,
                                y = (screenY + radius + 4.dp.value).dp
                            )
                            .width(80.dp)
                    )
                }
            }
        }

        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No nodes discovered yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun computeRadialLayout(nodes: List<TopologyNode>): Map<String, Offset> {
    if (nodes.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, Offset>()
    if (nodes.size == 1) {
        result[nodes[0].ip] = Offset.Zero
        return result
    }
    result[nodes[0].ip] = Offset.Zero
    val remaining = nodes.drop(1)
    val ringRadius = 180f
    remaining.forEachIndexed { index, node ->
        val angle = (2 * PI * index / remaining.size).toFloat()
        result[node.ip] = Offset(
            x = ringRadius * cos(angle),
            y = ringRadius * sin(angle)
        )
    }
    return result
}

private fun nodeRadius(node: TopologyNode): Float {
    return when {
        node.capabilities.contains(DeviceCapability.ROUTER) -> 28f
        node.capabilities.contains(DeviceCapability.SWITCH) -> 22f
        else -> 18f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailSheet(
    node: TopologyNode,
    links: List<TopologyLink>,
    onDismiss: () -> Unit,
    onNavigateToNeighbour: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = deviceIcon(node.capabilities),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = node.sysName ?: node.ip,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = node.ip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val vendor = node.vendor
                        if (vendor != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(vendor, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.topology_node_detail_system)) {
                    DetailRow("Vendor / Model", "${node.vendor ?: "Unknown"} ${node.model ?: ""}".trim())
                    val fw = node.firmwareVersion
                    val loc = node.sysLocation
                    val uptime = node.uptimeHuman
                    if (fw != null) DetailRow("Firmware", fw)
                    if (loc != null) DetailRow("Location", loc)
                    if (uptime != null) DetailRow("Uptime", uptime)
                    if (!node.snmpReachable) {
                        Text(
                            text = "SNMP not reachable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (node.interfaces.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.topology_node_detail_interfaces)) {
                        node.interfaces.forEach { iface ->
                            InterfaceRow(iface)
                        }
                    }
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            node.vlans.forEach { vlan ->
                                if (vlan.active) {
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text("${vlan.id}: ${vlan.name}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                } else {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${vlan.id}: ${vlan.name}", style = MaterialTheme.typography.labelSmall) }
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
                                onTap = { ip -> onNavigateToNeighbour(ip) }
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
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InterfaceRow(iface: SnmpInterface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = iface.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            val mac = iface.macAddress
            if (mac != null) {
                Text(
                    text = mac,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val speed = iface.speedBps
        if (speed != null) {
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        val statusColor = when (iface.operStatus) {
            InterfaceStatus.UP -> MaterialTheme.colorScheme.primary
            InterfaceStatus.DOWN -> MaterialTheme.colorScheme.error
            InterfaceStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusLabel = when (iface.operStatus) {
            InterfaceStatus.UP -> "UP"
            InterfaceStatus.DOWN -> "DOWN"
            InterfaceStatus.UNKNOWN -> "?"
        }
        AssistChip(
            onClick = {},
            label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = statusColor
            )
        )
    }
}

@Composable
private fun NeighbourRow(link: TopologyLink, currentNodeIp: String, onTap: (String) -> Unit) {
    val neighbourIp = if (link.fromIp == currentNodeIp) link.toIp else link.fromIp
    val localPort = if (link.fromIp == currentNodeIp) link.fromPort else link.toPort
    val remotePort = if (link.fromIp == currentNodeIp) link.toPort else link.fromPort

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(neighbourIp) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = {},
            label = { Text(link.protocol.name, style = MaterialTheme.typography.labelSmall) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val portInfo = when {
                localPort != null && remotePort != null -> "$localPort -> $remotePort"
                localPort != null -> "local: $localPort"
                remotePort != null -> "remote: $remotePort"
                else -> ""
            }
            if (portInfo.isNotBlank()) {
                Text(
                    text = portInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = link.neighbourSysName ?: neighbourIp,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSpeed(bps: Long): String {
    return when {
        bps >= 1_000_000_000L -> "${bps / 1_000_000_000L} Gbps"
        bps >= 1_000_000L -> "${bps / 1_000_000L} Mbps"
        bps >= 1_000L -> "${bps / 1_000L} Kbps"
        else -> "$bps bps"
    }
}

private fun deviceIcon(capabilities: Set<DeviceCapability>): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        capabilities.contains(DeviceCapability.ROUTER) -> Icons.Default.Router
        capabilities.contains(DeviceCapability.SWITCH) -> Icons.Default.Hub
        capabilities.contains(DeviceCapability.AP) -> Icons.Default.Wifi
        capabilities.contains(DeviceCapability.PHONE) -> Icons.Default.Phone
        else -> Icons.Default.Computer
    }
}
