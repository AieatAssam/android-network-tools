package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.clipToBounds
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.components.NetworkStatusBanner
import net.aieat.netswissknife.app.ui.components.NetworkStatusScope
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.components.ToolTextField
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.RecentHostsRow
import net.aieat.netswissknife.app.ui.components.ScrollEdgeFadeWidth
import net.aieat.netswissknife.app.ui.components.scrollIntoViewOnFocus
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.screens.dns.DnsUiState
import net.aieat.netswissknife.app.ui.screens.dns.DnsViewModel
import net.aieat.netswissknife.app.ui.screens.dns.dnsRecordIconLabel
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.app.ui.components.rememberDismissKeyboard
import net.aieat.netswissknife.app.ui.components.scrollEdgeFade
import net.aieat.netswissknife.core.network.HostProblem
import net.aieat.netswissknife.core.network.dns.DnsNameValidator
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import net.aieat.netswissknife.app.ui.i18n.asString
import net.aieat.netswissknife.app.ui.i18n.uiDescription
import net.aieat.netswissknife.app.ui.i18n.uiLabel
import net.aieat.netswissknife.app.ui.screens.dns.DnsResponseTone
import net.aieat.netswissknife.app.ui.screens.dns.dnsResponseTone

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Handles for instrumentation tests. The result panel is the third item of a
 * LazyColumn, so on a phone viewport it is not composed until scrolled to;
 * tests need a stable handle on the list to reach it.
 */
object DnsScreenTestTags {
    const val CONTENT_LIST = "dns_content_list"
    const val DOMAIN_INPUT = "dns_domain_input"
    const val RECORD_TYPE_CHIPS = "dns_record_type_chips"
    const val RECORD_TYPE_SCROLL_HINT = "dns_record_type_scroll_hint"
    const val CANCEL_LOOKUP = "dns_cancel_lookup"

    /** Index of the idle/loading/error/success panel within [CONTENT_LIST]. */
    const val STATE_PANEL_INDEX = 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(viewModel: DnsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val announcementPhase = when (uiState) {
        DnsUiState.Idle -> null
        DnsUiState.Loading, DnsUiState.Canceling -> ToolAnnouncementPhase.RUNNING
        DnsUiState.Canceled -> ToolAnnouncementPhase.CANCELED
        is DnsUiState.Error -> ToolAnnouncementPhase.ERROR
        is DnsUiState.Success -> ToolAnnouncementPhase.FINISHED
    }
    val announcementDetail = when (val state = uiState) {
        is DnsUiState.Success -> pluralStringResource(
            R.plurals.a11y_dns_record_count,
            state.result.records.size,
            state.result.records.size,
        )
        else -> null
    }
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val domain by viewModel.domain.collectAsStateWithLifecycle()
    val recordType by viewModel.recordType.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val customServerAddress by viewModel.customServerAddress.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val screenAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(400),
        label         = "screen-alpha"
    )
    var showHelp by remember { mutableStateOf(false) }
    val isRefreshing = uiState is DnsUiState.Loading
    val canRefresh = uiState is DnsUiState.Success || uiState is DnsUiState.Error

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (canRefresh) viewModel.onRetry() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DnsScreenTestTags.CONTENT_LIST)
            .background(MaterialTheme.colorScheme.background)
            .alpha(screenAlpha),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                Box {
                    ToolStateAnnouncer(
                        stringResource(R.string.help_dns_title),
                        announcementPhase,
                        detail = announcementDetail,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DnsHeroHeader(onHelpClick = { showHelp = true })
                        NetworkStatusBanner(networkStatus, scope = NetworkStatusScope.INTERNET)
                    }
                }
            }

            item {
                DnsInputCard(
                    domain = domain,
                    recordType = recordType,
                    selectedServer = selectedServer,
                    customServerAddress = customServerAddress,
                    isLoading = uiState is DnsUiState.Loading,
                    isCanceling = uiState is DnsUiState.Canceling,
                    recentHosts = recentHosts,
                    onDomainChange = viewModel::onDomainChange,
                    onRecordTypeChange = viewModel::onRecordTypeChange,
                    onServerChange = viewModel::onServerChange,
                    onCustomServerAddressChange = viewModel::onCustomServerAddressChange,
                    onLookup = viewModel::performLookup,
                    onCancelLookup = viewModel::onStopLookup,
                    onRemoveRecentHost = viewModel::removeRecentHost,
                    onClearRecentHosts = viewModel::clearRecentHosts
                )
            }

            item {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        (fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 8 })
                            .togetherWith(fadeOut(AppMotion.exit(200)))
                    },
                    contentKey = { it::class },
                    label = "dns-result-state"
                ) { state ->
                    when (state) {
                        is DnsUiState.Idle -> DnsIdleHint(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        is DnsUiState.Loading -> DnsLoadingPanel(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        is DnsUiState.Canceling -> DnsCancelingPanel(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        is DnsUiState.Canceled -> DnsCanceledPanel(
                            modifier = Modifier.padding(vertical = 8.dp),
                            onClear = viewModel::onClearResults
                        )
                        is DnsUiState.Error -> DnsErrorPanel(
                            message = state.message,
                            onRetry = viewModel::onRetry,
                            canFallbackToCloudflare = state.canFallbackToCloudflare,
                            onUseCloudflare = viewModel::onUseCloudflare
                        )
                        is DnsUiState.Success -> DnsResultPanel(
                            result = state.result,
                            showRaw = state.showRaw,
                            onToggleRaw = viewModel::onToggleRawView,
                            onClear = viewModel::onClearResults
                        )
                    }
                }
            }
        }
    } // end PullToRefreshBox

    if (showHelp) {
        ToolHelpSheet(
            title = stringResource(R.string.help_dns_title),
            conceptHeading = stringResource(R.string.help_dns_concept_heading),
            conceptBody = stringResource(R.string.help_dns_concept_body),
            sections = listOf(
                HelpSection(stringResource(R.string.help_dns_what_heading), stringResource(R.string.help_dns_what_body)),
                HelpSection(
                    heading = stringResource(R.string.help_dns_params_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_dns_params_bullets).toList()
                ),
                HelpSection(
                    heading = stringResource(R.string.help_dns_results_heading),
                    body = "",
                    bullets = stringArrayResource(R.array.help_dns_results_bullets).toList()
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

// ── Hero header ───────────────────────────────────────────────────────────────

@Composable
private fun DnsHeroHeader(onHelpClick: () -> Unit) {
    var iconReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { iconReady = true }

    val iconScale by animateFloatAsState(
        targetValue = if (iconReady) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dns-icon-scale"
    )

    ToolHeroHeader(
        title = stringResource(R.string.dns_screen_title),
        subtitle = stringResource(R.string.dns_screen_subtitle),
        icon = Icons.Default.Language,
        onHelpClick = onHelpClick,
        iconContent = {
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    )
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun DnsInputCard(
    domain: String,
    recordType: DnsRecordType,
    selectedServer: DnsServer,
    customServerAddress: String,
    isLoading: Boolean,
    isCanceling: Boolean,
    recentHosts: List<String>,
    onDomainChange: (String) -> Unit,
    onRecordTypeChange: (DnsRecordType) -> Unit,
    onServerChange: (DnsServer) -> Unit,
    onCustomServerAddressChange: (String) -> Unit,
    onLookup: () -> Unit,
    onCancelLookup: () -> Unit,
    onRemoveRecentHost: (String) -> Unit,
    onClearRecentHosts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissKeyboard = rememberDismissKeyboard()

    val isBusy = isLoading || isCanceling
    val isCustomServerValid = selectedServer !is DnsServer.Custom || customServerAddress.trim().let {
        HostValidator.isValidIpv4(it) || HostValidator.isValidIpv6(it)
    }
    val domainProblem = domainProblemOf(domain, recordType)
    val domainProblemText = domainProblem?.let { stringResource(dnsNameProblemRes(it, domain)) }
    val domainSupporting: (@Composable () -> Unit)? = domainProblemText?.let { message -> { Text(message) } }
    val canLookup = domain.isNotBlank() && domainProblem == null && isCustomServerValid
    val lookupInView = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) { if (imeVisible) lookupInView.bringIntoView() }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Domain input
            ToolTextField(
                value = domain,
                onValueChange = onDomainChange,
                label = { Text(stringResource(R.string.dns_domain_label)) },
                placeholder = { Text(stringResource(R.string.dns_domain_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        dismissKeyboard()
                        if (!isBusy && canLookup) onLookup()
                    }
                ),
                enabled = !isBusy,
                isError = domainProblem != null,
                supportingText = domainSupporting,
                modifier = Modifier.fillMaxWidth().testTag(DnsScreenTestTags.DOMAIN_INPUT),
                shape = AppShapes.medium,
                showClear = domain.isNotEmpty() && !isBusy,
            )

            RecentHostsRow(
                recentHosts = recentHosts,
                onHostSelected = onDomainChange,
                onRemoveHost = onRemoveRecentHost,
                onClearAll = onClearRecentHosts,
                selectionEnabled = !isBusy
            )

            // Record type selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dns_record_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordTypeChips(
                    selected = recordType,
                    onSelect = onRecordTypeChange,
                    enabled = !isBusy
                )
                // Description of selected record type
                RecordTypeDescription(recordType = recordType)
            }

            // DNS server selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DnsServerSelector(
                    selectedServer = selectedServer,
                    customServerAddress = customServerAddress,
                    onServerChange = onServerChange,
                    onCustomAddressChange = onCustomServerAddressChange,
                    enabled = !isBusy
                )
            }

            // Lookup button. The keyboard can open over it while the domain field has focus, so
            // it is scrolled into view whenever the IME appears.
            Button(
                onClick = hapticAction(if (isLoading) onCancelLookup else onLookup),
                enabled = isLoading || (!isCanceling && canLookup),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .bringIntoViewRequester(lookupInView)
                    .testTag(DnsScreenTestTags.CANCEL_LOOKUP),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    Text(stringResource(R.string.cancel))
                } else if (isCanceling) {
                    Text(stringResource(R.string.dns_canceling))
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dns_lookup_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/** First char code outside 7-bit ASCII; IDN input containing these needs punycode. */
private const val NON_ASCII_CHAR_CODE = 128

/** Pre-submit check for the domain field; null when the lookup may proceed. */
private fun domainProblemOf(
    domain: String,
    recordType: DnsRecordType,
): HostProblem? = if (domain.isBlank()) null else DnsNameValidator.problemOf(domain, recordType)

/** DNS wording for a rejected query name; IDNA failures get their own explanation. */
@StringRes
private fun dnsNameProblemRes(
    problem: HostProblem,
    input: String,
): Int =
    when (problem) {
        HostProblem.CONTAINS_WHITESPACE -> R.string.dns_name_whitespace
        HostProblem.INVALID -> dnsInvalidNameRes(input)
        else -> problem.messageRes()
    }

@StringRes
private fun dnsInvalidNameRes(input: String): Int =
    when {
        HostValidator.isValidIpv6(input.trim()) -> R.string.dns_name_ipv6_needs_ptr
        input.any { it.code >= NON_ASCII_CHAR_CODE } -> R.string.dns_name_idn
        else -> R.string.dns_name_invalid
    }

// ── Record type chips ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTypeChips(
    selected: DnsRecordType,
    onSelect: (DnsRecordType) -> Unit,
    enabled: Boolean
) {
    val scrollState = rememberScrollState()

    // The chips scroll in their own viewport, which fades the cut-off edge. The "more" chevron sits
    // beside the viewport, never over a chip.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .clipToBounds()
                    .scrollEdgeFade(
                        canScrollBackward = scrollState.canScrollBackward,
                        canScrollForward = scrollState.canScrollForward,
                    ),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(end = ScrollEdgeFadeWidth)
                    .testTag(DnsScreenTestTags.RECORD_TYPE_CHIPS),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnsRecordType.entries.forEach { type ->
                    val isSelected = type == selected
                    FilterChip(
                        selected = isSelected,
                        enabled = enabled,
                        onClick = { onSelect(type) },
                        label = {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (scrollState.canScrollForward) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.dns_record_types_scroll_hint),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun RecordTypeDescription(recordType: DnsRecordType) {
    Crossfade(
        targetState = recordType,
        animationSpec = AppMotion.effect(200),
        label = "record-type-desc"
    ) { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp).padding(top = 1.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ── DNS server selector (combo box) ──────────────────────────────────────────

/**
 * An editable combo box for DNS server selection.
 * - Preset servers (System, Google, Cloudflare, OpenDNS, Quad9) are offered in the dropdown.
 * - The user can also type a custom IP address directly into the field; this switches the
 *   server to [DnsServer.Custom] automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsServerSelector(
    selectedServer: DnsServer,
    customServerAddress: String,
    onServerChange: (DnsServer) -> Unit,
    onCustomAddressChange: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val dismissKeyboard = rememberDismissKeyboard()

    // Text shown in the field: preset display name, or the typed custom address.
    val fieldValue = if (selectedServer is DnsServer.Custom) customServerAddress
                     else selectedServer.uiLabel().asString()

    // Custom server addresses must be literal IPs (IPv4 or IPv6).
    val trimmedCustomAddress = customServerAddress.trim()
    val isCustomInvalid = selectedServer is DnsServer.Custom &&
        !HostValidator.isValidIpv4(trimmedCustomAddress) &&
        !HostValidator.isValidIpv6(trimmedCustomAddress)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { text ->
                // Any manual edit → treat as a custom DNS address.
                onCustomAddressChange(text)
            },
            label = { Text(stringResource(R.string.dns_server_label)) },
            placeholder = { Text(stringResource(R.string.dns_custom_server_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            isError = isCustomInvalid,
            enabled = enabled,
            supportingText = if (isCustomInvalid) {
                { Text(stringResource(R.string.dns_custom_server_invalid)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
                .scrollIntoViewOnFocus(),
            shape = AppShapes.medium,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DnsServer.presets.forEach { server ->
                val isSelected = when {
                    server is DnsServer.System && selectedServer is DnsServer.System -> true
                    else -> selectedServer == server
                }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = server.uiLabel().asString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = server.uiDescription().asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    enabled = enabled,
                    onClick = {
                        onServerChange(server)
                        expanded = false
                        dismissKeyboard()
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            // "Custom…" sentinel at the bottom of the list
            val isCustomSelected = selectedServer is DnsServer.Custom
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.dns_server_custom),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCustomSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCustomSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                },
                enabled = enabled,
                onClick = {
                    onCustomAddressChange("")
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
        }
    }
}

// ── Idle hint ─────────────────────────────────────────────────────────────────

@Composable
private fun DnsIdleHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.dns_idle_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.dns_idle_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Loading panel ─────────────────────────────────────────────────────────────

@Composable
private fun DnsLoadingPanel(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val loadingCd = stringResource(R.string.a11y_loading)
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp).semantics { contentDescription = loadingCd },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                text = stringResource(R.string.dns_querying),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.dns_querying_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DnsCancelingPanel(modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.dns_canceling), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DnsCanceledPanel(onClear: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.dns_canceled), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.dns_canceled_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
        }
    }
}

// ── Error panel ───────────────────────────────────────────────────────────────

@Composable
private fun DnsErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    canFallbackToCloudflare: Boolean = false,
    onUseCloudflare: () -> Unit = {},
) {
    ToolErrorCard(
        modifier = modifier,
        title = stringResource(R.string.dns_error_title),
        message = message,
    ) {
            ToolRetryButton(text = stringResource(R.string.dns_retry), onClick = onRetry)
            if (canFallbackToCloudflare) {
                TextButton(
                    onClick = onUseCloudflare,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dns_use_cloudflare))
                }
            }
    }
}

// ── Result panel ──────────────────────────────────────────────────────────────

@Composable
private fun DnsResultPanel(
    result: DnsResult,
    showRaw: Boolean,
    onToggleRaw: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        DnsResultSummaryCard(result = result, onClear = onClear)

        if (result.authority.isNotEmpty()) {
            DnsSectionCard(
                title = stringResource(R.string.dns_authority_section),
                records = result.authority
            )
        }
        if (result.additional.isNotEmpty()) {
            DnsSectionCard(
                title = stringResource(R.string.dns_additional_section),
                records = result.additional,
                initiallyExpanded = false,
            )
        }

        // Records / empty state
        if (result.records.isEmpty()) {
            DnsNoRecordsCard(result = result)
        } else {
            result.records.forEachIndexed { index, record ->
                DnsRecordCard(record = record, index = index)
            }
        }

        // Raw response toggle
        DnsRawToggleCard(
            rawResponse = result.rawResponse,
            showRaw = showRaw,
            onToggle = onToggleRaw
        )
    }
}

@Composable
private fun DnsResultSummaryCard(
    result: DnsResult,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.share_subject_dns, result.recordType.name, result.domain)
    val shareServerLine = stringResource(R.string.dns_share_server, result.server.uiLabel().asString())
    val tone = dnsResponseTone(result.rcode)
    val onContainer =
        when (tone) {
            DnsResponseTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            DnsResponseTone.NOT_FOUND -> MaterialTheme.colorScheme.onSurface
            DnsResponseTone.FAILURE -> MaterialTheme.colorScheme.onErrorContainer
        }
    // A found name keeps the success gradient. NXDOMAIN is neutral: it is a definitive answer,
    // not a success, and must not read as one.
    val colors = MaterialTheme.colorScheme
    val successBrush = Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer))
    val neutralBrush = Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface))
    val failureBrush = Brush.linearGradient(listOf(colors.errorContainer, colors.errorContainer))
    val containerBrush = when (tone) {
        DnsResponseTone.SUCCESS -> successBrush
        DnsResponseTone.NOT_FOUND -> neutralBrush
        DnsResponseTone.FAILURE -> failureBrush
    }
    ElevatedCard(
        shape = AppShapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(containerBrush)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.domain,
                        style = MaterialTheme.typography.titleLarge,
                        color = onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.dns_rcode, result.rcode),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (tone) {
                                DnsResponseTone.SUCCESS -> onContainer
                                DnsResponseTone.NOT_FOUND -> MaterialTheme.colorScheme.onSurfaceVariant
                                DnsResponseTone.FAILURE -> onContainer
                            }
                    )
                    Text(
                        text = stringResource(R.string.dns_flags, result.flags.joinToString(" ").ifEmpty { "N/A" }),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.dns_server_used, result.serverUsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val systemServer = result.server as? DnsServer.System
                    if (systemServer?.privateDnsActive == true) {
                        Text(
                            text = stringResource(
                                R.string.dns_private_notice,
                                systemServer.privateDnsHost?.let { " ($it)" } ?: ""
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = onContainer
                    )
                }
            }

            HorizontalDivider(color = onContainer.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryMetric(
                    icon = Icons.Default.Dns,
                    value = result.records.size.toString(),
                    label = if (result.records.size == 1) stringResource(R.string.dns_record_singular)
                            else stringResource(R.string.dns_record_plural)
                )
                SummaryMetric(
                    icon = Icons.Default.Speed,
                    value = "${result.queryTimeMs}",
                    label = stringResource(R.string.dns_ms)
                )
            }
            // Copy and Share both send buildDnsShareText, so the clipboard matches the share text.
            ResultActionsRow(
                copyText = buildDnsShareText(result, shareServerLine),
                onShare = {
                    context.shareText(
                        text = buildDnsShareText(result, shareServerLine),
                        subject = shareSubject
                    )
                },
            )
        }
    }
}

@Composable
private fun DnsSectionCard(
    title: String,
    records: List<DnsRecord>,
    initiallyExpanded: Boolean = true,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.action_collapse)
                    else stringResource(R.string.action_expand)
                )
            }
            if (expanded) {
                records.forEachIndexed { index, record ->
                    DnsRecordCard(record = record, index = index)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Individual record card ────────────────────────────────────────────────────

@Composable
private fun DnsRecordCard(record: DnsRecord, index: Int) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.enter(200 + index * 40),
        label = "record-alpha-$index"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = AppShapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The type badge is the one place the record type is shown.
                    RecordTypeIcon(type = record.type, label = dnsRecordIconLabel(record))
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TtlBadge(ttl = record.ttl)
                    IconButton(
                        onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", record.value))) }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.dns_copy_value),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Record value – formatted per type
            RecordValueDisplay(record = record)
        }
    }
}

@Composable
private fun RecordTypeIcon(
    type: DnsRecordType?,
    label: String,
) {
    val (bgColor, iconTint) = when (type) {
        null -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        DnsRecordType.A     -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DnsRecordType.AAAA  -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        DnsRecordType.MX    -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DnsRecordType.TXT   -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        DnsRecordType.CNAME -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DnsRecordType.NS    -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DnsRecordType.SOA   -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DnsRecordType.PTR   -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        DnsRecordType.SRV   -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DnsRecordType.CAA   -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = iconTint
        )
    }
}

@Composable
private fun TtlBadge(ttl: Long) {
    val ttlText = when {
        ttl < 60    -> "${ttl}s"
        ttl < 3600  -> "${ttl / 60}m"
        else        -> "${ttl / 3600}h"
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "TTL $ttlText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RecordValueDisplay(record: DnsRecord) {
    when (record.type) {
        null -> PlainValueDisplay(value = record.value)
        DnsRecordType.A, DnsRecordType.AAAA -> {
            // Highlight IP addresses with monospace font and colored background
            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        DnsRecordType.MX -> {
            // Parse "priority hostname" format
            val parts = record.value.split(" ", limit = 2)
            val priority = parts.getOrNull(0) ?: ""
            val host = parts.getOrNull(1) ?: record.value
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledValue(label = stringResource(R.string.dns_mx_priority), value = priority)
                LabeledValue(label = stringResource(R.string.dns_mx_host), value = host)
                // RFC 7505 null MX: a single root host means the domain refuses mail.
                if (host == ".") {
                    Text(
                        text = stringResource(R.string.dns_null_mx_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DnsRecordType.SOA -> {
            // SOA has multiple space-separated fields: mname rname serial refresh retry expire minimum
            val fields = record.value.split(" ")
            val labels = listOf("Primary NS", "Responsible", "Serial", "Refresh", "Retry", "Expire", "Min TTL")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fields.forEachIndexed { i, value ->
                    if (i < labels.size) {
                        LabeledValue(label = labels[i], value = value)
                    }
                }
            }
        }

        DnsRecordType.SRV -> {
            val parts = record.value.split(" ")
            if (parts.size >= 4) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledValue(label = stringResource(R.string.dns_srv_priority), value = parts[0])
                    LabeledValue(label = stringResource(R.string.dns_srv_weight), value = parts[1])
                    LabeledValue(label = stringResource(R.string.dns_srv_port), value = parts[2])
                    LabeledValue(label = stringResource(R.string.dns_srv_target), value = parts[3])
                }
            } else {
                PlainValueDisplay(value = record.value)
            }
        }

        DnsRecordType.TXT -> {
            // TXT records can be long – use scrollable monospace box
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = record.value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> PlainValueDisplay(value = record.value)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlainValueDisplay(value: String) {
    SelectionContainer {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(10.dp)
        )
    }
}

// ── No records state ──────────────────────────────────────────────────────────

@Composable
private fun DnsNoRecordsCard(result: DnsResult) {
    val (title, subtitle) = when (result.rcode) {
        "NOERROR" -> R.string.dns_no_records_title to
            stringResource(R.string.dns_no_records_subtitle, result.recordType.displayName, result.domain)
        "NXDOMAIN" -> R.string.dns_nxdomain_title to
            stringResource(R.string.dns_nxdomain_subtitle, result.domain)
        else -> R.string.dns_rcode_error_title to
            stringResource(R.string.dns_rcode_error_subtitle, result.rcode, result.domain)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Raw response toggle ───────────────────────────────────────────────────────

@Composable
private fun DnsRawToggleCard(
    rawResponse: String,
    showRaw: Boolean,
    onToggle: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.dns_raw_response),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = showRaw) {
                Column {
                    HorizontalDivider()
                    val clipboard2 = LocalClipboard.current
                    val scope2 = rememberCoroutineScope()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(
                                text = rawResponse,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { scope2.launch { clipboard2.setClipEntry(ClipEntry(ClipData.newPlainText("", rawResponse))) } },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.dns_copy_raw),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun buildDnsShareText(result: DnsResult, serverLine: String): String = buildString {
    appendLine("DNS ${result.recordType.name} – ${result.domain}")
    appendLine(serverLine)
    appendLine("Query time: ${result.queryTimeMs}ms")
    appendLine()
    if (result.records.isEmpty()) {
        appendLine("No records found.")
    } else {
        result.records.forEach { record ->
            appendLine("${record.rrTypeName}\t${record.value}\tTTL: ${record.ttl}s")
        }
    }
}
