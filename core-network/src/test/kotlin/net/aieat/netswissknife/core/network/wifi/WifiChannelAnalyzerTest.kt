package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiChannelAnalyzerTest {

    @Test
    fun `4 point 9 and adjacent 5 GHz frequencies map to separate channel groups`() {
        val ap4910 = accessPoint("49-1", -50, WifiBand.BAND_5GHZ, 4910).copy(
            channel = WifiChannelHelper.frequencyToChannel(4910, WifiBand.BAND_5GHZ),
        )
        val ap5170 = accessPoint("51-0", -50, WifiBand.BAND_5GHZ, 5170).copy(
            channel = WifiChannelHelper.frequencyToChannel(5170, WifiBand.BAND_5GHZ),
        )
        val ap5180 = accessPoint("51-1", -50, WifiBand.BAND_5GHZ, 5180).copy(
            channel = WifiChannelHelper.frequencyToChannel(5180, WifiBand.BAND_5GHZ),
        )

        val channels = WifiChannelAnalyzer.analyze(listOf(ap4910, ap5170, ap5180))

        assertEquals(setOf(182, 34, 36), channels.map { it.channel }.toSet())
        assertEquals(3, channels.size)
        assertEquals(1, channels.single { it.channel == 182 }.accessPointCount)
        assertEquals(1, channels.single { it.channel == 34 }.accessPointCount)
        assertEquals(1, channels.single { it.channel == 36 }.accessPointCount)
    }

    @Test
    fun `distinct frequencies with aliased channel numbers stay separate`() {
        fun apAtFrequency(id: String, rssi: Int, frequency: Int) =
            accessPoint(id, rssi, WifiBand.BAND_5GHZ, frequency).copy(
                channel = WifiChannelHelper.frequencyToChannel(frequency, WifiBand.BAND_5GHZ),
            )

        val accessPoints = listOf(
            apAtFrequency("58-1", -45, 5885),
            apAtFrequency("58-2", -55, 5885),
            apAtFrequency("59-0", -60, 5900),
            apAtFrequency("49-1", -65, 4910),
        )

        val channels = WifiChannelAnalyzer.analyze(accessPoints)

        assertEquals(3, channels.size)
        assertEquals(2, channels.single { it.frequencyMhz == 5885 }.accessPointCount)
        assertEquals(1, channels.single { it.frequencyMhz == 5900 }.accessPointCount)
        assertEquals(1, channels.single { it.frequencyMhz == 4910 }.accessPointCount)
        assertEquals(177, channels.single { it.frequencyMhz == 5885 }.channel)
        assertEquals(177, channels.single { it.frequencyMhz == 5900 }.channel)
        assertEquals(182, channels.single { it.frequencyMhz == 4910 }.channel)
    }

    @Test
    fun `same channel number stays separate across bands and 2 point 4 overlap ignores 6 GHz`() {
        val accessPoints24 = listOf(
            accessPoint("24-1", -40, WifiBand.BAND_2_4GHZ, 2412),
            accessPoint("24-2", -40, WifiBand.BAND_2_4GHZ, 2412),
            accessPoint("24-3", -40, WifiBand.BAND_2_4GHZ, 2412),
        )
        // Keep input order consistent with the production scan's strongest-first ordering.
        val accessPoints = listOf(accessPoint("6-1", -30, WifiBand.BAND_6GHZ, 5955)) + accessPoints24

        val channels = WifiChannelAnalyzer.analyze(accessPoints)
        val channels24Only = WifiChannelAnalyzer.analyze(accessPoints24)
        val channel24 = channels.single { it.band == WifiBand.BAND_2_4GHZ && it.channel == 1 }
        val channel6 = channels.single { it.band == WifiBand.BAND_6GHZ && it.channel == 1 }
        val fullResult = WifiScanResult(
            accessPoints = accessPoints,
            channels = channels,
            connectedNetwork = null,
            scanTimestampMs = 0,
            isWifiEnabled = true,
        )
        val baselineResult = WifiScanResult(
            accessPoints = accessPoints24,
            channels = channels24Only,
            connectedNetwork = null,
            scanTimestampMs = 0,
            isWifiEnabled = true,
        )

        assertEquals(2, channels.size)
        assertEquals(3, channel24.accessPointCount)
        assertEquals(1, channel6.accessPointCount)
        assertEquals(0.3f, channel24.congestionScore, 0.0001f)
        assertEquals(1.0f, channel6.congestionScore)
        val baselineChannel24 = channels24Only.single { it.band == WifiBand.BAND_2_4GHZ && it.channel == 1 }
        assertEquals(baselineChannel24.congestionScore, channel24.congestionScore)
        assertEquals(6, baselineResult.bestChannel24GHz)
        assertEquals(baselineResult.bestChannel24GHz, fullResult.bestChannel24GHz)
        assertTrue(fullResult.bestChannel24GHz != 1)
    }

    private fun accessPoint(
        bssidSuffix: String,
        rssi: Int,
        band: WifiBand,
        frequency: Int,
    ) = WifiAccessPoint(
        ssid = "network-$bssidSuffix",
        bssid = "00:11:22:33:44:$bssidSuffix",
        rssi = rssi,
        frequency = frequency,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 1,
        band = band,
        standard = if (band == WifiBand.BAND_6GHZ) WifiStandard.WIFI_6E else WifiStandard.WIFI_4,
        security = WifiSecurity.WPA2,
        isConnected = false,
        vendor = "",
        centerFrequency0 = frequency,
        centerFrequency1 = 0,
        timestampUs = 0,
    )
}
