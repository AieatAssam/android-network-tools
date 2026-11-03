package net.aieat.netswissknife.app.ui.screens.netinfo

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.netinfo.IpAddressScope
import net.aieat.netswissknife.core.network.netinfo.NetInfoSummaryLabels
import net.aieat.netswissknife.core.network.netinfo.NetworkTransport

@StringRes
internal fun scopeLabelRes(scope: IpAddressScope): Int =
    when (scope) {
        IpAddressScope.LOOPBACK -> R.string.netinfo_scope_loopback
        IpAddressScope.LINK_LOCAL -> R.string.netinfo_scope_link_local
        IpAddressScope.PRIVATE -> R.string.netinfo_scope_private
        IpAddressScope.SHARED -> R.string.netinfo_scope_shared
        IpAddressScope.UNIQUE_LOCAL -> R.string.netinfo_scope_unique_local
        IpAddressScope.GLOBAL -> R.string.netinfo_scope_global
        IpAddressScope.MULTICAST -> R.string.netinfo_scope_multicast
        IpAddressScope.UNSPECIFIED -> R.string.netinfo_scope_unspecified
        IpAddressScope.OTHER -> R.string.netinfo_scope_other
    }

@StringRes
internal fun transportLabelRes(transport: NetworkTransport): Int =
    when (transport) {
        NetworkTransport.WIFI -> R.string.netinfo_transport_wifi
        NetworkTransport.CELLULAR -> R.string.netinfo_transport_cellular
        NetworkTransport.ETHERNET -> R.string.netinfo_transport_ethernet
        NetworkTransport.VPN -> R.string.netinfo_transport_vpn
        NetworkTransport.BLUETOOTH -> R.string.netinfo_transport_bluetooth
        NetworkTransport.OTHER -> R.string.netinfo_transport_other
    }

/** Every label the plain-text summary needs, resolved from strings.xml for the current locale. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal fun rememberNetInfoSummaryLabels(): NetInfoSummaryLabels {
    val scopeNames = IpAddressScope.entries.associateWith { stringResource(scopeLabelRes(it)) }
    val transportNames = NetworkTransport.entries.associateWith { stringResource(transportLabelRes(it)) }
    return NetInfoSummaryLabels(
        title = stringResource(R.string.netinfo_screen_title),
        transports = stringResource(R.string.netinfo_transports),
        metered = stringResource(R.string.netinfo_metered),
        roaming = stringResource(R.string.netinfo_roaming),
        yes = stringResource(R.string.netinfo_yes),
        no = stringResource(R.string.netinfo_no),
        wifi = stringResource(R.string.netinfo_section_wifi),
        ssid = stringResource(R.string.netinfo_ssid),
        ssidUnavailable = stringResource(R.string.netinfo_ssid_unavailable),
        signal = stringResource(R.string.netinfo_signal),
        linkSpeed = stringResource(R.string.netinfo_link_speed),
        frequency = stringResource(R.string.netinfo_frequency),
        interfaceName = stringResource(R.string.netinfo_interface),
        addresses = stringResource(R.string.netinfo_addresses),
        gateways = stringResource(R.string.netinfo_gateway),
        dns = stringResource(R.string.netinfo_dns),
        mtu = stringResource(R.string.netinfo_mtu),
        mac = stringResource(R.string.netinfo_mac),
        macUnavailable = stringResource(R.string.netinfo_not_available),
        domains = stringResource(R.string.netinfo_domains),
        proxy = stringResource(R.string.netinfo_proxy),
        noneValue = stringResource(R.string.netinfo_none),
        publicIp = stringResource(R.string.netinfo_section_public),
        publicIpv4 = stringResource(R.string.netinfo_public_ipv4),
        publicIpv6 = stringResource(R.string.netinfo_public_ipv6),
        hostname = stringResource(R.string.netinfo_hostname),
        location = stringResource(R.string.netinfo_location),
        asn = stringResource(R.string.netinfo_asn),
        notChecked = stringResource(R.string.netinfo_not_checked),
        notAvailable = stringResource(R.string.netinfo_not_available),
        noActiveNetwork = stringResource(R.string.netinfo_no_network_title),
        scopeName = { scope -> scopeNames.getValue(scope) },
        transportName = { transport -> transportNames.getValue(transport) },
    )
}
