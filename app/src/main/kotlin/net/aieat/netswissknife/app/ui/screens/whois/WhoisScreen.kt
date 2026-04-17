package net.aieat.netswissknife.app.ui.screens.whois

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun WhoisScreen(viewModel: WhoisViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
            // ── Input card ─────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.whois_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.whois_screen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.whois_query_label)) },
                        placeholder = { Text(stringResource(R.string.whois_query_placeholder)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                    Button(
                        onClick = viewModel::lookup,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && uiState.query.isNotBlank()
                    ) {
                        Text(stringResource(R.string.whois_lookup_button))
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
                        } else {
                            // Show loading indicator when waiting for first hop
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        if (uiState.isLoading) {
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
                targetState = Triple(uiState.isLoading, uiState.result, uiState.error),
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 } togetherWith
                            fadeOut(tween(200))
                }
            ) { (isLoading, result, error) ->
                when {
                    result != null -> {
                        WhoisResultsSection(
                            result = result,
                            showRaw = uiState.showRawResponse,
                            onToggleRaw = viewModel::onToggleRawResponse,
                            onOpenUrl = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {}
                            }
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
}

// ── Relay Chain Visualiser ────────────────────────────────────────────────────

@Composable
fun RelayChainVisualiser(hopStates: List<HopUiState>) {
    if (hopStates.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        // Canvas for lines and travelling dots
        Canvas(modifier = Modifier.matchParentSize()) {
            if (hopStates.size < 2) return@Canvas
            val nodeRadius = 20.dp.toPx()
            val totalWidth = size.width
            val spacing = totalWidth / hopStates.size
            val nodeY = size.height / 2f

            for (i in 0 until hopStates.size - 1) {
                val startX = spacing * i + spacing / 2f
                val endX = spacing * (i + 1) + spacing / 2f
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(startX + nodeRadius, nodeY),
                    end = Offset(endX - nodeRadius, nodeY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Node composables in a Row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            hopStates.forEach { hop ->
                HopNode(
                    hop = hop,
                    infiniteTransition = infiniteTransition,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HopNode(
    hop: HopUiState,
    infiniteTransition: androidx.compose.animation.core.InfiniteTransition,
    modifier: Modifier = Modifier
) {
    val roleColor = when (hop.server.role) {
        WhoisServerRole.IANA -> MaterialTheme.colorScheme.tertiaryContainer
        WhoisServerRole.REGISTRY -> MaterialTheme.colorScheme.primaryContainer
        WhoisServerRole.REGISTRAR -> MaterialTheme.colorScheme.secondaryContainer
        WhoisServerRole.RIR -> MaterialTheme.colorScheme.secondaryContainer
    }
    val roleOnColor = when (hop.server.role) {
        WhoisServerRole.IANA -> MaterialTheme.colorScheme.onTertiaryContainer
        WhoisServerRole.REGISTRY -> MaterialTheme.colorScheme.onPrimaryContainer
        WhoisServerRole.REGISTRAR -> MaterialTheme.colorScheme.onSecondaryContainer
        WhoisServerRole.RIR -> MaterialTheme.colorScheme.onSecondaryContainer
    }

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
            HopStatus.DONE -> roleColor
            HopStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            HopStatus.SKIPPED -> MaterialTheme.colorScheme.surface
        },
        label = "node_bg"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(if (hop.status == HopStatus.QUERYING) pulseScale else 1f)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            when (hop.status) {
                HopStatus.DONE ->
                    Icon(Icons.Default.Check, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = roleOnColor)
                HopStatus.FAILED ->
                    Icon(Icons.Default.Close, contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                HopStatus.QUERYING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                else -> {}
            }
        }

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
                text = "${hop.queryTimeMs} ms",
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
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.whois_retry))
                }
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
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (result.queryType) {
            WhoisQueryType.DOMAIN -> DomainResultCard(result, onOpenUrl)
            WhoisQueryType.IPV4, WhoisQueryType.IPV6, WhoisQueryType.ASN -> IpAsnResultCard(result)
        }

        // Raw response accordion
        RawResponseAccordion(result = result, showRaw = showRaw, onToggle = onToggleRaw)
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
                                label = { Text(humanReadableStatus(status), style = MaterialTheme.typography.labelSmall) }
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
                LabeledRow(label = stringResource(R.string.whois_dnssec), value = dnssec)
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

            // Raw toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.whois_show_raw),
                    style = MaterialTheme.typography.bodyMedium
                )
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
                if (netRange != null) LabeledRow(stringResource(R.string.whois_net_range), netRange)
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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.whois_raw_responses),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showRaw, onCheckedChange = { onToggle() })
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

// ── Utility functions ─────────────────────────────────────────────────────────

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))

private fun relativeDate(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = epochMs - now
    val days = TimeUnit.MILLISECONDS.toDays(abs(diffMs))
    val years = days / 365
    return when {
        diffMs < 0 && years > 0 -> "$years years ago"
        diffMs < 0 -> "$days days ago"
        days == 0L -> "today"
        days < 30 -> "$days days left"
        years > 0 -> "$years years left"
        else -> "$days days left"
    }
}

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

private fun humanReadableStatus(raw: String): String {
    val stripped = raw.substringBefore(" ").trim()
    return when (stripped) {
        "clientTransferProhibited" -> "Transfer Locked"
        "clientDeleteProhibited" -> "Delete Locked"
        "clientUpdateProhibited" -> "Update Locked"
        "serverTransferProhibited" -> "Server Transfer Locked"
        "clientHold" -> "Client Hold"
        "pendingDelete" -> "Pending Delete"
        else -> {
            stripped
                .removePrefix("client")
                .removePrefix("server")
                .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                .replaceFirstChar { it.uppercase() }
        }
    }
}

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val base = 0x1F1E6 - 'A'.code
    return countryCode.uppercase()
        .map { char -> String(Character.toChars(base + char.code)) }
        .joinToString("")
}
