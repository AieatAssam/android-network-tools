package net.aieat.netswissknife.app.ui.i18n

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.domain.PortScanPreset

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
