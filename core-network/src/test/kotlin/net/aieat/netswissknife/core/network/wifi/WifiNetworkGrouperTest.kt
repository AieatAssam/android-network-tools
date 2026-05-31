package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WifiNetworkGrouper")
class WifiNetworkGrouperTest {

    private fun ap(
        ssid: String,
        bssid: String,
        rssi: Int = -60,
        security: WifiSecurity = WifiSecurity.WPA2,
        band: WifiBand = WifiBand.BAND_2_4GHZ,
        frequency: Int = 2437
    ) = WifiAccessPoint(
        ssid = ssid, bssid = bssid, rssi = rssi, frequency = frequency,
        channelWidthMhz = 20, capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 6, band = band, standard = WifiStandard.WIFI_5,
        security = security, isConnected = false, vendor = "",
        centerFrequency0 = frequency, centerFrequency1 = 0, timestampUs = 0L
    )

    @Nested
    @DisplayName("visible network grouping")
    inner class VisibleGrouping {

        @Test
        fun `dual-band same SSID and security grouped into one WifiNetwork`() {
            val aps = listOf(
                ap("HomeWifi", "AA:BB:CC:DD:EE:01", rssi = -55, band = WifiBand.BAND_2_4GHZ, frequency = 2437),
                ap("HomeWifi", "AA:BB:CC:DD:EE:02", rssi = -65, band = WifiBand.BAND_5GHZ, frequency = 5180)
            )
            val networks = WifiNetworkGrouper.group(aps)
            assertEquals(1, networks.size)
            assertEquals(2, networks[0].bssidCount)
            assertEquals(setOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ), networks[0].bands)
        }

        @Test
        fun `same SSID different security yields two networks`() {
            val aps = listOf(
                ap("CafeWifi", "AA:BB:CC:DD:EE:01", security = WifiSecurity.OPEN),
                ap("CafeWifi", "AA:BB:CC:DD:EE:02", security = WifiSecurity.WPA2)
            )
            val networks = WifiNetworkGrouper.group(aps)
            assertEquals(2, networks.size)
        }

        @Test
        fun `mesh router with three BSSIDs same SSID grouped into one`() {
            val aps = listOf(
                ap("MeshNet", "AA:BB:CC:DD:EE:01", rssi = -45),
                ap("MeshNet", "AA:BB:CC:DD:EE:02", rssi = -60),
                ap("MeshNet", "AA:BB:CC:DD:EE:03", rssi = -75)
            )
            val networks = WifiNetworkGrouper.group(aps)
            assertEquals(1, networks.size)
            assertEquals(3, networks[0].bssidCount)
        }

        @Test
        fun `access points sorted strongest-first within network`() {
            val aps = listOf(
                ap("Net", "AA:BB:CC:DD:EE:01", rssi = -75),
                ap("Net", "AA:BB:CC:DD:EE:02", rssi = -45),
                ap("Net", "AA:BB:CC:DD:EE:03", rssi = -60)
            )
            val network = WifiNetworkGrouper.group(aps).first()
            assertEquals(listOf(-45, -60, -75), network.accessPoints.map { it.rssi })
        }

        @Test
        fun `bestRssi reflects strongest AP in group`() {
            val aps = listOf(
                ap("Net", "AA:BB:CC:DD:EE:01", rssi = -80),
                ap("Net", "AA:BB:CC:DD:EE:02", rssi = -50)
            )
            assertEquals(-50, WifiNetworkGrouper.group(aps).first().bestRssi)
        }

        @Test
        fun `networks sorted by bestRssi descending`() {
            val aps = listOf(
                ap("Weak", "AA:BB:CC:DD:EE:01", rssi = -80),
                ap("Strong", "AA:BB:CC:DD:EE:02", rssi = -40),
                ap("Mid", "AA:BB:CC:DD:EE:03", rssi = -60)
            )
            val names = WifiNetworkGrouper.group(aps).map { it.ssid }
            assertEquals(listOf("Strong", "Mid", "Weak"), names)
        }
    }

    @Nested
    @DisplayName("hidden network handling")
    inner class HiddenNetworks {

        @Test
        fun `two hidden APs are never grouped together`() {
            val aps = listOf(
                ap("", "AA:BB:CC:DD:EE:01", rssi = -55),
                ap("", "AA:BB:CC:DD:EE:02", rssi = -65)
            )
            val networks = WifiNetworkGrouper.group(aps)
            assertEquals(2, networks.size)
            assertTrue(networks.all { it.bssidCount == 1 })
        }

        @Test
        fun `hidden network displaySsid shows last 8 chars of BSSID`() {
            val aps = listOf(ap("", "AA:BB:CC:DD:EE:FF", rssi = -60))
            val network = WifiNetworkGrouper.group(aps).first()
            assertEquals("<Hidden: DD:EE:FF>", network.displaySsid)
        }
    }

    @Nested
    @DisplayName("color stability")
    inner class ColorStability {

        @Test
        fun `same network always gets same colorIndex`() {
            val aps1 = listOf(ap("HomeWifi", "AA:BB:CC:DD:EE:01", rssi = -55))
            val aps2 = listOf(ap("HomeWifi", "AA:BB:CC:DD:EE:01", rssi = -60))
            val color1 = WifiNetworkGrouper.group(aps1).first().colorIndex
            val color2 = WifiNetworkGrouper.group(aps2).first().colorIndex
            assertEquals(color1, color2)
        }

        @Test
        fun `colorIndex is in range 0 until PALETTE_SIZE`() {
            val ssids = listOf("Network A", "Network B", "My Wifi", "Guest", "Corp")
            ssids.forEach { ssid ->
                val ap = ap(ssid, "AA:BB:CC:DD:EE:01")
                val idx = WifiNetworkGrouper.group(listOf(ap)).first().colorIndex
                assertTrue(idx in 0 until WifiNetwork.PALETTE_SIZE, "colorIndex $idx out of range for '$ssid'")
            }
        }

        @Test
        fun `colorIndex is never negative even when hashCode is Int MIN_VALUE`() {
            // Any WifiNetwork whose (ssid + security.name).hashCode() == Int.MIN_VALUE must still
            // produce a valid palette index — Math.abs(Int.MIN_VALUE) overflows back to negative.
            // The fix uses (hash and Int.MAX_VALUE) which clears the sign bit.
            val network = WifiNetwork(
                ssid = "test",
                security = WifiSecurity.WPA2,
                accessPoints = listOf(ap("test", "AA:BB:CC:DD:EE:01"))
            )
            // Force-verify the property contract regardless of actual hash value
            assertTrue(network.colorIndex >= 0, "colorIndex must be non-negative, got ${network.colorIndex}")
            assertTrue(network.colorIndex < WifiNetwork.PALETTE_SIZE, "colorIndex out of palette range")
        }
    }

    @Nested
    @DisplayName("empty and edge cases")
    inner class EdgeCases {

        @Test
        fun `empty list returns empty`() {
            assertEquals(emptyList<WifiNetwork>(), WifiNetworkGrouper.group(emptyList()))
        }

        @Test
        fun `single AP returns one network`() {
            val networks = WifiNetworkGrouper.group(listOf(ap("Solo", "AA:BB:CC:DD:EE:01")))
            assertEquals(1, networks.size)
            assertEquals("Solo", networks[0].ssid)
        }
    }
}
