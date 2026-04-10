package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.SecurityHeaderCheck
import net.aieat.netswissknife.core.network.httprobe.SecurityRating

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun HttpProbeScreen(viewModel: HttpProbeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "httprobe_entrance_alpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 16.dp
            )
        ) {
            item { HttpProbeHeaderCard() }

            item {
                HttpProbeInputCard(
                    uiState = uiState,
                    onUrlChange = viewModel::onUrlChange,
                    onMethodChange = viewModel::onMethodChange,
                    onBodyChange = viewModel::onBodyChange,
                    onFollowRedirectsToggle = viewModel::onFollowRedirectsToggle,
                    onToggleHeaders = viewModel::onToggleHeadersExpanded,
                    onAddHeader = viewModel::addHeader,
                    onRemoveHeader = viewModel::removeHeader,
                    onHeaderKeyChange = viewModel::updateHeaderKey,
                    onHeaderValueChange = viewModel::updateHeaderValue,
                    onSend = viewModel::send
                )
            }

            item {
                val displayState: DisplayState = when {
                    uiState.isLoading     -> DisplayState.Loading
                    uiState.error != null -> DisplayState.Error(uiState.error!!)
                    uiState.result != null -> DisplayState.Success(uiState.result!!)
                    else                  -> DisplayState.Idle
                }

                AnimatedContent(
                    targetState = displayState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "httprobe_content_state"
                ) { state ->
                    when (state) {
                        is DisplayState.Idle    -> HttpProbeIdlePlaceholder()
                        is DisplayState.Loading -> HttpProbeLoadingContent()
                        is DisplayState.Error   -> HttpProbeErrorContent(state.message) { viewModel.send() }
                        is DisplayState.Success -> HttpProbeSuccessContent(
                            result = state.result,
                            selectedTab = uiState.selectedTab,
                            onTabSelected = viewModel::onTabSelected
                        )
                    }
                }
            }
        }
    }
}

// ── Display state ─────────────────────────────────────────────────────────────

private sealed class DisplayState {
    object Idle : DisplayState()
    object Loading : DisplayState()
    data class Error(val message: String) : DisplayState()
    data class Success(val result: HttpProbeResult) : DisplayState()
}

// ── Header card ───────────────────────────────────────────────────────────────

@Composable
private fun HttpProbeHeaderCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF1565C0),
                            Color(0xFF0288D1)
                        )
                    )
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Http,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.httprobe_screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.httprobe_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HttpProbeInputCard(
    uiState: HttpProbeUiState,
    onUrlChange: (String) -> Unit,
    onMethodChange: (HttpMethod) -> Unit,
    onBodyChange: (String) -> Unit,
    onFollowRedirectsToggle: () -> Unit,
    onToggleHeaders: () -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onHeaderKeyChange: (Int, String) -> Unit,
    onHeaderValueChange: (Int, String) -> Unit,
    onSend: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // URL field
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.httprobe_url_label)) },
                placeholder = { Text(stringResource(R.string.httprobe_url_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Http, contentDescription = null) },
                trailingIcon = {
                    if (uiState.url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
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
                IconButton(onClick = onToggleHeaders, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (uiState.headersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onAddHeader, modifier = Modifier.size(32.dp)) {
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
                                label = { Text(stringResource(R.string.httprobe_header_key), style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = header.value,
                                onValueChange = { onHeaderValueChange(index, it) },
                                label = { Text(stringResource(R.string.httprobe_header_value), style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = { onRemoveHeader(index) },
                                modifier = Modifier.size(32.dp)
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
                    onCheckedChange = { onFollowRedirectsToggle() }
                )
            }

            // Send button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSend()
                },
                enabled = uiState.url.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
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
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.httprobe_send_button))
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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
                    text = stringResource(R.string.httprobe_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.httprobe_retry))
            }
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun HttpProbeSuccessContent(
    result: HttpProbeResult,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            ScrollableTabRow(
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
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "httprobe_tab_content"
            ) { tab ->
                when (tab) {
                    0 -> OverviewTabContent(result)
                    1 -> HeadersTabContent(result)
                    2 -> BodyTabContent(result)
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
                        text = result.finalUrl,
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
                        text = stringResource(R.string.httprobe_redirects_label, result.redirectChain.size),
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
        LabeledValue(stringResource(R.string.httprobe_final_url), result.finalUrl)
        LabeledValue(stringResource(R.string.httprobe_response_size), formatBytes(result.responseBodyBytes))
        LabeledValue(
            stringResource(R.string.httprobe_content_type),
            result.responseHeaders["Content-Type"]?.firstOrNull() ?: "—"
        )
        if (result.redirectChain.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.httprobe_redirect_chain),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result.redirectChain.forEachIndexed { index, url ->
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
                    Text(
                        text = url,
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
            val displayHeaders = result.responseHeaders.filter { it.key != null }
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
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
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
private fun BodyTabContent(result: HttpProbeResult) {
    val clipboardManager = LocalClipboardManager.current

    val responseBody = result.responseBody

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
                    onClick = { clipboardManager.setText(AnnotatedString(responseBody)) }
                ) {
                    Text(stringResource(R.string.httprobe_copy_body), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (result.responseBodyBytes > 512_000L) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = responseBody,
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
            SecuritySummaryChip(count = passCount, label = "Pass", color = Color(0xFF2E7D32))
            SecuritySummaryChip(count = warnCount, label = "Warn", color = Color(0xFFF57F17))
            SecuritySummaryChip(count = failCount, label = "Fail", color = MaterialTheme.colorScheme.error)
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

                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
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
                        text = check.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityRatingIcon(rating: SecurityRating, modifier: Modifier = Modifier) {
    when (rating) {
        SecurityRating.PASS -> Icon(Icons.Default.Check, null, modifier = modifier, tint = Color(0xFF2E7D32))
        SecurityRating.WARN -> Icon(Icons.Default.Warning, null, modifier = modifier, tint = Color(0xFFF57F17))
        SecurityRating.FAIL -> Icon(Icons.Default.Clear, null, modifier = modifier, tint = MaterialTheme.colorScheme.error)
        SecurityRating.INFO -> Icon(Icons.Default.Info, null, modifier = modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityRatingBadge(rating: SecurityRating) {
    val (bg, fg, label) = when (rating) {
        SecurityRating.PASS -> Triple(Color(0xFF2E7D32).copy(alpha = 0.12f), Color(0xFF2E7D32), "PASS")
        SecurityRating.WARN -> Triple(Color(0xFFF57F17).copy(alpha = 0.12f), Color(0xFFF57F17), "WARN")
        SecurityRating.FAIL -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "FAIL")
        SecurityRating.INFO -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "INFO")
    }
    Surface(shape = MaterialTheme.shapes.small, color = bg) {
        Text(
            text = label,
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
    in 200..299 -> Color(0xFF2E7D32)
    in 300..399 -> MaterialTheme.colorScheme.primary
    in 400..499 -> Color(0xFFF57F17)
    in 500..599 -> MaterialTheme.colorScheme.error
    else        -> MaterialTheme.colorScheme.onSurface
}

private fun statusGradient(code: Int): List<Color> = when (code) {
    in 200..299 -> listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))
    in 300..399 -> listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
    in 400..499 -> listOf(Color(0xFFE65100), Color(0xFFFFB74D))
    in 500..599 -> listOf(Color(0xFFB71C1C), Color(0xFFEF9A9A))
    else        -> listOf(Color(0xFF616161), Color(0xFFBDBDBD))
}

@Composable
private fun timingColor(ms: Long): Color = when {
    ms < 200  -> Color(0xFF2E7D32)
    ms < 800  -> Color(0xFFF57F17)
    else      -> MaterialTheme.colorScheme.error
}

@Composable
private fun methodColor(method: HttpMethod): Color = when (method) {
    HttpMethod.GET     -> Color(0xFF1565C0)
    HttpMethod.POST    -> Color(0xFF2E7D32)
    HttpMethod.PUT     -> Color(0xFFF57F17)
    HttpMethod.PATCH   -> Color(0xFF6A1B9A)
    HttpMethod.DELETE  -> MaterialTheme.colorScheme.error
    HttpMethod.HEAD    -> Color(0xFF00695C)
    HttpMethod.OPTIONS -> Color(0xFF4E342E)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else                -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
