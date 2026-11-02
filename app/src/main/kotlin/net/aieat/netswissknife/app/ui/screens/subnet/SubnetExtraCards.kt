@file:Suppress("FunctionNaming", "ktlint:standard:function-naming") // Compose functions are PascalCase by convention.

package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.ResultActionsRow
import net.aieat.netswissknife.app.util.shareText
import net.aieat.netswissknife.core.network.subnet.SubnetInfo

// ── Copy and share ────────────────────────────────────────────────────────────

@Composable
internal fun SubnetShareActions(info: SubnetInfo) {
    val context = LocalContext.current
    val text = buildSubnetShareText(info, shareLabels(), stringResource(info.scope.labelRes()))
    val subject = stringResource(R.string.subnet_share_subject, info.cidrNotation)
    ResultActionsRow(
        copyText = text,
        onShare = { context.shareText(text, subject) },
    )
}

@Composable
private fun shareLabels(): SubnetShareLabels =
    SubnetShareLabels(
        cidr = stringResource(R.string.subnet_notation_cidr),
        networkAddress = stringResource(R.string.subnet_network_address),
        broadcast = stringResource(R.string.subnet_broadcast),
        firstHost = stringResource(R.string.subnet_first_host),
        lastHost = stringResource(R.string.subnet_last_host),
        totalAddresses = stringResource(R.string.subnet_total_hosts),
        usableHosts = stringResource(R.string.subnet_usable_hosts),
        subnetMask = stringResource(R.string.subnet_notation_mask),
        wildcardMask = stringResource(R.string.subnet_notation_wildcard),
        scope = stringResource(R.string.subnet_scope),
    )
