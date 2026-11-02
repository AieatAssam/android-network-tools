package net.aieat.netswissknife.app.ui.screens.subnet

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.core.network.subnet.Ipv4Scope
import net.aieat.netswissknife.core.network.subnet.SubnetInfo

@Composable
fun SubnetCalculatorScreen(viewModel: SubnetCalculatorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val announcementPhase = when {
        uiState.error != null -> ToolAnnouncementPhase.ERROR
        uiState.result != null -> ToolAnnouncementPhase.FINISHED
        else -> null
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

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
            // ── Hero header ─────────────────────────────────────────────────────
            Box {
                ToolStateAnnouncer(stringResource(R.string.help_subnet_title), announcementPhase)
                ToolHeroHeader(
                    title = stringResource(R.string.subnet_screen_title),
                    subtitle = stringResource(R.string.subnet_screen_subtitle),
                    icon = Icons.Default.Calculate,
                    onHelpClick = { showHelp = true },
                )
            }

            // ── Input card ──────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Mode toggle
                    SubnetModeToggle(
                        isRangeMode = uiState.isRangeMode,
                        onToggle = viewModel::toggleMode
                    )

                    // Animated switch between input modes
                    AnimatedContent(
                        targetState = uiState.isRangeMode,
                        transitionSpec = { fadeIn(AppMotion.enter(200)) togetherWith fadeOut(AppMotion.exit(150)) },
                        label = "input-mode"
                    ) { rangeMode ->
                        if (rangeMode) {
                            SubnetRangeInputs(
                                minIp = uiState.minIpInput,
                                maxIp = uiState.maxIpInput,
                                onMinIpChange = viewModel::onMinIpChange,
                                onMaxIpChange = viewModel::onMaxIpChange,
                                onCalculate = viewModel::calculateRange,
                                canCalculate = uiState.minIpInput.isNotBlank() && uiState.maxIpInput.isNotBlank()
                            )
                        } else {
                            SubnetCidrInputs(
                                input = uiState.input,
                                onInputChange = viewModel::onInputChange,
                                onCalculate = viewModel::calculate,
                                onExampleSelected = viewModel::setExample,
                                canCalculate = uiState.input.isNotBlank()
                            )
                        }
                    }
                }
            }

            // ── Result / error area ─────────────────────────────────────────────
            AnimatedContent(
                targetState = Triple(uiState.result, uiState.error, uiState.isRangeMode),
                transitionSpec = {
                    fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 8 } togetherWith
                            fadeOut(AppMotion.exit(200))
                },
                label = "subnet-state"
            ) { (result, error, rangeMode) ->
                when {
                    result != null -> SubnetResultContent(result, uiState, viewModel)
                    error != null  -> SubnetErrorCard(
                        message = ErrorTextMapper.map(error.info, error.message).asString(),
                        onRetry = if (rangeMode) viewModel::calculateRange else viewModel::calculate
                    )
                    else           -> SubnetIdleCard(isRangeMode = rangeMode)
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_subnet_title),
            conceptHeading = stringResource(R.string.help_subnet_concept_heading),
            conceptBody = stringResource(R.string.help_subnet_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_subnet_what_heading), stringResource(R.string.help_subnet_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_subnet_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_subnet_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_subnet_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_subnet_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Mode toggle ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubnetModeToggle(isRangeMode: Boolean, onToggle: () -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isRangeMode,
            onClick = { if (isRangeMode) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.subnet_mode_cidr)) }
        )
        SegmentedButton(
            selected = isRangeMode,
            onClick = { if (!isRangeMode) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.subnet_mode_range)) }
        )
    }
}

// ── CIDR input panel ──────────────────────────────────────────────────────────

@Composable
private fun SubnetCidrInputs(
    input: String,
    onInputChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onExampleSelected: (String) -> Unit,
    canCalculate: Boolean
) {
    val dismissKeyboard = rememberDismissKeyboard()
    val submit: () -> Unit = {
        dismissKeyboard()
        onCalculate()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_input_label)) },
            placeholder = { Text(stringResource(R.string.subnet_input_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )

        Button(
            onClick = hapticAction(submit),
            modifier = Modifier.fillMaxWidth(),
            enabled = canCalculate
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.subnet_calculate_button))
        }

        SubnetExampleChips(onExampleSelected = onExampleSelected)
    }
}

// ── Range input panel ─────────────────────────────────────────────────────────

@Composable
private fun SubnetRangeInputs(
    minIp: String,
    maxIp: String,
    onMinIpChange: (String) -> Unit,
    onMaxIpChange: (String) -> Unit,
    onCalculate: () -> Unit,
    canCalculate: Boolean
) {
    val dismissKeyboard = rememberDismissKeyboard()
    val submit: () -> Unit = {
        dismissKeyboard()
        onCalculate()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTextField(
            value = minIp,
            onValueChange = onMinIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_min_ip_label)) },
            placeholder = { Text(stringResource(R.string.subnet_min_ip_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        ToolTextField(
            value = maxIp,
            onValueChange = onMaxIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_max_ip_label)) },
            placeholder = { Text(stringResource(R.string.subnet_max_ip_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )

        Button(
            onClick = hapticAction(submit),
            modifier = Modifier.fillMaxWidth(),
            enabled = canCalculate
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.subnet_range_calculate_button))
        }
    }
}

// ── Example chips ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetExampleChips(onExampleSelected: (String) -> Unit) {
    val examples = listOf("192.168.1.0/24", "10.0.0.0/8", "172.16.0.0/12", "192.168.1.1/30", "0.0.0.0/0")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        examples.forEach { example ->
            FilterChip(
                selected = false,
                onClick = { onExampleSelected(example) },
                label = { Text(example, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ── Idle card ─────────────────────────────────────────────────────────────────

@Composable
private fun SubnetIdleCard(isRangeMode: Boolean) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Calculate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(
                    if (isRangeMode) R.string.subnet_range_idle_title else R.string.subnet_idle_title
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (isRangeMode) R.string.subnet_range_idle_body else R.string.subnet_idle_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Error card ────────────────────────────────────────────────────────────────

@Composable
private fun SubnetErrorCard(message: String, onRetry: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                text = stringResource(R.string.subnet_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            ToolRetryButton(text = stringResource(R.string.subnet_retry), onClick = onRetry)
        }
    }
}

// ── Results content ───────────────────────────────────────────────────────────

@Composable
private fun SubnetResultContent(
    info: SubnetInfo,
    state: SubnetCalculatorUiState,
    viewModel: SubnetCalculatorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!info.inputIsAligned) {
            SubnetAlignmentWarning(info)
        }
        SubnetOverviewCard(info)
        SubnetBinaryVisualizerCard(info)
        SubnetNotationsCard(info)
        SubnetPropertiesCard(info)
        SubnetMembershipCard(
            input = state.checkIpInput,
            membership = state.membership,
            error = state.membershipError,
            onInputChange = viewModel::onCheckIpChange,
        )
        SubnetSplitCard(
            mode = state.splitMode,
            input = state.splitInput,
            split = state.split,
            error = state.splitError,
            onModeChange = viewModel::onSplitModeChange,
            onInputChange = viewModel::onSplitInputChange,
            onSplit = viewModel::splitNetwork,
        )
        SubnetShareActions(info)
    }
}

// ── Alignment warning card ────────────────────────────────────────────────────

@Composable
private fun SubnetAlignmentWarning(info: SubnetInfo) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.subnet_alignment_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.subnet_alignment_warning_body,
                        info.inputIp,
                        info.prefixLength,
                        info.networkAddress
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ── Overview card ─────────────────────────────────────────────────────────────

@Composable
private fun SubnetOverviewCard(info: SubnetInfo) {
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
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = info.cidrNotation,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.subnet_overview_hosts,
                            subnetPluralQuantity(info.usableHosts),
                            formatSubnetCount(info.usableHosts)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            HorizontalDivider()

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_network_address),
                    value = info.networkAddress,
                    tooltip = stringResource(R.string.tooltip_network_address)
                )
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_broadcast),
                    value = info.broadcastAddress,
                    tooltip = stringResource(R.string.tooltip_broadcast)
                )
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_first_host),
                    value = info.firstHost,
                    tooltip = stringResource(R.string.tooltip_first_host)
                )
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_last_host),
                    value = info.lastHost,
                    tooltip = stringResource(R.string.tooltip_last_host)
                )
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_total_hosts),
                    value = formatSubnetCount(info.totalHosts)
                )
            }
        }
    }
}

// ── Binary visualizer card ────────────────────────────────────────────────────

@Composable
private fun SubnetBinaryVisualizerCard(info: SubnetInfo) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.subnet_binary_breakdown),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = pluralStringResource(R.plurals.subnet_network_bits, info.prefixLength, info.prefixLength)
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = pluralStringResource(R.plurals.subnet_host_bits, info.hostBits, info.hostBits)
                )
            }

            // IP address binary row
            BinaryBitsRow(
                label = stringResource(R.string.subnet_binary_ip),
                binaryDotted = info.binaryIpAddress,
                prefixLength = info.prefixLength
            )

            // Subnet mask binary row
            BinaryBitsRow(
                label = stringResource(R.string.subnet_binary_mask),
                binaryDotted = info.binaryMask,
                prefixLength = info.prefixLength
            )

            // Network address binary row
            BinaryBitsRow(
                label = stringResource(R.string.subnet_binary_network),
                binaryDotted = info.binaryNetworkAddress,
                prefixLength = info.prefixLength
            )
        }
    }
}

/** One spoken description per binary row, so TalkBack does not read individual bits. */
private fun binaryRowDescription(
    resources: Resources,
    label: String,
    prefixLength: Int,
    hostBits: Int,
): String {
    val networkText = resources.getQuantityString(R.plurals.subnet_describe_network_bits, prefixLength, prefixLength)
    val hostText = resources.getQuantityString(R.plurals.subnet_describe_host_bits, hostBits, hostBits)
    return resources.getString(R.string.subnet_binary_row_description, label, networkText, hostText)
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BinaryBitsRow(label: String, binaryDotted: String, prefixLength: Int) {
    // Strip dots to get a flat 32-bit string, then re-add octet grouping with colors
    val bits = binaryDotted.replace(".", "")
    val resources = LocalContext.current.resources
    val description = binaryRowDescription(resources, label, prefixLength, bits.length - prefixLength)

    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BinaryBitCells(bits = bits, prefixLength = prefixLength)
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose naming convention.
@Composable
private fun BinaryBitCells(bits: String, prefixLength: Int) {
    val networkColor = MaterialTheme.colorScheme.primary
    val hostColor = MaterialTheme.colorScheme.tertiary
    val networkBg = MaterialTheme.colorScheme.primaryContainer
    val hostBg = MaterialTheme.colorScheme.tertiaryContainer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        bits.chunked(8).forEachIndexed { octetIdx, octet ->
            val octetStart = octetIdx * 8
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                octet.forEachIndexed { bitIdx, bit ->
                    val absoluteBit = octetStart + bitIdx
                    val isNetwork = absoluteBit < prefixLength
                    val bg = if (isNetwork) networkBg else hostBg
                    val fg = if (isNetwork) networkColor else hostColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(bg)
                            .border(
                                width = if (absoluteBit == prefixLength - 1 || absoluteBit == prefixLength) 1.dp else 0.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bit.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isNetwork) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = fg
                        )
                    }
                }
            }
            if (octetIdx < 3) {
                Text(
                    text = ".",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

// ── Notations card ────────────────────────────────────────────────────────────

@Composable
private fun SubnetNotationsCard(info: SubnetInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.subnet_notations_title),
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider()
            SubnetInfoRow(
                label = stringResource(R.string.subnet_notation_mask),
                value = info.subnetMask,
                monospace = true
            )
            SubnetInfoRow(
                label = stringResource(R.string.subnet_notation_wildcard),
                value = info.wildcardMask,
                monospace = true,
                tooltip = stringResource(R.string.tooltip_wildcard)
            )
            SubnetInfoRow(
                label = stringResource(R.string.subnet_notation_hex),
                value = info.hexMask,
                monospace = true
            )
        }
    }
}

// ── Properties card ───────────────────────────────────────────────────────────

private fun scopeIconFor(scope: Ipv4Scope): ImageVector =
    when (scope) {
        Ipv4Scope.PRIVATE -> Icons.Default.Lock
        Ipv4Scope.PUBLIC -> Icons.Default.Public
        else -> Icons.Default.Info
    }

@Composable
private fun SubnetPropertiesCard(info: SubnetInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.subnet_properties_title),
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider()

            // IP Class badge
            SubnetInfoRow(
                label = stringResource(R.string.subnet_ip_class),
                tooltip = stringResource(R.string.tooltip_ip_class)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.subnet_class_label, info.ipClass),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Scope of the whole subnet (private, shared, public, mixed, ...)
            SubnetInfoRow(
                label = stringResource(R.string.subnet_scope),
                tooltip = stringResource(R.string.tooltip_scope)
            ) {
                val isPublic = info.scope == Ipv4Scope.PUBLIC
                val scopeIcon = scopeIconFor(info.scope)
                val scopeColor =
                    if (isPublic) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                val scopeContainerColor =
                    if (isPublic) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                Surface(shape = RoundedCornerShape(8.dp), color = scopeContainerColor) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            scopeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scopeColor
                        )
                        Text(
                            text = stringResource(info.scope.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            color = scopeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

/**
 * Row with label on the left, arbitrary content on the right, and an optional tooltip
 * info icon next to the label. Long-press the (i) icon to see the tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubnetInfoRow(
    label: String,
    tooltip: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tooltip != null) {
                val tooltipState = rememberTooltipState(isPersistent = true)
                val tooltipScope = rememberCoroutineScope()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                text = tooltip,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    state = tooltipState
                ) {
                    IconButton(
                        onClick = { tooltipScope.launch { tooltipState.show() } },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.subnet_explain_field, label),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

/** Convenience overload for rows with a plain text value. */
@Composable
private fun SubnetInfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    tooltip: String? = null
) {
    SubnetInfoRow(label = label, tooltip = tooltip) {
        Text(
            text = value,
            style = if (monospace)
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            else
                MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
    }
}
