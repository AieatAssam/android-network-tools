package net.aieat.netswissknife.app.ui.screens.tls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ChipColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.app.ui.theme.StatusWarn
import net.aieat.netswissknife.app.ui.theme.StatusWarnDeep
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.app.ui.components.scrollIntoViewOnFocus
import net.aieat.netswissknife.app.ui.format.withSeparatorBreaks
import net.aieat.netswissknife.app.ui.theme.StatusGoodDeep
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.theme.StatusBlue
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.TlsInspectorErrorKeys
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// ── Screen ────────────────────────────────────────────────────────────────────

object TlsInspectorScreenTestTags {
    const val SOURCE_CONTEXT = "tls_source_context"
    const val INVALID_HANDOFF = "tls_invalid_handoff"
    const val CLEAR_PREFILL_ACTION = "tls_clear_prefill_action"
    const val PROTOCOL_PROBE_TOGGLE = "tls_protocol_probe_toggle"
    const val EXPECTED_PIN_FIELD = "tls_expected_pin_field"
    const val VERDICT_DETAIL = "tls_verdict_detail"
    const val EXPIRY_BADGE = "tls_expiry_badge"
}

@Composable
fun TlsInspectorScreen(viewModel: TlsInspectorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val hasInvalidHandoff by viewModel.hasInvalidHandoff.collectAsStateWithLifecycle()
    val sourceContext by viewModel.sourceContextState.collectAsStateWithLifecycle()
    val announcementPhase = when {
        uiState.isLoading -> ToolAnnouncementPhase.RUNNING
        uiState.isCanceled -> ToolAnnouncementPhase.CANCELED
        uiState.error != null -> ToolAnnouncementPhase.ERROR
        uiState.result != null -> ToolAnnouncementPhase.FINISHED
        else -> null
    }
    ToolStateAnnouncer(stringResource(R.string.help_tls_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label = "tls_entrance_alpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .testTag("tls_content_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 16.dp
            )
        ) {
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TlsHeaderCard(onHelpClick = { showHelp = true })
                    NetworkStatusBanner(networkStatus, scope = NetworkStatusScope.INTERNET)
                    if (sourceContext == ToolSource.LAN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.tls_source_lan),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.testTag(TlsInspectorScreenTestTags.SOURCE_CONTEXT),
                            )
                            TextButton(
                                onClick = viewModel::clearPrefill,
                                enabled = !uiState.isLoading,
                                modifier = Modifier.testTag(TlsInspectorScreenTestTags.CLEAR_PREFILL_ACTION),
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                    if (hasInvalidHandoff) {
                        Text(
                            text = stringResource(R.string.tls_invalid_handoff),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(TlsInspectorScreenTestTags.INVALID_HANDOFF),
                        )
                    }
                }
            }

            // Input section
            item {
                TlsInputSection(
                    host      = uiState.host,
                    port      = uiState.port,
                    probeProtocols = uiState.probeProtocols,
                    expectedPinSha256 = uiState.expectedPinSha256,
                    isLoading = uiState.isLoading,
                    isCanceling = uiState.isCanceling,
                    recentHosts = recentHosts,
                    onHostChange = viewModel::onHostChange,
                    onPortChange = viewModel::onPortChange,
                    onProbeProtocolsChange = viewModel::onProbeProtocolsChange,
                    onExpectedPinChange = viewModel::onExpectedPinChange,
                    onInspect    = viewModel::inspect,
                    onCancel     = viewModel::stopInspection,
                    onRemoveRecentHost = viewModel::removeRecentHost,
                    onClearRecentHosts = viewModel::clearRecentHosts
                )
            }

            // Content area — idle / loading / error / success
            item {
                val displayState = when {
                    uiState.isLoading          -> DisplayState.Loading
                    uiState.isCanceled         -> DisplayState.Canceled
                    uiState.error != null      -> DisplayState.Error(
                        uiState.error!!,
                        uiState.errorDescriptionKey,
                    )
                    uiState.result != null     -> DisplayState.Success(uiState.result!!)
                    else                       -> DisplayState.Idle
                }

                AnimatedContent(
                    targetState   = displayState,
                    transitionSpec = {
                        fadeIn(AppMotion.enter(300)) togetherWith fadeOut(AppMotion.exit(200))
                    },
                    label = "tls_content_state"
                ) { state ->
                    when (state) {
                        is DisplayState.Idle    -> TlsIdlePlaceholder()
                        is DisplayState.Loading -> TlsLoadingContent()
                        is DisplayState.Canceled -> TlsCanceledContent()
                        is DisplayState.Error   -> TlsErrorContent(state.message, state.descriptionKey) { viewModel.inspect() }
                        is DisplayState.Success -> TlsSuccessContent(state.result)
                    }
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_tls_title),
            conceptHeading = stringResource(R.string.help_tls_concept_heading),
            conceptBody = stringResource(R.string.help_tls_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_tls_what_heading), stringResource(R.string.help_tls_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_tls_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_tls_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_tls_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_tls_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Display state ─────────────────────────────────────────────────────────────

private sealed class DisplayState {
    object Idle : DisplayState()
    object Loading : DisplayState()
    object Canceled : DisplayState()
    data class Error(val message: String, val descriptionKey: String?) : DisplayState()
    data class Success(val result: TlsInspectorResult) : DisplayState()
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun TlsHeaderCard(onHelpClick: () -> Unit) {
    ToolHeroHeader(
        title = stringResource(R.string.tls_screen_title),
        subtitle = stringResource(R.string.tls_screen_subtitle),
        icon = Icons.Default.Lock,
        onHelpClick = onHelpClick
    )
}

// ── Input section ─────────────────────────────────────────────────────────────

@Composable
private fun TlsInputSection(
    host: String,
    port: String,
    probeProtocols: Boolean,
    expectedPinSha256: String,
    isLoading: Boolean,
    isCanceling: Boolean,
    recentHosts: List<String>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onProbeProtocolsChange: (Boolean) -> Unit,
    onExpectedPinChange: (String) -> Unit,
    onInspect: () -> Unit,
    onCancel: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit
) {
    val dismissKeyboard = rememberDismissKeyboard()
    val protocolToggleLabel = stringResource(R.string.tls_probe_protocols_label)
    val normalizedHost = HostValidator.normalize(host)
    val isHostInvalid = host.isNotBlank() && normalizedHost == null
    val parsedPort = port.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1..65_535

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Host field
            ToolTextField(
                value         = host,
                onValueChange = onHostChange,
                label         = { Text(stringResource(R.string.tls_host_label)) },
                placeholder   = { Text(stringResource(R.string.tls_host_placeholder)) },
                leadingIcon   = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                isError       = isHostInvalid,
                supportingText = if (isHostInvalid) {
                    { Text(stringResource(HostValidator.problemOf(host)?.messageRes() ?: R.string.error_host_invalid)) }
                } else null,
                enabled       = !isLoading,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Next
                )
            )

            RecentHostsRow(
                recentHosts = recentHosts,
                onHostSelected = onHostChange,
                onRemoveHost = onRemoveRecentHost,
                onClearAll = onClearRecentHosts,
                selectionEnabled = !isLoading
            )

            // Port field
            OutlinedTextField(
                value         = port,
                onValueChange = onPortChange,
                label         = { Text(stringResource(R.string.tls_port_label)) },
                isError       = !isPortValid,
                supportingText = if (!isPortValid) {
                    { Text(stringResource(R.string.error_invalid_port)) }
                } else null,
                enabled       = !isLoading,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        dismissKeyboard()
                        if (!isLoading && normalizedHost != null && isPortValid) onInspect()
                    }
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.tls_probe_protocols_label), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.tls_probe_protocols_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = probeProtocols,
                    onCheckedChange = onProbeProtocolsChange,
                    enabled = !isLoading,
                    modifier = Modifier
                        .semantics { contentDescription = protocolToggleLabel }
                        .testTag(TlsInspectorScreenTestTags.PROTOCOL_PROBE_TOGGLE),
                )
            }

            OutlinedTextField(
                value = expectedPinSha256,
                onValueChange = onExpectedPinChange,
                label = { Text(stringResource(R.string.tls_expected_pin_label)) },
                placeholder = { Text(stringResource(R.string.tls_expected_pin_placeholder)) },
                supportingText = { Text(stringResource(R.string.tls_expected_pin_help)) },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TlsInspectorScreenTestTags.EXPECTED_PIN_FIELD)
                    .scrollIntoViewOnFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick   = hapticAction {
                        dismissKeyboard()
                        onInspect()
                    },
                    enabled   = normalizedHost != null && isPortValid && !isLoading,
                    modifier  = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(18.dp),
                            color     = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tls_inspecting))
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tls_inspect_button))
                    }
                }
                if (isLoading) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isCanceling,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (isCanceling) R.string.tls_stopping
                                else R.string.tls_cancel_inspection,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun TlsIdlePlaceholder() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text  = stringResource(R.string.tls_idle_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = stringResource(R.string.tls_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TlsCanceledContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tls_inspection_canceled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────

@Composable
private fun TlsLoadingContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text  = stringResource(R.string.tls_inspecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun TlsErrorContent(message: String, descriptionKey: String?, onRetry: () -> Unit) {
    val visibleMessage = when (descriptionKey) {
        TlsInspectorErrorKeys.PIN_INVALID_DESCRIPTION -> stringResource(R.string.tls_pin_invalid)
        TlsInspectorErrorKeys.NOT_TLS_PORT_DESCRIPTION -> stringResource(R.string.tls_not_tls_port)
        else -> message
    }
    ToolErrorCard(
        title = stringResource(R.string.tls_error_title),
        message = visibleMessage,
    ) {
        ToolRetryButton(text = stringResource(R.string.tls_retry), onClick = onRetry)
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun TlsSuccessContent(result: TlsInspectorResult) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.share_subject_tls, result.host, result.port)
    val pemShareSubject = stringResource(R.string.tls_share_pem_subject, result.host, result.port)
    val hasPem = result.chain.any { it.pemEncoded.isNotBlank() }
    val pemAction: (@Composable RowScope.() -> Unit)? =
        if (hasPem) {
            {
                TextButton(onClick = {
                    context.shareText(
                        text = buildTlsPemShareText(result),
                        subject = pemShareSubject,
                    )
                }) {
                    Text(stringResource(R.string.tls_share_pem))
                }
            }
        } else {
            null
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectionCard(result)
        result.chain.forEachIndexed { index, cert ->
            val certLabel = when {
                result.chain.size == 1 -> stringResource(R.string.tls_cert_leaf)
                index == 0             -> stringResource(R.string.tls_cert_leaf)
                index == result.chain.size - 1 -> stringResource(R.string.tls_cert_root)
                else                   -> stringResource(R.string.tls_cert_intermediate)
            }
            CertificateCard(
                cert      = cert,
                index     = index,
                certLabel = certLabel,
                expandedByDefault = index == 0
            )
        }
        ResultActionsRow(
            copyText = buildTlsShareText(result),
            onShare = {
                context.shareText(
                    text = buildTlsShareText(result),
                    subject = shareSubject
                )
            },
            extraActions = pemAction,
        )
    }
}

// ── Connection card ───────────────────────────────────────────────────────────

@Composable
private fun ConnectionCard(result: TlsInspectorResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    drawRect(
                        brush = Brush.linearGradient(listOf(StatusGood, StatusBlue))
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = stringResource(R.string.tls_connection_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Verdict, not the bare chain result: hostname, expiry and pin failures
                    // make the connection untrusted even when the chain verifies.
                    val trusted = result.isConnectionTrusted
                    SuggestionChip(
                        onClick = {},
                        label   = {
                            Text(
                                if (trusted) stringResource(R.string.tls_trusted)
                                else stringResource(R.string.tls_untrusted)
                            )
                        },
                        colors  = verdictChipColors(trusted),
                    )
                }

                Text(
                    text = verdictDetail(result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TlsInspectorScreenTestTags.VERDICT_DETAIL),
                )
                HorizontalDivider()

                LabeledValue(
                    label = stringResource(R.string.tls_version),
                    value = result.tlsVersion
                )
                LabeledValue(
                    label = stringResource(R.string.tls_cipher_suite),
                    value = withSeparatorBreaks(result.cipherSuite)
                )
                LabeledValue(
                    label = stringResource(R.string.tls_handshake_time),
                    value = stringResource(R.string.tls_ms_format, result.handshakeTimeMs)
                )
                LabeledValue(
                    label = stringResource(R.string.tls_connect_time),
                    value = stringResource(R.string.tls_ms_format, result.connectTimeMs)
                )
                result.alpn?.takeIf { it.isNotBlank() }?.let { alpn ->
                    LabeledValue(stringResource(R.string.tls_alpn), alpn)
                }
                result.protocolSupport?.toSortedMap()?.forEach { (protocol, supported) ->
                    LabeledValue(
                        label = protocol,
                        value = stringResource(if (supported) R.string.tls_supported else R.string.tls_not_supported),
                    )
                }
                result.protocolProbeUnknown.sorted().forEach { protocol ->
                    LabeledValue(protocol, stringResource(R.string.tls_probe_unknown))
                }
                result.protocolProbeNotTestable.sorted().forEach { protocol ->
                    LabeledValue(protocol, stringResource(R.string.tls_probe_not_testable))
                }
                if (result.chainIssues.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.tls_findings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    result.chainIssues.forEach { issue ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        issue.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    issueText(issue, result.untrustedRootName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One line that says what the verdict rests on, so the Trusted or Untrusted chip is not a bare claim. */
@Composable
private fun verdictDetail(result: TlsInspectorResult): String {
    val trusted = result.isConnectionTrusted
    return if (trusted && result.pinMatch == true) {
        stringResource(R.string.tls_verdict_trusted_pinned)
    } else if (trusted) {
        stringResource(R.string.tls_verdict_trusted)
    } else if (result.chainIssues.isNotEmpty()) {
        stringResource(R.string.tls_verdict_untrusted_findings)
    } else {
        stringResource(R.string.tls_verdict_untrusted_generic)
    }
}

/**
 * Verdict chip colours. Trusted uses the success token rather than the error role; untrusted uses
 * the error role. The verdict already folds in hostname, expiry and pin failures.
 */
@Composable
private fun verdictChipColors(trusted: Boolean): ChipColors {
    // Brighter green on dark surfaces keeps label contrast; the deeper one suits light surfaces.
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successText = if (onDark) StatusGood else StatusGoodDeep
    return SuggestionChipDefaults.suggestionChipColors(
        containerColor = if (trusted) StatusGoodDeep.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
        labelColor = if (trusted) successText else MaterialTheme.colorScheme.onErrorContainer,
    )
}

// ── Certificate card ──────────────────────────────────────────────────────────

@Composable
private fun CertificateCard(
    cert: TlsCertificate,
    index: Int,
    certLabel: String,
    expandedByDefault: Boolean
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row — always visible, tappable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        Modifier.then(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cert type badge
                Surface(
                    shape  = MaterialTheme.shapes.small,
                    color  = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text     = certLabel,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Subject CN
                Text(
                    text     = cert.subjectCN.ifBlank { stringResource(R.string.tls_unknown_cn) },
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.width(8.dp))

                // Expiry badge
                ExpiryBadge(cert)

                // Expand/collapse toggle
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded)
                            stringResource(R.string.tls_collapse)
                        else
                            stringResource(R.string.tls_expand)
                    )
                }
            }

            // Expanded body
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(AppMotion.enter(200)) + fadeIn(AppMotion.enter(200)),
                exit  = shrinkVertically(AppMotion.exit(200)) + fadeOut(AppMotion.exit(150)),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    CertificateDetails(cert)
                }
            }
        }
    }
}

@Composable
private fun ExpiryBadge(cert: TlsCertificate) {
    val daysLeft = TimeUnit.MILLISECONDS.toDays(cert.notAfter - System.currentTimeMillis())
    val tone = expiryTone(daysLeft = daysLeft, expired = cert.isExpired, notYetValid = cert.notYetValid)
    val label =
        when {
            cert.notYetValid -> stringResource(R.string.tls_not_yet_valid)
            cert.isExpired -> stringResource(R.string.tls_expired)
            else -> pluralStringResource(R.plurals.tls_days_left, daysLeft.toInt(), daysLeft)
        }
    // Colour carries the state; the text carries the number. Surfaces stay readable on light and dark.
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = expiryContainerColor(tone)
    val content = expiryContentColor(tone, onDark)
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier =
                Modifier
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag(TlsInspectorScreenTestTags.EXPIRY_BADGE),
        )
    }
}

@Composable
private fun expiryContainerColor(tone: ExpiryTone): Color =
    when (tone) {
        ExpiryTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        ExpiryTone.WARNING -> StatusWarn.copy(alpha = 0.22f)
        ExpiryTone.OK -> StatusGoodDeep.copy(alpha = 0.12f)
    }

@Composable
private fun expiryContentColor(
    tone: ExpiryTone,
    onDark: Boolean,
): Color =
    when (tone) {
        ExpiryTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        ExpiryTone.WARNING -> if (onDark) StatusWarn else StatusWarnDeep
        ExpiryTone.OK -> if (onDark) StatusGood else StatusGoodDeep
    }

@Composable
private fun CertificateDetails(cert: TlsCertificate) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Subject
        cert.subjectOrg?.let { LabeledValue(stringResource(R.string.tls_subject_org), it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Issuer
        LabeledValue(stringResource(R.string.tls_issuer_cn),  cert.issuerCN.ifBlank { "N/A" })
        cert.issuerOrg?.let { LabeledValue(stringResource(R.string.tls_issuer_org), it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Validity
        LabeledValue(stringResource(R.string.tls_valid_from), formatDate(cert.notBefore))
        LabeledValue(stringResource(R.string.tls_valid_to),   formatDate(cert.notAfter))
        if (cert.isCa) {
            Text(
                stringResource(R.string.tls_ca_certificate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cert.keyUsage.isNotEmpty()) {
            LabeledValue(stringResource(R.string.tls_key_usage), cert.keyUsage.joinToString(", "))
        }

        if (cert.isSelfSigned) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text  = stringResource(R.string.tls_self_signed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // SANs
        if (cert.sans.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text  = stringResource(R.string.tls_sans_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = cert.sans.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Technical details
        LabeledValue(stringResource(R.string.tls_serial),       cert.serialNumber)
        LabeledValue(stringResource(R.string.tls_sig_algo),     cert.signatureAlgorithm)
        LabeledValue(
            stringResource(R.string.tls_key_algo),
            if (cert.publicKeyBits > 0)
                stringResource(R.string.tls_key_algo_bits, cert.publicKeyAlgorithm, cert.publicKeyBits)
            else
                cert.publicKeyAlgorithm
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // SHA-256 fingerprint
        Text(
            text  = stringResource(R.string.tls_fingerprint_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text     = cert.sha256Fingerprint,
            style    = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color    = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.4f)
                .padding(end = 8.dp)
        )
        SelectionContainer(modifier = Modifier.weight(0.6f)) {
            Text(
                text     = value,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

internal fun buildTlsShareText(result: TlsInspectorResult): String = buildString {
    appendLine("TLS – ${result.host}:${result.port}")
    appendLine("Protocol: ${result.tlsVersion}")
    appendLine("Cipher: ${result.cipherSuite}")
    appendLine("Trusted: ${result.isConnectionTrusted}")
    appendLine("Chain trusted: ${result.isChainTrusted}")
    appendLine("Connect: ${result.connectTimeMs} ms; handshake: ${result.handshakeTimeMs} ms")
    result.alpn?.let { appendLine("ALPN: $it") }
    result.hostnameMatches?.let { appendLine("Hostname match: $it") }
    result.pinMatch?.let { appendLine("Expected pin match: $it") }
    result.protocolSupport?.toSortedMap()?.forEach { (protocol, supported) ->
        appendLine("$protocol: ${if (supported) "supported" else "not supported"}")
    }
    result.protocolProbeUnknown.sorted().forEach { appendLine("$it: could not determine") }
    result.protocolProbeNotTestable.sorted().forEach { appendLine("$it: not testable on this device") }
    if (result.chainIssues.isNotEmpty()) appendLine("Findings: ${result.chainIssues.joinToString { it.name }}")
    appendLine()
    result.chain.forEachIndexed { index, cert ->
        val label = when {
            result.chain.size == 1 || index == 0 -> "Leaf"
            index == result.chain.size - 1 -> "Root"
            else -> "Intermediate"
        }
        appendLine("[$label] ${cert.subjectCN}")
        appendLine("  Issuer: ${cert.issuerCN}")
        appendLine("  Valid: ${formatDate(cert.notBefore)} – ${formatDate(cert.notAfter)}")
        if (cert.sans.isNotEmpty()) {
            appendLine("  SANs: ${cert.sans.joinToString(", ")}")
        }
        appendLine("  SHA-256: ${cert.sha256Fingerprint}")
        appendLine()
    }
}

internal fun buildTlsPemShareText(result: TlsInspectorResult): String = result.chain
    .mapNotNull { it.pemEncoded.takeIf(String::isNotBlank)?.trim() }
    .joinToString(separator = "\n", postfix = "\n")

/** Names the missing issuing root when it is the only finding; otherwise the generic issue text. */
@Composable
private fun issueText(
    issue: net.aieat.netswissknife.core.network.tls.ChainIssue,
    untrustedRootName: String?,
): String =
    if (issue == net.aieat.netswissknife.core.network.tls.ChainIssue.UNTRUSTED && untrustedRootName != null) {
        stringResource(R.string.tls_issue_untrusted_root, untrustedRootName)
    } else {
        stringResource(issue.stringRes())
    }

private fun net.aieat.netswissknife.core.network.tls.ChainIssue.stringRes(): Int = when (this) {
    net.aieat.netswissknife.core.network.tls.ChainIssue.EXPIRED -> R.string.tls_issue_expired
    net.aieat.netswissknife.core.network.tls.ChainIssue.NOT_YET_VALID -> R.string.tls_issue_not_yet_valid
    net.aieat.netswissknife.core.network.tls.ChainIssue.EXPIRES_SOON -> R.string.tls_issue_expires_soon
    net.aieat.netswissknife.core.network.tls.ChainIssue.SELF_SIGNED -> R.string.tls_issue_self_signed
    net.aieat.netswissknife.core.network.tls.ChainIssue.UNTRUSTED -> R.string.tls_issue_untrusted
    net.aieat.netswissknife.core.network.tls.ChainIssue.HOSTNAME_MISMATCH -> R.string.tls_issue_hostname_mismatch
    net.aieat.netswissknife.core.network.tls.ChainIssue.WEAK_SIGNATURE -> R.string.tls_issue_weak_signature
    net.aieat.netswissknife.core.network.tls.ChainIssue.WEAK_KEY -> R.string.tls_issue_weak_key
    net.aieat.netswissknife.core.network.tls.ChainIssue.INCOMPLETE_CHAIN -> R.string.tls_issue_incomplete_chain
    net.aieat.netswissknife.core.network.tls.ChainIssue.PIN_MISMATCH -> R.string.tls_issue_pin_mismatch
}
