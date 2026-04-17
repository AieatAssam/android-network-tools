package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.subnet.SubnetInfo

@Composable
fun SubnetCalculatorScreen(viewModel: SubnetCalculatorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Input card ──────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.subnet_screen_title),
                                style = MaterialTheme.typography.displaySmall
                            )
                            Text(
                                text = stringResource(R.string.subnet_screen_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode toggle
                    SubnetModeToggle(
                        isRangeMode = uiState.isRangeMode,
                        onToggle = viewModel::toggleMode
                    )

                    // Animated switch between input modes
                    AnimatedContent(
                        targetState = uiState.isRangeMode,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
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
                    fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 } togetherWith
                            fadeOut(tween(200))
                },
                label = "subnet-state"
            ) { (result, error, rangeMode) ->
                when {
                    result != null -> SubnetResultContent(result)
                    error != null  -> SubnetErrorCard(
                        message = error,
                        onRetry = if (rangeMode) viewModel::calculateRange else viewModel::calculate
                    )
                    else           -> SubnetIdleCard(isRangeMode = rangeMode)
                }
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_input_label)) },
            placeholder = { Text(stringResource(R.string.subnet_input_placeholder)) },
            leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { onInputChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCalculate() })
        )

        Button(
            onClick = onCalculate,
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = minIp,
            onValueChange = onMinIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_min_ip_label)) },
            placeholder = { Text(stringResource(R.string.subnet_min_ip_placeholder)) },
            leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
            trailingIcon = {
                if (minIp.isNotEmpty()) {
                    IconButton(onClick = { onMinIpChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = maxIp,
            onValueChange = onMaxIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.subnet_max_ip_label)) },
            placeholder = { Text(stringResource(R.string.subnet_max_ip_placeholder)) },
            leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
            trailingIcon = {
                if (maxIp.isNotEmpty()) {
                    IconButton(onClick = { onMaxIpChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCalculate() })
        )

        Button(
            onClick = onCalculate,
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
            Button(onClick = onRetry) {
                Text(stringResource(R.string.subnet_retry))
            }
        }
    }
}

// ── Results content ───────────────────────────────────────────────────────────

@Composable
private fun SubnetResultContent(info: SubnetInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!info.inputIsAligned) {
            SubnetAlignmentWarning(info)
        }
        SubnetOverviewCard(info)
        SubnetBinaryVisualizerCard(info)
        SubnetNotationsCard(info)
        SubnetPropertiesCard(info)
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
                        text = stringResource(
                            R.string.subnet_overview_hosts,
                            formatHostCount(info.usableHosts)
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
                    value = formatHostCount(info.totalHosts)
                )
                SubnetInfoRow(
                    label = stringResource(R.string.subnet_usable_hosts),
                    value = formatHostCount(info.usableHosts),
                    tooltip = stringResource(R.string.tooltip_usable_hosts)
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
                    label = stringResource(R.string.subnet_network_bits, info.prefixLength)
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.subnet_host_bits, info.hostBits)
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
    val networkColor = MaterialTheme.colorScheme.primary
    val hostColor = MaterialTheme.colorScheme.tertiary
    val networkBg = MaterialTheme.colorScheme.primaryContainer
    val hostBg = MaterialTheme.colorScheme.tertiaryContainer

    // Strip dots to get a flat 32-bit string, then re-add octet grouping with colors
    val bits = binaryDotted.replace(".", "")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                label = stringResource(R.string.subnet_notation_cidr),
                value = info.cidrNotation,
                monospace = true,
                tooltip = stringResource(R.string.tooltip_cidr)
            )
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
            SubnetInfoRow(
                label = stringResource(R.string.subnet_notation_binary),
                value = info.binaryMask,
                monospace = true
            )
        }
    }
}

// ── Properties card ───────────────────────────────────────────────────────────

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

            // Private / Public badge
            SubnetInfoRow(
                label = stringResource(R.string.subnet_scope),
                tooltip = stringResource(R.string.tooltip_scope)
            ) {
                val (scopeIcon, scopeLabel, scopeColor, scopeContainerColor) = if (info.isPrivate) {
                    Quad(
                        Icons.Default.Lock,
                        stringResource(R.string.subnet_scope_private),
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                } else {
                    Quad(
                        Icons.Default.Public,
                        stringResource(R.string.subnet_scope_public),
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
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
                            text = scopeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = scopeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            SubnetInfoRow(
                label = stringResource(R.string.subnet_prefix_bits),
                value = stringResource(R.string.subnet_prefix_of_32, info.prefixLength)
            )
            SubnetInfoRow(
                label = stringResource(R.string.subnet_host_bit_count),
                value = stringResource(R.string.subnet_bits_count, info.hostBits)
            )
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
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
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
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
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

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatHostCount(count: Long): String = when {
    count >= 1_000_000L -> "%.2fM".format(count / 1_000_000.0)
    count >= 1_000L     -> "%.1fK".format(count / 1_000.0)
    else                -> count.toString()
}
