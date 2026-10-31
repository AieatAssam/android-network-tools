package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiBestChannel24GHzTest {
    @Test
    fun `does not recommend channel 1 when a strong AP on channel 2 overlaps it and channel 1 has no AP`() {
        val accessPoints = listOf(ap("2-strong", rssi = -50, frequency = 2417))

        val result = resultFor(accessPoints)

        // Channel 2 overlaps channels 1 and 6 under the +/-4 rule; channel 11 is clear.
        assertEquals(11, result.bestChannel24GHz)
    }

    @Test
    fun `missing channels are scored by their overlap, not treated as clear`() {
        val accessPoints =
            listOf(
                ap("1-a", rssi = -40, frequency = 2412),
                ap("6-a", rssi = -40, frequency = 2437),
            )

        val result = resultFor(accessPoints)

        // Channel 11 has no AP and no overlap with 1 or 6 in the +/-4 window, so it is the clear one.
        assertEquals(11, result.bestChannel24GHz)
    }

    @Test
    fun `saturated environment still compares raw interference instead of tying at the clamp`() {
        // Enough strong APs on 1 and 6 that both clamp to 1.0; channel 11 is still clear.
        val accessPoints =
            (1..12).map { ap("1-$it", rssi = -30, frequency = 2412) } +
                (1..12).map { ap("6-$it", rssi = -30, frequency = 2437) }

        val result = resultFor(accessPoints)

        assertEquals(11, result.bestChannel24GHz)
    }

    @Test
    fun `returns null when no 2 point 4 GHz access points were scanned`() {
        val accessPoints = listOf(ap("5", rssi = -50, frequency = 5180, band = WifiBand.BAND_5GHZ))

        assertNull(resultFor(accessPoints).bestChannel24GHz)
    }

    private fun resultFor(accessPoints: List<WifiAccessPoint>) =
        WifiScanResult(
            accessPoints = accessPoints,
            channels = WifiChannelAnalyzer.analyze(accessPoints),
            connectedNetwork = null,
            scanTimestampMs = 0,
            isWifiEnabled = true,
        )

    private fun ap(
        id: String,
        rssi: Int,
        frequency: Int,
        band: WifiBand = WifiBand.BAND_2_4GHZ,
    ) = WifiAccessPoint(
        ssid = "net-$id",
        bssid = "00:11:22:33:44:%02X".format(id.hashCode() and 0xFF),
        rssi = rssi,
        frequency = frequency,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = WifiChannelHelper.frequencyToChannel(frequency, band),
        band = band,
        standard = WifiStandard.WIFI_4,
        security = WifiSecurity.WPA2,
        isConnected = false,
        vendor = "",
        centerFrequency0 = frequency,
        centerFrequency1 = 0,
        timestampUs = 0,
    )
}
