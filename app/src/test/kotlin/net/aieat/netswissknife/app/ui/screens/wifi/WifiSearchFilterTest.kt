package net.aieat.netswissknife.app.ui.screens.wifi

import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiSearchFilterTest {
    private val networks =
        listOf(
            network(ssid = "FiftyShadesOfPing", bssid = "EE:9F:80:77:61:60", vendor = "Espressif"),
            network(ssid = "Brilliant Radiator!", bssid = "10:20:30:40:50:60", vendor = "Belkin"),
        )

    @Test
    fun `blank query keeps every network`() {
        assertEquals(networks, wifiNetworksMatching(networks, "   "))
    }

    @Test
    fun `SSID match ignores case`() {
        val matches = wifiNetworksMatching(networks, "fiftyshades")

        assertEquals(listOf("FiftyShadesOfPing"), matches.map { it.ssid })
    }

    @Test
    fun `BSSID and vendor of any access point match their network`() {
        assertEquals(listOf("FiftyShadesOfPing"), wifiNetworksMatching(networks, "9f:80").map { it.ssid })
        assertEquals(listOf("Brilliant Radiator!"), wifiNetworksMatching(networks, "belkin").map { it.ssid })
    }

    @Test
    fun `query with no match returns an empty list`() {
        assertTrue(wifiNetworksMatching(networks, "ZZZnomatch").isEmpty())
    }

    private fun network(
        ssid: String,
        bssid: String,
        vendor: String,
    ): WifiNetwork {
        val ap =
            WifiAccessPoint(
                ssid = ssid,
                bssid = bssid,
                rssi = -50,
                frequency = 2412,
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channel = 1,
                band = WifiBand.BAND_2_4GHZ,
                standard = WifiStandard.WIFI_4,
                security = WifiSecurity.WPA2,
                isConnected = false,
                vendor = vendor,
                centerFrequency0 = 2412,
                centerFrequency1 = 0,
                timestampUs = 0,
            )
        return WifiNetwork(ssid = ssid, security = WifiSecurity.WPA2, accessPoints = listOf(ap))
    }
}
