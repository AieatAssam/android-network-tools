package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.components.ToolAnnouncementPhase
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsTruncationReason
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToolAnnouncementPhaseMappersTest {
    @Test
    fun `wifi maps idle running cancellation and errors`() {
        assertNull(wifiAnnouncementPhase(WifiScanUiState.Idle))
        assertEquals(ToolAnnouncementPhase.RUNNING, wifiAnnouncementPhase(WifiScanUiState.Scanning))
        assertEquals(ToolAnnouncementPhase.CANCELED, wifiAnnouncementPhase(WifiScanUiState.Cancelled))
        assertEquals(ToolAnnouncementPhase.CANCELED, wifiAnnouncementPhase(WifiScanUiState.Paused))
        assertEquals(ToolAnnouncementPhase.ERROR, wifiAnnouncementPhase(WifiScanUiState.Error("scan failed")))
        assertEquals(ToolAnnouncementPhase.ERROR, wifiAnnouncementPhase(WifiScanUiState.NoPermission))
        assertEquals(ToolAnnouncementPhase.ERROR, wifiAnnouncementPhase(WifiScanUiState.NotSupported))
        assertEquals(ToolAnnouncementPhase.ERROR, wifiAnnouncementPhase(WifiScanUiState.WifiDisabled))
        assertEquals(ToolAnnouncementPhase.ERROR, wifiAnnouncementPhase(WifiScanUiState.LocationDisabled))
    }

    @Test
    fun `wifi retained results report partial refresh failure and current results report finished`() {
        val refreshFailures = listOf(
            WifiScanRefreshStatus.NOT_UPDATED,
            WifiScanRefreshStatus.TIMED_OUT,
            WifiScanRefreshStatus.REJECTED,
            WifiScanRefreshStatus.FAILED,
            WifiScanRefreshStatus.PERMISSION_DENIED,
        )
        refreshFailures.forEach { status ->
            assertEquals(
                ToolAnnouncementPhase.PARTIAL,
                wifiAnnouncementPhase(wifiSuccess(status, isFresh = false)),
                "refresh status $status",
            )
        }
        assertEquals(
            ToolAnnouncementPhase.FINISHED,
            wifiAnnouncementPhase(wifiSuccess(WifiScanRefreshStatus.UPDATED)),
        )
        assertEquals(
            ToolAnnouncementPhase.FINISHED,
            wifiAnnouncementPhase(wifiSuccess(WifiScanRefreshStatus.NOT_REQUESTED)),
        )
    }

    @Test
    fun `mdns maps idle running canceling and no-result cancellation`() {
        assertNull(mdnsAnnouncementPhase(MdnsDiscoveryUiState()))
        assertEquals(
            ToolAnnouncementPhase.RUNNING,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(isScanning = true)),
        )
        assertEquals(
            ToolAnnouncementPhase.RUNNING,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(isCanceling = true)),
        )
        assertEquals(
            ToolAnnouncementPhase.CANCELED,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(scanCanceled = true)),
        )
    }

    @Test
    fun `mdns maps retained or truncated results partial complete scans finished and errors`() {
        val service = DiscoveredService(
            serviceType = "_http._tcp",
            instanceName = "router",
            displayName = "router",
            hostname = "router.local",
            port = 80,
        )
        assertEquals(
            ToolAnnouncementPhase.PARTIAL,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(scanCanceled = true, services = listOf(service))),
        )
        assertEquals(
            ToolAnnouncementPhase.PARTIAL,
            mdnsAnnouncementPhase(
                MdnsDiscoveryUiState(
                    scanCanceled = true,
                    truncationReasons = setOf(MdnsTruncationReason.QUERY_LIMIT),
                ),
            ),
        )
        assertEquals(
            ToolAnnouncementPhase.PARTIAL,
            mdnsAnnouncementPhase(
                MdnsDiscoveryUiState(
                    scanComplete = true,
                    truncationReasons = setOf(MdnsTruncationReason.QUERY_LIMIT),
                ),
            ),
        )
        assertEquals(
            ToolAnnouncementPhase.FINISHED,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(scanComplete = true)),
        )
        assertEquals(
            ToolAnnouncementPhase.ERROR,
            mdnsAnnouncementPhase(MdnsDiscoveryUiState(isScanning = true, error = "setsockopt failed")),
        )
    }

    private fun wifiSuccess(
        refreshStatus: WifiScanRefreshStatus,
        isFresh: Boolean = true,
    ) = WifiScanUiState.Success(
        WifiScanResult(
            accessPoints = if (isFresh) emptyList() else listOf(cachedAccessPoint()),
            channels = emptyList(),
            connectedNetwork = null,
            scanTimestampMs = 0L,
            isWifiEnabled = true,
            isFresh = isFresh,
            refreshStatus = refreshStatus,
        ),
    )

    private fun cachedAccessPoint() = WifiAccessPoint(
        ssid = "cached-network",
        bssid = "AA:BB:CC:DD:EE:FF",
        rssi = -55,
        frequency = 2_412,
        channelWidthMhz = 20,
        capabilities = "[ESS]",
        channel = 1,
        band = WifiBand.BAND_2_4GHZ,
        standard = WifiStandard.WIFI_4,
        security = WifiSecurity.OPEN,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 0,
        centerFrequency1 = 0,
        timestampUs = 0L,
    )
}
