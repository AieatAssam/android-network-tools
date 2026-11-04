package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.netinfo.IpFamily
import net.aieat.netswissknife.core.network.netinfo.LocalAddress
import net.aieat.netswissknife.core.network.netinfo.MacUnavailableReason
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.SsidUnavailableReason
import net.aieat.netswissknife.core.network.netinfo.WifiLinkDetails

@Composable
internal fun ssidUnavailableText(reason: SsidUnavailableReason): String =
    when (reason) {
        SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED -> stringResource(R.string.netinfo_ssid_unavailable)
        SsidUnavailableReason.NOT_REPORTED -> stringResource(R.string.netinfo_not_available)
    }

@Composable
internal fun macUnavailableText(reason: MacUnavailableReason): String =
    when (reason) {
        MacUnavailableReason.HIDDEN_BY_ANDROID -> stringResource(R.string.netinfo_mac_hidden)
        MacUnavailableReason.NOT_REPORTED -> stringResource(R.string.netinfo_mac_not_reported)
    }

@Composable
internal fun frequencyText(wifi: WifiLinkDetails): String {
    val frequency = wifi.frequencyMhz ?: return stringResource(R.string.netinfo_not_available)
    val band = wifi.band
    val channel = wifi.channel
    return if (band != null && channel != null) {
        stringResource(R.string.netinfo_frequency_full, frequency, band.displayName, channel)
    } else {
        stringResource(R.string.netinfo_value_mhz, frequency)
    }
}

@Composable
internal fun addressLabel(address: LocalAddress): String {
    val family =
        if (address.family == IpFamily.IPV4) {
            stringResource(R.string.netinfo_family_ipv4)
        } else {
            stringResource(R.string.netinfo_family_ipv6)
        }
    return "$family · ${stringResource(scopeLabelRes(address.scope))}"
}

@Composable
internal fun addressDetail(address: LocalAddress): String =
    address.subnetMask?.let { mask ->
        stringResource(R.string.netinfo_address_detail_v4, address.prefixLength, mask)
    } ?: stringResource(R.string.netinfo_address_detail_v6, address.prefixLength)

@Composable
internal fun proxyText(local: NetInfoSnapshot): String {
    val proxy = local.proxy
    if (proxy == null || !proxy.isSet) return stringResource(R.string.netinfo_none)
    return proxy.pacUrl ?: listOfNotNull(proxy.host, proxy.port?.toString()).joinToString(":")
}
