package net.aieat.netswissknife.app.ui.i18n

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.network.dns.DnsServer

/** App-owned, locale-aware labels for domain enums shown in Compose UI. */
internal fun PortScanPreset.uiText(): UiText = when (this) {
    PortScanPreset.COMMON -> UiText.Res(R.string.ports_preset_common_services)
    PortScanPreset.WELL_KNOWN -> UiText.Res(R.string.ports_preset_well_known)
    PortScanPreset.WEB -> UiText.Res(R.string.ports_preset_web_services)
    PortScanPreset.DATABASE -> UiText.Res(R.string.ports_preset_databases)
    PortScanPreset.MAIL -> UiText.Res(R.string.ports_preset_mail_services)
    PortScanPreset.REMOTE -> UiText.Res(R.string.ports_preset_remote_access)
    PortScanPreset.CUSTOM -> UiText.Res(R.string.ports_preset_custom_range)
}

/** App-owned, locale-aware presentation for DNS resolver choices. */
internal fun DnsServer.uiLabel(): UiText = when (this) {
    is DnsServer.System -> UiText.Res(R.string.dns_server_system_name)
    DnsServer.Google -> UiText.Res(R.string.dns_server_google_name)
    DnsServer.Cloudflare -> UiText.Res(R.string.dns_server_cloudflare_name)
    DnsServer.OpenDns -> UiText.Res(R.string.dns_server_opendns_name)
    DnsServer.Quad9 -> UiText.Res(R.string.dns_server_quad9_name)
    is DnsServer.Custom -> UiText.Res(R.string.dns_server_custom_name)
}

/** Custom resolvers retain the entered address as their descriptive value. */
internal fun DnsServer.uiDescription(): UiText = when (this) {
    is DnsServer.System -> UiText.Res(R.string.dns_server_system_description)
    DnsServer.Google -> UiText.Res(R.string.dns_server_google_description)
    DnsServer.Cloudflare -> UiText.Res(R.string.dns_server_cloudflare_description)
    DnsServer.OpenDns -> UiText.Res(R.string.dns_server_opendns_description)
    DnsServer.Quad9 -> UiText.Res(R.string.dns_server_quad9_description)
    is DnsServer.Custom -> UiText.Plain(address)
}
