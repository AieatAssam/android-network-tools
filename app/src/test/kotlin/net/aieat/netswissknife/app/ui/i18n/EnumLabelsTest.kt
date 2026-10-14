package net.aieat.netswissknife.app.ui.i18n

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumLabelsTest {
    @Test
    fun `port scan presets map exhaustively to app resources`() {
        assertEquals(
            listOf(
                UiText.Res(R.string.ports_preset_common_services),
                UiText.Res(R.string.ports_preset_well_known),
                UiText.Res(R.string.ports_preset_web_services),
                UiText.Res(R.string.ports_preset_databases),
                UiText.Res(R.string.ports_preset_mail_services),
                UiText.Res(R.string.ports_preset_remote_access),
                UiText.Res(R.string.ports_preset_custom_range),
            ),
            PortScanPreset.entries.map(PortScanPreset::uiText),
        )
    }

    @Test
    fun `DNS server labels and descriptions map exhaustively to app resources`() {
        val presets = DnsServer.presets
        assertEquals(
            listOf(
                UiText.Res(R.string.dns_server_system_name),
                UiText.Res(R.string.dns_server_google_name),
                UiText.Res(R.string.dns_server_cloudflare_name),
                UiText.Res(R.string.dns_server_opendns_name),
                UiText.Res(R.string.dns_server_quad9_name),
            ),
            presets.map(DnsServer::uiLabel),
        )
        assertEquals(
            listOf(
                UiText.Res(R.string.dns_server_system_description),
                UiText.Res(R.string.dns_server_google_description),
                UiText.Res(R.string.dns_server_cloudflare_description),
                UiText.Res(R.string.dns_server_opendns_description),
                UiText.Res(R.string.dns_server_quad9_description),
            ),
            presets.map(DnsServer::uiDescription),
        )
        assertEquals(UiText.Res(R.string.dns_server_custom_name), DnsServer.Custom("1.1.1.1").uiLabel())
        assertEquals(UiText.Plain("1.1.1.1"), DnsServer.Custom("1.1.1.1").uiDescription())
    }
}
