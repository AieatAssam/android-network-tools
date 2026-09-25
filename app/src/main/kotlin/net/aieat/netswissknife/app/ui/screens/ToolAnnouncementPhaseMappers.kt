package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus

internal fun wifiAnnouncementPhase(state: WifiScanUiState): ToolAnnouncementPhase? = when (state) {
    WifiScanUiState.Idle -> null
    WifiScanUiState.Scanning -> ToolAnnouncementPhase.RUNNING
    WifiScanUiState.Cancelled, WifiScanUiState.Paused -> ToolAnnouncementPhase.CANCELED
    is WifiScanUiState.Success -> if (
        state.result.refreshStatus in setOf(
            WifiScanRefreshStatus.NOT_UPDATED,
            WifiScanRefreshStatus.TIMED_OUT,
            WifiScanRefreshStatus.REJECTED,
            WifiScanRefreshStatus.FAILED,
            WifiScanRefreshStatus.PERMISSION_DENIED,
        )
    ) {
        ToolAnnouncementPhase.PARTIAL
    } else {
        ToolAnnouncementPhase.FINISHED
    }
    WifiScanUiState.NoPermission,
    WifiScanUiState.NotSupported,
    WifiScanUiState.WifiDisabled,
    WifiScanUiState.LocationDisabled,
    is WifiScanUiState.Error -> ToolAnnouncementPhase.ERROR
}

internal fun mdnsAnnouncementPhase(state: MdnsDiscoveryUiState): ToolAnnouncementPhase? = when {
    state.error != null -> ToolAnnouncementPhase.ERROR
    state.isScanning || state.isCanceling -> ToolAnnouncementPhase.RUNNING
    state.scanCanceled -> if (state.services.isNotEmpty() || state.truncationReasons.isNotEmpty()) {
        ToolAnnouncementPhase.PARTIAL
    } else {
        ToolAnnouncementPhase.CANCELED
    }
    state.truncationReasons.isNotEmpty() -> ToolAnnouncementPhase.PARTIAL
    state.scanComplete -> ToolAnnouncementPhase.FINISHED
    else -> null
}
