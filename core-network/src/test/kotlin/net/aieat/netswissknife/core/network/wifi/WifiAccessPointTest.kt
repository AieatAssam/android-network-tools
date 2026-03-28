package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WifiAccessPoint")
class WifiAccessPointTest {

    private fun makeAp(
        ssid: String = "TestNetwork",
        bssid: String = "AA:BB:CC:DD:EE:FF",
        rssi: Int = -60,
        isConnected: Boolean = false
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = 5180,
        channelWidthMhz = 80,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 36,
        band = WifiBand.BAND_5GHZ,
        standard = WifiStandard.WIFI_5,
        security = WifiSecurity.WPA2,
        isConnected = isConnected,
        vendor = "Cisco",
        centerFrequency0 = 5210,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    @Nested
    @DisplayName("displaySsid")
    inner class DisplaySsid {
        @Test
        fun `non-blank SSID is returned as-is`() {
            val ap = makeAp(ssid = "MyNetwork")
            assertEquals("MyNetwork", ap.displaySsid)
        }

        @Test
        fun `blank SSID falls back to hidden network label`() {
            val ap = makeAp(ssid = "")
            assertEquals("<Hidden Network>", ap.displaySsid)
        }
    }

    @Nested
    @DisplayName("signalQualityPercent")
    inner class SignalQuality {
        @Test
        fun `rssi at -50 gives 100 percent`() {
            assertEquals(100, makeAp(rssi = -50).signalQualityPercent)
        }

        @Test
        fun `rssi above -50 gives 100 percent`() {
            assertEquals(100, makeAp(rssi = -30).signalQualityPercent)
        }

        @Test
        fun `rssi at -60 gives 80 percent`() {
            assertEquals(80, makeAp(rssi = -60).signalQualityPercent)
        }

        @Test
        fun `rssi at -70 gives 60 percent`() {
            assertEquals(60, makeAp(rssi = -70).signalQualityPercent)
        }

        @Test
        fun `rssi at -80 gives 40 percent`() {
            assertEquals(40, makeAp(rssi = -80).signalQualityPercent)
        }

        @Test
        fun `rssi below -90 gives 0 percent`() {
            assertEquals(0, makeAp(rssi = -100).signalQualityPercent)
        }
    }

    @Nested
    @DisplayName("signalLevel")
    inner class SignalLevelTest {
        @Test
        fun `rssi -45 is EXCELLENT`() {
            assertEquals(SignalLevel.EXCELLENT, makeAp(rssi = -45).signalLevel)
        }

        @Test
        fun `rssi -55 is GOOD`() {
            assertEquals(SignalLevel.GOOD, makeAp(rssi = -55).signalLevel)
        }

        @Test
        fun `rssi -65 is FAIR`() {
            assertEquals(SignalLevel.FAIR, makeAp(rssi = -65).signalLevel)
        }

        @Test
        fun `rssi -75 is WEAK`() {
            assertEquals(SignalLevel.WEAK, makeAp(rssi = -75).signalLevel)
        }

        @Test
        fun `rssi -85 is POOR`() {
            assertEquals(SignalLevel.POOR, makeAp(rssi = -85).signalLevel)
        }
    }

    @Nested
    @DisplayName("is80Plus80")
    inner class Is80Plus80Test {
        @Test
        fun `80+80 mode detected when 160 MHz and non-zero centerFreq1`() {
            val ap = makeAp().copy(channelWidthMhz = 160, centerFrequency1 = 5775)
            assertTrue(ap.is80Plus80)
        }

        @Test
        fun `contiguous 160 MHz is not 80+80`() {
            val ap = makeAp().copy(channelWidthMhz = 160, centerFrequency1 = 0)
            assertFalse(ap.is80Plus80)
        }
    }
}
