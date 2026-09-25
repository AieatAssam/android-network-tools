package net.aieat.netswissknife.app.ui.i18n

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.domain.PortScanPreset
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
}
