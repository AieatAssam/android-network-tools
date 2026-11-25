package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.ui.components.ToolErrorCard
import net.aieat.netswissknife.app.ui.components.ToolRetryButton
import net.aieat.netswissknife.app.ui.components.hapticAction
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.netinfo.PublicAddressInfo
import net.aieat.netswissknife.core.network.netinfo.PublicIpReport
import net.aieat.netswissknife.core.network.netinfo.PublicNetworkDetails

/** The public address is looked up only from [onCheck]. Nothing in this card starts a request by itself. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoPublicIpCard(
    state: PublicIpUiState,
    onCheck: () -> Unit,
    onCopy: (String) -> Unit,
) {
    NetInfoSectionCard(title = stringResource(R.string.netinfo_section_public)) {
        when (state) {
            PublicIpUiState.Idle -> PublicIpIdle(onCheck)
            PublicIpUiState.Loading -> PublicIpLoading()
            is PublicIpUiState.Loaded -> PublicIpResult(state.report, onCheck, onCopy)
        }
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun PublicIpIdle(onCheck: () -> Unit) {
    val check = hapticAction(onCheck)
    Text(
        text = stringResource(R.string.netinfo_public_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = check,
        modifier = Modifier.fillMaxWidth().testTag(NetInfoPublicIpTestTags.CHECK),
    ) {
        Icon(imageVector = Icons.Default.Language, contentDescription = null)
        Text(text = stringResource(R.string.netinfo_public_check))
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun PublicIpLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(AppSpacing.l))
        Text(
            text = stringResource(R.string.netinfo_public_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    NetInfoShimmerLines(lines = 2)
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun PublicIpResult(
    report: PublicIpReport,
    onCheck: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val bothFailed = report.ipv4 is NetworkResult.Error && report.ipv6 is NetworkResult.Error
    if (bothFailed) {
        ToolErrorCard(
            title = stringResource(R.string.netinfo_public_error_title),
            message = stringResource(R.string.netinfo_public_error_body),
            modifier = Modifier.testTag(NetInfoPublicIpTestTags.ERROR),
        ) {
            ToolRetryButton(text = stringResource(R.string.netinfo_retry), onClick = onCheck)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(NetInfoPublicIpTestTags.RESULT),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s),
    ) {
        val shared = PublicNetworkDetails.shared(report)
        PublicFamilyRows(
            label = stringResource(R.string.netinfo_public_ipv4),
            unavailable = stringResource(R.string.netinfo_public_ipv4_unavailable),
            result = report.ipv4,
            showDetails = shared == null,
            onCopy = onCopy,
        )
        PublicFamilyRows(
            label = stringResource(R.string.netinfo_public_ipv6),
            unavailable = stringResource(R.string.netinfo_public_ipv6_unavailable),
            result = report.ipv6,
            showDetails = shared == null,
            onCopy = onCopy,
        )
        shared?.let { PublicDetailRows(it) }
    }
    OutlinedButton(
        onClick = hapticAction(onCheck),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.netinfo_public_check_again))
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun PublicFamilyRows(
    label: String,
    unavailable: String,
    result: NetworkResult<PublicAddressInfo>,
    showDetails: Boolean,
    onCopy: (String) -> Unit,
) {
    when (result) {
        is NetworkResult.Success -> {
            val info = result.data
            NetInfoValueRow(label = label, value = info.address, onCopy = onCopy)
            NetInfoFactRow(
                label = stringResource(R.string.netinfo_hostname),
                value = info.hostname ?: stringResource(R.string.netinfo_no_reverse_dns),
            )
            if (showDetails) PublicDetailRows(PublicNetworkDetails.of(info))
        }

        is NetworkResult.Error -> {
            NetInfoFactRow(label = label, value = unavailable)
        }
    }
}

@Composable
private fun PublicDetailRows(details: PublicNetworkDetails) {
    details.location?.let {
        NetInfoFactRow(label = stringResource(R.string.netinfo_location), value = it)
    }
    details.asn?.let {
        NetInfoFactRow(label = stringResource(R.string.netinfo_asn), value = it)
    }
}

/** Refresh re-reads the network; Copy and Share work on the whole summary through the shared row. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoActionsCard(
    onRefresh: () -> Unit,
    summaryText: String,
    onShare: () -> Unit,
) {
    val refresh = hapticAction(onRefresh)
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
        OutlinedButton(
            onClick = refresh,
            modifier = Modifier.fillMaxWidth().testTag(NetInfoScreenTestTags.REFRESH),
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Text(text = stringResource(R.string.netinfo_refresh))
        }
        ResultActionsRow(
            copyText = summaryText,
            onShare = onShare,
            copyTestTag = NetInfoScreenTestTags.COPY_ALL,
            shareTestTag = NetInfoScreenTestTags.SHARE,
        )
        Text(
            text = stringResource(R.string.netinfo_copy_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
