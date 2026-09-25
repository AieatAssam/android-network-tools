package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.AccentBlueLight
import net.aieat.netswissknife.app.ui.theme.AccentBrown
import net.aieat.netswissknife.app.ui.theme.AccentGreenLight
import net.aieat.netswissknife.app.ui.theme.AccentGreyDeep
import net.aieat.netswissknife.app.ui.theme.AccentGreyLight
import net.aieat.netswissknife.app.ui.theme.AccentOrangeDeep
import net.aieat.netswissknife.app.ui.theme.AccentOrangeLight
import net.aieat.netswissknife.app.ui.theme.AccentPurple
import net.aieat.netswissknife.app.ui.theme.AccentRedDeep
import net.aieat.netswissknife.app.ui.theme.AccentRedLight
import net.aieat.netswissknife.app.ui.theme.AccentTeal
import net.aieat.netswissknife.app.ui.theme.StatusBlueDeep
import net.aieat.netswissknife.app.ui.theme.StatusGoodDeep
import net.aieat.netswissknife.app.ui.theme.StatusWarnDeep
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.util.formatBytes
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.CurlExporter
import net.aieat.netswissknife.core.network.httprobe.JsonPrettyPrinter
import net.aieat.netswissknife.core.network.httprobe.SecurityHeaderCheck
import net.aieat.netswissknife.core.network.httprobe.SecurityRating
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimingBreakdown
import net.aieat.netswissknife.core.domain.validateHttpProbeUrl
import net.aieat.netswissknife.app.ui.navigation.ToolSource

// ── Screen ────────────────────────────────────────────────────────────────────

object HttpProbeScreenTestTags {
    const val CONTENT_LIST = "httprobe_content_list"
    const val SOURCE_CONTEXT = "httprobe_source_context"
    const val CLEAR_PREFILL_ACTION = "httprobe_clear_prefill_action"
    const val INVALID_HANDOFF = "httprobe_invalid_handoff"
    const val BLOCKED_REDIRECT_WARNING = "httprobe_blocked_redirect_warning"
    const val TIMING_BAR = "httprobe_timing_bar"
    const val COPY_CURL_ACTION = "httprobe_copy_curl_action"
    const val PRETTY_JSON_SWITCH = "httprobe_pretty_json_switch"
    const val CURL_REDIRECT_NOTICE = "httprobe_curl_redirect_notice"

    /** Index of the idle/loading/error/success result panel within [CONTENT_LIST]. */
    const val RESULT_PANEL_INDEX = 2
}

@Composable
fun HttpProbeScreen(viewModel: HttpProbeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val hasInvalidHandoff by viewModel.hasInvalidHandoff.collectAsStateWithLifecycle()
    val sourceContext by viewModel.sourceContextState.collectAsStateWithLifecycle()
    val announcementPhase = when {
        uiState.isLoading -> ToolAnnouncementPhase.RUNNING
        uiState.isCanceled -> ToolAnnouncementPhase.CANCELED
        uiState.error != null || uiState.blockedRedirectWarning != null -> ToolAnnouncementPhase.ERROR
        uiState.result != null -> ToolAnnouncementPhase.FINISHED
        else -> null
    }
    ToolStateAnnouncer(stringResource(R.string.help_httprobe_title), announcementPhase)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label = "httprobe_entrance_alpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter(400)) + slideInVertically(AppMotion.enter(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(HttpProbeScreenTestTags.CONTENT_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 16.dp
            )
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HttpProbeHeaderCard(onHelpClick = { showHelp = true })
                    NetworkStatusBanner(networkStatus, scope = NetworkStatusScope.INTERNET)
                    if (hasInvalidHandoff) {
                        Text(
                            text = stringResource(R.string.httprobe_invalid_handoff),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(HttpProbeScreenTestTags.INVALID_HANDOFF),
                        )
                    }
                }
            }

            val sourceLabel = when (sourceContext) {
                ToolSource.LAN -> R.string.httprobe_source_lan
                ToolSource.MDNS -> R.string.httprobe_source_mdns
                else -> null
            }
            if (sourceLabel != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(sourceLabel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag(HttpProbeScreenTestTags.SOURCE_CONTEXT),
                        )
                        TextButton(
                            onClick = viewModel::clearPrefill,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.testTag(HttpProbeScreenTestTags.CLEAR_PREFILL_ACTION),
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
            }

            item {
                HttpProbeInputCard(
                    uiState = uiState,
                    recentHosts = recentHosts,
                    onUrlChange = viewModel::onUrlChange,
                    onMethodChange = viewModel::onMethodChange,
                    onBodyChange = viewModel::onBodyChange,
                    onFollowRedirectsToggle = viewModel::onFollowRedirectsToggle,
                    onToggleHeaders = viewModel::onToggleHeadersExpanded,
                    onAddHeader = viewModel::addHeader,
                    onRemoveHeader = viewModel::removeHeader,
                    onHeaderKeyChange = viewModel::updateHeaderKey,
                    onHeaderValueChange = viewModel::updateHeaderValue,
                    onSend = viewModel::send,
                    onCancel = viewModel::cancel,
                    onRemoveRecentHost = viewModel::removeRecentHost,
                    onClearRecentHosts = viewModel::clearRecentHosts
                )
            }

            uiState.pendingEntityReplayApproval?.let { approval ->
                item(key = "entity-replay-${approval.runId}-${approval.destinationUrl}") {
                    CrossOriginEntityReplayApprovalCard(
                        approval = approval,
                        onApprove = {
                            viewModel.respondToEntityReplayApproval(
                                approval.runId,
                                approval.approvalId,
                                approved = true
                            )
                        },
                        onDeny = {
                            viewModel.respondToEntityReplayApproval(
                                approval.runId,
                                approval.approvalId,
                                approved = false
                            )
                        }
                    )
                }
            }

            item {
                val displayState: DisplayState = when {
                    uiState.isLoading     -> DisplayState.Loading
                    uiState.isCanceled    -> DisplayState.Canceled
                    uiState.blockedRedirectWarning != null ->
                        DisplayState.BlockedRedirect(requireNotNull(uiState.blockedRedirectWarning))
                    uiState.error != null -> DisplayState.Error(uiState.error!!)
                    uiState.result != null -> DisplayState.Success(uiState.result!!)
                    else                  -> DisplayState.Idle
                }

                AnimatedContent(
                    targetState = displayState,
                    transitionSpec = { fadeIn(AppMotion.enter(300)) togetherWith fadeOut(AppMotion.exit(200)) },
                    label = "httprobe_content_state"
                ) { state ->
                    when (state) {
                        is DisplayState.Idle    -> HttpProbeIdlePlaceholder()
                        is DisplayState.Loading -> HttpProbeLoadingContent()
                        is DisplayState.Canceled -> HttpProbeCanceledContent()
                        is DisplayState.Error   -> HttpProbeErrorContent(state.message) { viewModel.send() }
                        is DisplayState.BlockedRedirect -> HttpProbeBlockedRedirectContent(state.warning)
                        is DisplayState.Success -> {
                            val shareSubject = stringResource(
                                R.string.share_subject_http,
                                safeRedirectDisplayValue(state.result.request.url),
                            )
                            val shareSizeText = responseSizeText(
                                state.result,
                                stringResource(R.string.httprobe_response_size_at_least)
                            )
                            HttpProbeSuccessContent(
                                result = state.result,
                                selectedTab = uiState.selectedTab,
                                prettyJson = uiState.prettyJson,
                                onTabSelected = viewModel::onTabSelected,
                                onPrettyJsonToggle = viewModel::onPrettyJsonToggle,
                                onCopyCurl = {
                                    viewModel.copyAsCurl()?.let { command ->
                                        clipboardScope.launch {
                                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("cURL", command)))
                                        }
                                    }
                                },
                                onShare = {
                                    context.shareText(
                                        text = buildHttpShareText(state.result, shareSizeText),
                                        subject = shareSubject
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_httprobe_title),
            conceptHeading = stringResource(R.string.help_httprobe_concept_heading),
            conceptBody = stringResource(R.string.help_httprobe_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_httprobe_what_heading), stringResource(R.string.help_httprobe_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_httprobe_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_httprobe_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_httprobe_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_httprobe_results_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_httprobe_security_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_httprobe_security_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun CrossOriginEntityReplayApprovalCard(
    approval: PendingEntityReplayApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.httprobe_cross_origin_replay_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(stringResource(
                R.string.httprobe_cross_origin_replay_message,
                approval.statusCode,
                approval.method.name
            ))
            Text(
                text = safeRedirectDisplayValue(approval.destinationUrl),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove) {
                    Text(stringResource(R.string.httprobe_cross_origin_replay_approve))
                }
                TextButton(onClick = onDeny) {
                    Text(stringResource(R.string.httprobe_cross_origin_replay_deny))
                }
            }
        }
    }
}

// ── Display state ─────────────────────────────────────────────────────────────

private sealed class DisplayState {
    object Idle : DisplayState()
    object Loading : DisplayState()
    object Canceled : DisplayState()
    data class Error(val message: String) : DisplayState()
    data class BlockedRedirect(val warning: BlockedHttpRedirectWarning) : DisplayState()
    data class Success(val result: HttpProbeResult) : DisplayState()
}

@Composable
private fun HttpProbeBlockedRedirectContent(warning: BlockedHttpRedirectWarning) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag(HttpProbeScreenTestTags.BLOCKED_REDIRECT_WARNING),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.httprobe_blocked_redirect_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.httprobe_blocked_redirect_message, warning.statusCode),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            RedirectEvidenceLine(R.string.httprobe_blocked_redirect_source, warning.sourceUrl)
            RedirectEvidenceLine(R.string.httprobe_blocked_redirect_destination, warning.destinationUrl)
            RedirectEvidenceLine(R.string.httprobe_blocked_redirect_location, warning.location)
        }
    }
}

@Composable
private fun RedirectEvidenceLine(label: Int, value: String) {
    Text(
        text = stringResource(label, safeRedirectDisplayValue(value)),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun HttpProbeHeaderCard(onHelpClick: () -> Unit) {
    ToolHeroHeader(
        title = stringResource(R.string.httprobe_screen_title),
        subtitle = stringResource(R.string.httprobe_screen_subtitle),
        icon = Icons.Default.Http,
        onHelpClick = onHelpClick
    )
}

// ── Input card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HttpProbeInputCard(
    uiState: HttpProbeUiState,
    recentHosts: List<String>,
    onUrlChange: (String) -> Unit,
    onMethodChange: (HttpMethod) -> Unit,
    onBodyChange: (String) -> Unit,
    onFollowRedirectsToggle: () -> Unit,
    onToggleHeaders: () -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onHeaderKeyChange: (Int, String) -> Unit,
    onHeaderValueChange: (Int, String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val url = uiState.url
    val formEnabled = !uiState.isLoading
    val isUrlInvalid = url.isNotBlank() && validateHttpProbeUrl(url) != null

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // URL field
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                enabled = formEnabled,
                label = { Text(stringResource(R.string.httprobe_url_label)) },
                placeholder = { Text(stringResource(R.string.httprobe_url_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Http, contentDescription = null) },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }, enabled = formEnabled) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                isError = isUrlInvalid,
                supportingText = if (isUrlInvalid) {
                    { Text(stringResource(R.string.error_invalid_url)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSend()
                })
            )

            RecentHostsRow(
                recentHosts = recentHosts,
                onHostSelected = onUrlChange,
                onRemoveHost = onRemoveRecentHost,
                onClearAll = onClearRecentHosts,
                selectionEnabled = formEnabled,
            )

            // Method selector
            Text(
                text = stringResource(R.string.httprobe_method_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HttpMethod.values().forEach { method ->
                    val selected = uiState.method == method
                    FilterChip(
                        selected = selected,
                        onClick = { onMethodChange(method) },
                        enabled = formEnabled,
                        label = { Text(method.name, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = methodColor(method).copy(alpha = 0.2f),
                            selectedLabelColor = methodColor(method)
                        )
                    )
                }
            }

            // Custom headers toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.httprobe_custom_headers_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.customHeaders.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${uiState.customHeaders.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onToggleHeaders,
                    enabled = formEnabled,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (uiState.headersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (uiState.headersExpanded) R.string.action_collapse else R.string.action_expand),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onAddHeader,
                    enabled = formEnabled,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.httprobe_add_header), modifier = Modifier.size(20.dp))
                }
            }

            AnimatedVisibility(
                visible = uiState.headersExpanded && uiState.customHeaders.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.customHeaders.forEachIndexed { index, header ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = header.key,
                                onValueChange = { onHeaderKeyChange(index, it) },
                                enabled = formEnabled,
                                label = { Text(stringResource(R.string.httprobe_header_key), style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = header.value,
                                onValueChange = { onHeaderValueChange(index, it) },
                                enabled = formEnabled,
                                label = { Text(stringResource(R.string.httprobe_header_value), style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = { onRemoveHeader(index) },
                                enabled = formEnabled,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.httprobe_remove_header),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Request body (only for methods that support it)
            AnimatedVisibility(
                visible = uiState.method.supportsBody,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = uiState.body,
                    onValueChange = onBodyChange,
                    enabled = formEnabled,
                    label = { Text(stringResource(R.string.httprobe_body_label)) },
                    placeholder = { Text(stringResource(R.string.httprobe_body_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 6
                )
            }

            // Follow redirects toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.httprobe_follow_redirects_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.httprobe_follow_redirects_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.followRedirects,
                    onCheckedChange = { onFollowRedirectsToggle() },
                    enabled = formEnabled,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = hapticAction {
                        focusManager.clearFocus()
                        onSend()
                    },
                    enabled = uiState.url.isNotBlank() && !uiState.isLoading && !isUrlInvalid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.httprobe_sending))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.httprobe_send_button))
                    }
                }
                if (uiState.isLoading) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !uiState.isCanceling,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isCanceling) R.string.httprobe_stopping
                                else R.string.httprobe_cancel_request,
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
private fun HttpProbeIdlePlaceholder() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Http,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.httprobe_idle_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.httprobe_idle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HttpProbeCanceledContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.httprobe_request_canceled),
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
private fun HttpProbeLoadingContent() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = stringResource(R.string.httprobe_sending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun HttpProbeErrorContent(message: String, onRetry: () -> Unit) {
    ToolErrorCard(
        title = stringResource(R.string.httprobe_error_title),
        message = message,
    ) {
        TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.httprobe_retry))
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun HttpProbeSuccessContent(
    result: HttpProbeResult,
    selectedTab: Int,
    prettyJson: Boolean,
    onTabSelected: (Int) -> Unit,
    onPrettyJsonToggle: () -> Unit,
    onCopyCurl: () -> Unit,
    onShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
            TextButton(
                onClick = onCopyCurl,
                modifier = Modifier.testTag(HttpProbeScreenTestTags.COPY_CURL_ACTION),
            ) {
                Text(stringResource(R.string.httprobe_copy_curl))
            }
        }
        if (CurlExporter.redirectFollowingSuppressed(result.request)) {
            Text(
                text = stringResource(R.string.httprobe_curl_redirect_safety),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(HttpProbeScreenTestTags.CURL_REDIRECT_NOTICE),
            )
        }
        // Status banner
        StatusBannerCard(result)

        // Tabs
        val tabs = listOf(
            stringResource(R.string.httprobe_tab_overview),
            stringResource(R.string.httprobe_tab_headers),
            stringResource(R.string.httprobe_tab_body),
            stringResource(R.string.httprobe_tab_security)
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(AppMotion.enter(200)) togetherWith fadeOut(AppMotion.exit(150)) },
                label = "httprobe_tab_content"
            ) { tab ->
                when (tab) {
                    0 -> OverviewTabContent(result)
                    1 -> HeadersTabContent(result)
                    2 -> BodyTabContent(result, prettyJson, onPrettyJsonToggle)
                    3 -> SecurityTabContent(result.securityChecks)
                    else -> OverviewTabContent(result)
                }
            }
        }
    }
}

// ── Status banner ─────────────────────────────────────────────────────────────

@Composable
private fun StatusBannerCard(result: HttpProbeResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient top bar matching status class
            Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                drawRect(brush = Brush.horizontalGradient(statusGradient(result.statusCode)))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = result.statusCode.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusCodeColor(result.statusCode)
                        )
                        Text(
                            text = result.statusMessage,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = safeRedirectDisplayValue(result.finalUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${result.responseTimeMs} ms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = timingColor(result.responseTimeMs)
                    )
                    Text(
                        text = stringResource(R.string.httprobe_response_time_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result.redirectChain.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pluralStringResource(R.plurals.httprobe_redirects_label, result.redirectChain.size, result.redirectChain.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Overview tab ──────────────────────────────────────────────────────────────

@Composable
private fun OverviewTabContent(result: HttpProbeResult) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LabeledValue(stringResource(R.string.httprobe_method_used), result.request.method.name)
        LabeledValue(stringResource(R.string.httprobe_protocol_label), result.protocol)
        TimingBreakdown(result.timings)
        LabeledValue(stringResource(R.string.httprobe_final_url), safeRedirectDisplayValue(result.finalUrl))
        LabeledValue(
            stringResource(R.string.httprobe_response_size),
            responseSizeText(result, stringResource(R.string.httprobe_response_size_at_least))
        )
        LabeledValue(
            stringResource(R.string.httprobe_content_type),
            result.responseHeaders["Content-Type"]?.firstOrNull() ?: "N/A"
        )
        if (result.redirectChain.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.httprobe_redirect_chain),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val hops = result.redirectHops
            val sources = if (hops.isNotEmpty()) hops.map { it.url } else result.redirectChain
            sources.forEachIndexed { index, url ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (hops.isNotEmpty()) {
                        Text(
                            text = hops[index].statusCode.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = safeRedirectDisplayValue(url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingBreakdown(timings: HttpTimings) {
    val breakdown = HttpTimingBreakdown.from(timings)
    val segments = listOf(
        Triple(R.string.httprobe_timing_dns, breakdown.dnsMs, MaterialTheme.colorScheme.primary),
        Triple(R.string.httprobe_timing_connect, breakdown.connectMs, MaterialTheme.colorScheme.secondary),
        Triple(R.string.httprobe_timing_tls, breakdown.tlsMs, MaterialTheme.colorScheme.tertiary),
        Triple(R.string.httprobe_timing_server_wait, breakdown.serverWaitMs, StatusBlueDeep),
        Triple(R.string.httprobe_timing_transfer, breakdown.transferMs, StatusGoodDeep),
    ).filter { (_, value, _) -> value != null }
    val total = segments.sumOf { (_, value, _) -> value!!.coerceAtLeast(0L) }
    val visibleSegments = if (total == 0L) emptyList() else segments.filter { (_, value, _) -> value!! > 0L }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.httprobe_timing_breakdown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(HttpProbeScreenTestTags.TIMING_BAR),
        ) {
            visibleSegments.forEach { (_, value, color) ->
                Box(
                    Modifier
                        .weight(value!!.toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
        if (segments.isEmpty()) {
            Text(stringResource(R.string.httprobe_timing_unavailable), style = MaterialTheme.typography.bodySmall)
        } else {
            segments.forEach { (label, value, _) ->
                LabeledValue(stringResource(label), stringResource(R.string.httprobe_timing_value_ms, value ?: 0L))
            }
            breakdown.ttfbTotalMs?.let { totalTtfb ->
                LabeledValue(stringResource(R.string.httprobe_timing_ttfb), stringResource(R.string.httprobe_timing_value_ms, totalTtfb))
            }
        }
    }
}

// ── Headers tab ───────────────────────────────────────────────────────────────

@Composable
private fun HeadersTabContent(result: HttpProbeResult) {
    var showRequest by remember { mutableStateOf(false) }
    var showResponse by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Request headers section
        HeaderSection(
            title = stringResource(R.string.httprobe_request_headers),
            expanded = showRequest,
            onToggle = { showRequest = !showRequest }
        ) {
            if (result.request.headers.isEmpty()) {
                Text(
                    text = stringResource(R.string.httprobe_no_custom_headers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                result.request.headers.forEach { (key, value) ->
                    HeaderRow(key, value)
                }
            }
        }

        // Response headers section
        HeaderSection(
            title = stringResource(R.string.httprobe_response_headers),
            expanded = showResponse,
            onToggle = { showResponse = !showResponse }
        ) {
            val displayHeaders = visibleHttpResponseHeaders(result.responseHeaders)
            if (displayHeaders.isEmpty()) {
                Text(
                    text = stringResource(R.string.httprobe_no_headers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayHeaders.entries.sortedBy { it.key }.forEach { (key, values) ->
                    HeaderRow(key, values.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggle, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.4f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// ── Body tab ──────────────────────────────────────────────────────────────────

@Composable
private fun BodyTabContent(result: HttpProbeResult, prettyJson: Boolean, onPrettyJsonToggle: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val responseBody = result.responseBody
    val formattedJson = remember(responseBody, result.responseBodyTruncated) {
        if (result.responseBodyTruncated) null else responseBody?.let(JsonPrettyPrinter::prettyPrint)
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.httprobe_response_body),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!responseBody.isNullOrBlank()) {
                TextButton(
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", responseBody))) } }
                ) {
                    Text(stringResource(R.string.httprobe_copy_body), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (result.responseBodyTruncated) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = stringResource(R.string.httprobe_body_truncated, formatBytes(result.responseBodyBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (responseBody.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.httprobe_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.httprobe_pretty_json), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(
                        when {
                            result.responseBodyTruncated -> R.string.httprobe_json_incomplete
                            formattedJson != null -> R.string.httprobe_json_valid
                            else -> R.string.httprobe_json_invalid
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        result.responseBodyTruncated -> MaterialTheme.colorScheme.onSurfaceVariant
                        formattedJson != null -> StatusGoodDeep
                        else -> StatusWarnDeep
                    },
                )
                Switch(
                    checked = prettyJson,
                    onCheckedChange = { onPrettyJsonToggle() },
                    enabled = formattedJson != null && !result.responseBodyTruncated,
                    modifier = Modifier.testTag(HttpProbeScreenTestTags.PRETTY_JSON_SWITCH),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (prettyJson) formattedJson ?: responseBody else responseBody,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Security tab ──────────────────────────────────────────────────────────────

@Composable
private fun SecurityTabContent(checks: List<SecurityHeaderCheck>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Summary row
        val passCount = checks.count { it.rating == SecurityRating.PASS }
        val warnCount = checks.count { it.rating == SecurityRating.WARN }
        val failCount = checks.count { it.rating == SecurityRating.FAIL }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecuritySummaryChip(count = passCount, label = stringResource(R.string.security_rating_pass), color = StatusGoodDeep)
            SecuritySummaryChip(count = warnCount, label = stringResource(R.string.security_rating_warn), color = StatusWarnDeep)
            SecuritySummaryChip(count = failCount, label = stringResource(R.string.security_rating_fail), color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()

        checks.forEach { check ->
            SecurityCheckRow(check)
        }
    }
}

@Composable
private fun SecuritySummaryChip(count: Int, label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SecurityCheckRow(check: SecurityHeaderCheck) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityRatingIcon(check.rating, Modifier.size(18.dp))

                Text(
                    text = check.headerName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                SecurityRatingBadge(check.rating)

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                val checkValue = check.value
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (checkValue != null) {
                        Text(
                            text = checkValue,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                    }
                    Text(
                        text = securityDescription(check),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun securityDescription(check: SecurityHeaderCheck): String = when (check.descriptionKey) {
    "httprobe_sec_hsts_pass_strong" -> stringResource(R.string.httprobe_sec_hsts_pass_strong)
    "httprobe_sec_hsts_short" -> stringResource(R.string.httprobe_sec_hsts_short)
    "httprobe_sec_hsts_invalid" -> stringResource(R.string.httprobe_sec_hsts_invalid)
    "httprobe_sec_hsts_ignored_http" -> stringResource(R.string.httprobe_sec_hsts_ignored_http)
    "httprobe_sec_csp_unsafe_inline" -> stringResource(R.string.httprobe_sec_csp_unsafe_inline)
    "httprobe_sec_csp_ambiguous" -> stringResource(R.string.httprobe_sec_csp_ambiguous)
    "httprobe_sec_csp_report_only" -> stringResource(R.string.httprobe_sec_csp_report_only)
    "httprobe_sec_xfo_via_csp" -> stringResource(R.string.httprobe_sec_xfo_via_csp)
    "httprobe_sec_xss_protection_absent" -> stringResource(R.string.httprobe_sec_xss_protection_absent)
    "httprobe_sec_xss_protection_deprecated" -> stringResource(R.string.httprobe_sec_xss_protection_deprecated)
    "httprobe_sec_cookie_flags_complete" -> stringResource(R.string.httprobe_sec_cookie_flags_complete)
    "httprobe_sec_cookie_missing_secure" -> stringResource(R.string.httprobe_sec_cookie_missing_secure)
    "httprobe_sec_cookie_missing_httponly" -> stringResource(R.string.httprobe_sec_cookie_missing_httponly)
    "httprobe_sec_cookie_missing_secure_httponly" -> stringResource(R.string.httprobe_sec_cookie_missing_secure_httponly)
    "httprobe_sec_cookie_missing_samesite" -> stringResource(R.string.httprobe_sec_cookie_missing_samesite)
    "httprobe_sec_cookie_samesite_none_requires_secure" -> stringResource(R.string.httprobe_sec_cookie_samesite_none_requires_secure)
    "httprobe_sec_cookie_secure_from_http" -> stringResource(R.string.httprobe_sec_cookie_secure_from_http)
    "httprobe_sec_cookie_secure_not_applicable" -> stringResource(R.string.httprobe_sec_cookie_secure_not_applicable)
    "httprobe_sec_cookie_malformed" -> stringResource(R.string.httprobe_sec_cookie_malformed)
    else -> check.description
}

@Composable
private fun SecurityRatingIcon(rating: SecurityRating, modifier: Modifier = Modifier) {
    when (rating) {
        SecurityRating.PASS -> Icon(Icons.Default.Check, null, modifier = modifier, tint = StatusGoodDeep)
        SecurityRating.WARN -> Icon(Icons.Default.Warning, null, modifier = modifier, tint = StatusWarnDeep)
        SecurityRating.FAIL -> Icon(Icons.Default.Clear, null, modifier = modifier, tint = MaterialTheme.colorScheme.error)
        SecurityRating.INFO -> Icon(Icons.Default.Info, null, modifier = modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityRatingBadge(rating: SecurityRating) {
    val (bg, fg, label) = when (rating) {
        SecurityRating.PASS -> Triple(StatusGoodDeep.copy(alpha = 0.12f), StatusGoodDeep, stringResource(R.string.security_rating_pass))
        SecurityRating.WARN -> Triple(StatusWarnDeep.copy(alpha = 0.12f), StatusWarnDeep, stringResource(R.string.security_rating_warn))
        SecurityRating.FAIL -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, stringResource(R.string.security_rating_fail))
        SecurityRating.INFO -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.security_rating_info))
    }
    val badgeLabel = label.uppercase()
    Surface(shape = MaterialTheme.shapes.small, color = bg) {
        Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f).padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun statusCodeColor(code: Int): Color = when (code) {
    in 200..299 -> StatusGoodDeep
    in 300..399 -> MaterialTheme.colorScheme.primary
    in 400..499 -> StatusWarnDeep
    in 500..599 -> MaterialTheme.colorScheme.error
    else        -> MaterialTheme.colorScheme.onSurface
}

private fun statusGradient(code: Int): List<Color> = when (code) {
    in 200..299 -> listOf(StatusGoodDeep, AccentGreenLight)
    in 300..399 -> listOf(StatusBlueDeep, AccentBlueLight)
    in 400..499 -> listOf(AccentOrangeDeep, AccentOrangeLight)
    in 500..599 -> listOf(AccentRedDeep, AccentRedLight)
    else        -> listOf(AccentGreyDeep, AccentGreyLight)
}

@Composable
private fun timingColor(ms: Long): Color = when {
    ms < 200  -> StatusGoodDeep
    ms < 800  -> StatusWarnDeep
    else      -> MaterialTheme.colorScheme.error
}

@Composable
private fun methodColor(method: HttpMethod): Color = when (method) {
    HttpMethod.GET     -> StatusBlueDeep
    HttpMethod.POST    -> StatusGoodDeep
    HttpMethod.PUT     -> StatusWarnDeep
    HttpMethod.PATCH   -> AccentPurple
    HttpMethod.DELETE  -> MaterialTheme.colorScheme.error
    HttpMethod.HEAD    -> AccentTeal
    HttpMethod.OPTIONS -> AccentBrown
}

/**
 * A bounded read never sees past the cap, so the buffered count is the true
 * total only when nothing was truncated. Once it was, fall back to the server's
 * Content-Length, and otherwise say "at least N" rather than presenting a
 * buffered prefix as if it were the whole body.
 */
private fun responseSizeText(result: HttpProbeResult, atLeastFormat: String): String {
    val declared = result.declaredBodyBytes
    return when {
        // A complete read counted every byte, so it is exact. Content-Length is
        // not: HttpURLConnection gunzips transparently, so on a compressed
        // response the header describes the wire size, not what was decoded.
        !result.responseBodyTruncated -> formatBytes(result.responseBodyBytes)
        declared != null -> formatBytes(declared)
        else -> atLeastFormat.format(formatBytes(result.responseBodyBytes))
    }
}
