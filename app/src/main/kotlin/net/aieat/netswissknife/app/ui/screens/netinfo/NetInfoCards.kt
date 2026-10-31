package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.AppShapes
import net.aieat.netswissknife.app.ui.theme.AppSpacing
import net.aieat.netswissknife.core.network.netinfo.MacAddressState
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.SsidState
import net.aieat.netswissknife.core.network.netinfo.SsidUnavailableReason
import net.aieat.netswissknife.core.network.netinfo.WifiLinkDetails

/** Shown before the first read, so the layout keeps its height while the network is read. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoLoadingCard() {
    NetInfoSectionCard(title = stringResource(R.string.netinfo_loading)) {
        NetInfoShimmerLines()
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoNoNetworkCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s),
        ) {
            Text(
                text = stringResource(R.string.netinfo_no_network_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.netinfo_no_network_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Transport, metering and roaming for the active network. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoNetworkCard(
    transports: String,
    metered: String,
    roaming: String,
) {
    NetInfoSectionCard(title = stringResource(R.string.netinfo_section_network)) {
        NetInfoFactRow(label = stringResource(R.string.netinfo_transports), value = transports)
        NetInfoFactRow(label = stringResource(R.string.netinfo_metered), value = metered)
        NetInfoFactRow(label = stringResource(R.string.netinfo_roaming), value = roaming)
    }
}

/** Wi-Fi link details. The name is shown only when Android reveals it. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoWifiCard(
    wifi: WifiLinkDetails,
    onCopy: (String) -> Unit,
) {
    NetInfoSectionCard(title = stringResource(R.string.netinfo_section_wifi)) {
        val notAvailable = stringResource(R.string.netinfo_not_available)
        when (val ssid = wifi.ssid) {
            is SsidState.Known -> {
                NetInfoValueRow(
                    label = stringResource(R.string.netinfo_ssid),
                    value = ssid.ssid,
                    onCopy = onCopy,
                )
            }

            is SsidState.Unavailable -> {
                NetInfoFactRow(
                    label = stringResource(R.string.netinfo_ssid),
                    value = ssidUnavailableText(ssid.reason),
                )
                if (ssid.reason == SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED) {
                    Text(
                        text = stringResource(R.string.netinfo_ssid_permission_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val signal = wifi.rssiDbm?.let { stringResource(R.string.netinfo_value_dbm, it) } ?: notAvailable
        val speed = wifi.linkSpeedMbps?.let { stringResource(R.string.netinfo_value_mbps, it) } ?: notAvailable
        NetInfoFactRow(label = stringResource(R.string.netinfo_signal), value = signal)
        NetInfoFactRow(label = stringResource(R.string.netinfo_link_speed), value = speed)
        NetInfoFactRow(
            label = stringResource(R.string.netinfo_frequency),
            value = frequencyText(wifi),
        )
    }
}

/** Interface addresses with scope and prefix, then gateway, DNS, MTU, MAC, domains and proxy. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun NetInfoIpCard(
    local: NetInfoSnapshot,
    onCopy: (String) -> Unit,
) {
    NetInfoSectionCard(title = stringResource(R.string.netinfo_section_ip)) {
        NetInfoFactRow(
            label = stringResource(R.string.netinfo_interface),
            value = local.interfaceName ?: stringResource(R.string.netinfo_not_available),
        )
        local.addresses.forEach { address ->
            NetInfoValueRow(
                label = addressLabel(address),
                value = address.address,
                onCopy = onCopy,
                detail = addressDetail(address),
            )
        }
        local.gateways.forEach { gateway ->
            NetInfoValueRow(
                label = stringResource(R.string.netinfo_gateway),
                value = gateway,
                onCopy = onCopy,
            )
        }
        local.dnsServers.forEach { dns ->
            NetInfoValueRow(
                label = stringResource(R.string.netinfo_dns),
                value = dns,
                onCopy = onCopy,
            )
        }
        NetInfoFactRow(
            label = stringResource(R.string.netinfo_mtu),
            value = local.mtuBytes?.toString() ?: stringResource(R.string.netinfo_not_available),
        )
        local.mac?.let { MacRow(it, onCopy) }
        NetInfoFactRow(
            label = stringResource(R.string.netinfo_domains),
            value = local.searchDomains.joinToString().ifEmpty { stringResource(R.string.netinfo_none) },
        )
        NetInfoFactRow(
            label = stringResource(R.string.netinfo_proxy),
            value = proxyText(local),
        )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun MacRow(
    state: MacAddressState,
    onCopy: (String) -> Unit,
) {
    when (state) {
        is MacAddressState.Known -> {
            val detail = if (state.randomized) stringResource(R.string.netinfo_mac_randomized) else null
            NetInfoValueRow(
                label = stringResource(R.string.netinfo_mac),
                value = state.mac,
                onCopy = onCopy,
                detail = detail,
            )
        }

        is MacAddressState.Unavailable -> {
            NetInfoFactRow(
                label = stringResource(R.string.netinfo_mac),
                value = macUnavailableText(state.reason),
            )
        }
    }
}
