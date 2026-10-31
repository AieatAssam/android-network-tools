package net.aieat.netswissknife.app.ui.screens.netinfo

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.HelpSection
import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.components.ToolHelpSheet
import net.aieat.netswissknife.app.ui.components.ToolHeroHeader
import net.aieat.netswissknife.app.ui.components.ToolStateAnnouncer
import net.aieat.netswissknife.app.ui.theme.AppMotion
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.NetInfoSummaryFormatter

object NetInfoPublicIpTestTags {
    const val CHECK = "netinfo_check_public_ip"
    const val ERROR = "netinfo_public_ip_error"
    const val RESULT = "netinfo_public_ip_result"
}

object NetInfoScreenTestTags {
    const val NO_NETWORK = "netinfo_no_network"
    const val REFRESH = "netinfo_refresh"
    const val COPY_ALL = "netinfo_copy_all"
    const val SHARE = "netinfo_share"
}

private const val SLIDE_DIVISOR = 4

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun NetInfoScreen(viewModel: NetInfoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val report = (uiState.publicIp as? PublicIpUiState.Loaded)?.report
    val announcementPhase =
        when (uiState.publicIp) {
            PublicIpUiState.Idle -> null
            PublicIpUiState.Loading -> ToolAnnouncementPhase.RUNNING
            is PublicIpUiState.Loaded -> ToolAnnouncementPhase.FINISHED
        }
    ToolStateAnnouncer(stringResource(R.string.netinfo_screen_title), announcementPhase)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var showHelp by remember { mutableStateOf(false) }

    val labels = rememberNetInfoSummaryLabels()
    val summary = NetInfoSummaryFormatter.format(uiState.local, report, labels)
    val copy = rememberNetInfoCopy()
    val context = LocalContext.current
    val shareSubject = stringResource(R.string.netinfo_share_subject)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.enter()) + slideInVertically(AppMotion.enter()) { it / SLIDE_DIVISOR },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap),
        ) {
            NetInfoContent(
                uiState = uiState,
                onCheck = viewModel::checkPublicIp,
                onRefresh = viewModel::refresh,
                onCopy = copy,
                onCopyAll = { copy(summary) },
                onShare = { context.shareText(summary, shareSubject) },
                onHelp = { showHelp = true },
            )
        }
    }

    if (showHelp) {
        NetInfoHelpSheet(onDismiss = { showHelp = false })
    }
}

/** Hero, local network, public IP and actions. Kept apart so the screen function stays small. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NetInfoContent(
    uiState: NetInfoUiState,
    onCheck: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
    onHelp: () -> Unit,
) {
    ToolHeroHeader(
        title = stringResource(R.string.netinfo_screen_title),
        subtitle = stringResource(R.string.netinfo_screen_subtitle),
        icon = Icons.Default.SettingsEthernet,
        onHelpClick = onHelp,
    )

    AnimatedContent(
        targetState = uiState.local,
        label = "netinfo-local",
    ) { local ->
        LocalNetworkContent(local = local, onCopy = onCopy)
    }

    NetInfoPublicIpCard(
        state = uiState.publicIp,
        onCheck = onCheck,
        onCopy = onCopy,
    )

    NetInfoActionsCard(
        onRefresh = onRefresh,
        onCopyAll = onCopyAll,
        onShare = onShare,
    )
}

/** Help for Network Info, opened from the hero's help button. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun NetInfoHelpSheet(onDismiss: () -> Unit) {
    val helpSections =
        listOf(
            HelpSection(
                heading = stringResource(R.string.help_netinfo_public_heading),
                body = stringResource(R.string.help_netinfo_public_body),
            ),
            HelpSection(
                heading = stringResource(R.string.help_netinfo_privacy_heading),
                body = stringResource(R.string.help_netinfo_privacy_body),
            ),
            HelpSection(
                heading = stringResource(R.string.help_netinfo_scope_heading),
                body = stringResource(R.string.help_netinfo_scope_body),
            ),
        )
    ToolHelpSheet(
        title = stringResource(R.string.netinfo_screen_title),
        conceptHeading = stringResource(R.string.help_netinfo_concept_heading),
        conceptBody = stringResource(R.string.help_netinfo_concept_body),
        sections = helpSections,
        onDismiss = onDismiss,
    )
}

/** Loading, no-network and connected states, each in its own card layout. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun LocalNetworkContent(
    local: NetInfoSnapshot?,
    onCopy: (String) -> Unit,
) {
    when {
        local == null -> {
            NetInfoLoadingCard()
        }

        !local.hasActiveNetwork -> {
            NetInfoNoNetworkCard(modifier = Modifier.testTag(NetInfoScreenTestTags.NO_NETWORK))
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap)) {
                val transports = local.transports.map { stringResource(transportLabelRes(it)) }
                NetInfoNetworkCard(
                    transports = transports.joinToString(),
                    metered = yesNo(local.metered),
                    roaming = yesNo(local.roaming),
                )
                local.wifi?.let { NetInfoWifiCard(wifi = it, onCopy = onCopy) }
                NetInfoIpCard(local = local, onCopy = onCopy)
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.netinfo_yes else R.string.netinfo_no)

/** Copies text to the clipboard with a confirmation toast, as the other tools do. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun rememberNetInfoCopy(): (String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.netinfo_copied)
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
}
