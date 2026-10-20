package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WifiScanResultTest {

    private fun ap(
        ssid: String,
        bssid: String,
        security: WifiSecurity = WifiSecurity.WPA2
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = -60,
        frequency = 2437,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 6,
        band = WifiBand.BAND_2_4GHZ,
        standard = WifiStandard.WIFI_5,
        security = security,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 2437,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    private fun result(accessPoints: List<WifiAccessPoint>) = WifiScanResult(
        accessPoints = accessPoints,
        channels = emptyList(),
        connectedNetwork = null,
        scanTimestampMs = 0L,
        isWifiEnabled = true
    )

    @Test
    fun `two hidden BSSIDs count as two logical network rows`() {
        val scan = result(
            listOf(
                ap("", "AA:BB:CC:DD:EE:01"),
                ap("", "AA:BB:CC:DD:EE:02")
            )
        )

        assertEquals(2, scan.uniqueNetworkCount)
    }

    @Test
    fun `same SSID and security across BSSIDs count as one logical network`() {
        val scan = result(
            listOf(
                ap("HomeWifi", "AA:BB:CC:DD:EE:01"),
                ap("HomeWifi", "AA:BB:CC:DD:EE:02")
            )
        )

        assertEquals(1, scan.uniqueNetworkCount)
    }

    @Test
    fun `same SSID with different security counts as separate logical networks`() {
        val scan = result(
            listOf(
                ap("CafeWifi", "AA:BB:CC:DD:EE:01", WifiSecurity.OPEN),
                ap("CafeWifi", "AA:BB:CC:DD:EE:02", WifiSecurity.WPA2)
            )
        )

        assertEquals(2, scan.uniqueNetworkCount)
    }

    @Test
    fun `empty scan has no logical networks`() {
        assertEquals(0, result(emptyList()).uniqueNetworkCount)
    }
}
