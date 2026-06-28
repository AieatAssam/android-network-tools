package net.aieat.netswissknife.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R

private data class ToolSection(val labelRes: Int, val routes: List<String>)

private val TOOL_SECTIONS = listOf(
    ToolSection(R.string.more_section_diagnostics, listOf("ping", "traceroute", "ports", "dns")),
    ToolSection(R.string.more_section_wifi_lan,    listOf("wifi_scan", "lan", "topology", "mdns")),
    ToolSection(R.string.more_section_security,    listOf("tls", "whois", "httprobe")),
    ToolSection(R.string.more_section_utilities,   listOf("subnet", "speedtest")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsSheet(
    pinnedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    maxPinned: Int,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onDebugLogsClick: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pinLimitMessage = stringResource(R.string.more_pin_limit_reached, maxPinned)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(snackbarData = data)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(scaffoldPadding)
                    .padding(horizontal = 20.dp),
            ) {
                // ── Header (non-scrolling) ────────────────────────────────────
                Text(
                    text = stringResource(R.string.more_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.more_sheet_subtitle, maxPinned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // ── Scrollable tools area ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    TOOL_SECTIONS.forEach { section ->
                        val sectionTools = NavRoutes.allTools.filter { it.route in section.routes }
                        if (sectionTools.isEmpty()) return@forEach

                        Text(
                            text = stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )

                        sectionTools.forEachIndexed { index, tool ->
                            val isPinned = pinnedRoutes.contains(tool.route)
                            val canPin = isPinned || pinnedRoutes.size < maxPinned

                            ToolSheetRow(
                                tool = tool,
                                isPinned = isPinned,
                                onNavigate = { onNavigate(tool.route) },
                                onTogglePin = {
                                    if (canPin) {
                                        onTogglePin(tool.route)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(pinLimitMessage)
                                        }
                                    }
                                },
                            )

                            if (index < sectionTools.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // ── Sticky footer: Settings + Debug ───────────────────────────
                HorizontalDivider()

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSettingsClick)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_entry_label),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_entry_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (net.aieat.netswissknife.app.BuildConfig.DEBUG) {
                    HorizontalDivider()

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDebugLogsClick)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.debug_log_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.debug_log_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToolSheetRow(
    tool: ToolInfo,
    isPinned: Boolean,
    onNavigate: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val pinTint by animateColorAsState(
        targetValue = if (isPinned)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "pin-tint-${tool.route}",
    )
    val iconBg by animateColorAsState(
        targetValue = if (isPinned)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(200),
        label = "icon-bg-${tool.route}",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isPinned)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(200),
        label = "icon-tint-${tool.route}",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = tool.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned)
                    stringResource(R.string.more_unpin_description, tool.label)
                else
                    stringResource(R.string.more_pin_description, tool.label),
                tint = pinTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
