package net.aieat.netswissknife.app.ui.screens.tls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.theme.StatusBlue
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.app.ui.theme.StatusGood
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TlsInspectorScreen(viewModel: TlsInspectorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "tls_entrance_alpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 16.dp
            )
        ) {
            // Header
            item { TlsHeaderCard(onHelpClick = { showHelp = true }) }

            // Input section
            item {
                TlsInputSection(
                    host      = uiState.host,
                    port      = uiState.port,
                    isLoading = uiState.isLoading,
                    recentHosts = recentHosts,
                    onHostChange = viewModel::onHostChange,
                    onPortChange = viewModel::onPortChange,
                    onInspect    = viewModel::inspect,
                    onRemoveRecentHost = viewModel::removeRecentHost,
                    onClearRecentHosts = viewModel::clearRecentHosts
                )
            }

            // Content area — idle / loading / error / success
            item {
                val displayState = when {
                    uiState.isLoading          -> DisplayState.Loading
                    uiState.error != null      -> DisplayState.Error(uiState.error!!)
                    uiState.result != null     -> DisplayState.Success(uiState.result!!)
                    else                       -> DisplayState.Idle
                }

                AnimatedContent(
                    targetState   = displayState,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "tls_content_state"
                ) { state ->
                    when (state) {
                        is DisplayState.Idle    -> TlsIdlePlaceholder()
                        is DisplayState.Loading -> TlsLoadingContent()
                        is DisplayState.Error   -> TlsErrorContent(state.message) { viewModel.inspect() }
                        is DisplayState.Success -> TlsSuccessContent(state.result)
                    }
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_tls_title),
            sections = listOf(
                HelpSection(stringResource(R.string.help_tls_what_heading), stringResource(R.string.help_tls_what_body)),
                HelpSection(stringResource(R.string.help_tls_params_heading), stringResource(R.string.help_tls_params_body)),
                HelpSection(stringResource(R.string.help_tls_results_heading), stringResource(R.string.help_tls_results_body))
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Display state ─────────────────────────────────────────────────────────────

private sealed class DisplayState {
    object Idle : DisplayState()
    object Loading : DisplayState()
    data class Error(val message: String) : DisplayState()
    data class Success(val result: TlsInspectorResult) : DisplayState()
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun TlsHeaderCard(onHelpClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tls_screen_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.tls_screen_subtitle),
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
        }
    }
}

// ── Input section ─────────────────────────────────────────────────────────────

@Composable
private fun TlsInputSection(
    host: String,
    port: String,
    isLoading: Boolean,
    recentHosts: List<String>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onInspect: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Host field
            OutlinedTextField(
                value         = host,
                onValueChange = onHostChange,
                label         = { Text(stringResource(R.string.tls_host_label)) },
                placeholder   = { Text(stringResource(R.string.tls_host_placeholder)) },
                leadingIcon   = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon  = {
                    if (host.isNotEmpty()) {
                        IconButton(onClick = { onHostChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                },
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
                onClearAll = onClearRecentHosts
            )

            // Port field
            OutlinedTextField(
                value         = port,
                onValueChange = onPortChange,
                label         = { Text(stringResource(R.string.tls_port_label)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (host.isNotBlank()) onInspect()
                    }
                )
            )

            // Inspect button
            Button(
                onClick   = {
                    focusManager.clearFocus()
                    onInspect()
                },
                enabled   = host.isNotBlank() && !isLoading,
                modifier  = Modifier.fillMaxWidth()
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
private fun TlsErrorContent(message: String, onRetry: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors   = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.tls_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text  = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tls_retry))
            }
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun TlsSuccessContent(result: TlsInspectorResult) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = {
                context.shareText(
                    text = buildTlsShareText(result),
                    subject = context.getString(R.string.share_subject_tls, result.host, result.port)
                )
            }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
        }
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
                    SuggestionChip(
                        onClick = {},
                        label   = {
                            Text(
                                if (result.isChainTrusted) stringResource(R.string.tls_trusted)
                                else stringResource(R.string.tls_untrusted)
                            )
                        },
                        colors  = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (result.isChainTrusted)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }

                HorizontalDivider()

                LabeledValue(
                    label = stringResource(R.string.tls_host_port),
                    value = "${result.host}:${result.port}"
                )
                LabeledValue(
                    label = stringResource(R.string.tls_version),
                    value = result.tlsVersion
                )
                LabeledValue(
                    label = stringResource(R.string.tls_cipher_suite),
                    value = result.cipherSuite
                )
                LabeledValue(
                    label = stringResource(R.string.tls_handshake_time),
                    value = stringResource(R.string.tls_ms_format, result.handshakeTimeMs)
                )
            }
        }
    }
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

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
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
            AnimatedVisibility(visible = expanded) {
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
    val now = System.currentTimeMillis()
    val daysLeft = TimeUnit.MILLISECONDS.toDays(cert.notAfter - now)
    val expiryDate = formatDate(cert.notAfter)

    when {
        cert.isExpired -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text  = stringResource(R.string.tls_expired),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        daysLeft <= 30 -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text  = stringResource(R.string.tls_days_left, daysLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        else -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text  = expiryDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun CertificateDetails(cert: TlsCertificate) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Subject
        LabeledValue(stringResource(R.string.tls_subject_cn),  cert.subjectCN.ifBlank { "—" })
        cert.subjectOrg?.let { LabeledValue(stringResource(R.string.tls_subject_org), it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Issuer
        LabeledValue(stringResource(R.string.tls_issuer_cn),  cert.issuerCN.ifBlank { "—" })
        cert.issuerOrg?.let { LabeledValue(stringResource(R.string.tls_issuer_org), it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Validity
        LabeledValue(stringResource(R.string.tls_valid_from), formatDate(cert.notBefore))
        LabeledValue(stringResource(R.string.tls_valid_to),   formatDate(cert.notAfter))

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
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

private fun buildTlsShareText(result: TlsInspectorResult): String = buildString {
    appendLine("TLS – ${result.host}:${result.port}")
    appendLine("Protocol: ${result.tlsVersion}")
    appendLine("Cipher: ${result.cipherSuite}")
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
