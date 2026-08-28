package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WifiNetwork")
class WifiNetworkTest {

    private fun accessPoint(
        ssid: String = "HomeNet",
        bssid: String = "AA:BB:CC:DD:EE:FF",
        rssi: Int = -55,
        band: WifiBand = WifiBand.BAND_2_4GHZ,
        isConnected: Boolean = false
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = band.minFrequencyMhz + 37,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 6,
        band = band,
        standard = WifiStandard.entries.first(),
        security = WifiSecurity.entries.first(),
        isConnected = isConnected,
        vendor = "Acme",
        centerFrequency0 = 0,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    private fun network(
        ssid: String = "HomeNet",
        security: WifiSecurity = WifiSecurity.entries.first(),
        accessPoints: List<WifiAccessPoint> = listOf(accessPoint())
    ) = WifiNetwork(ssid = ssid, security = security, accessPoints = accessPoints)

    @Nested
    @DisplayName("displaySsid")
    inner class DisplaySsid {

        @Test
        @DisplayName("uses the SSID when it is set")
        fun namedNetwork() {
            assertEquals("HomeNet", network().displaySsid)
        }

        @Test
        @DisplayName("falls back to the BSSID tail for a hidden network")
        fun hiddenNetwork() {
            val hidden = network(
                ssid = "",
                accessPoints = listOf(accessPoint(ssid = "", bssid = "AA:BB:CC:DD:EE:FF"))
            )
            assertEquals("<Hidden: DD:EE:FF>", hidden.displaySsid)
        }
    }

    @Nested
    @DisplayName("signal aggregation")
    inner class SignalAggregation {

        private val multiBand = network(
            accessPoints = listOf(
                accessPoint(rssi = -75, band = WifiBand.BAND_2_4GHZ),
                accessPoint(rssi = -45, band = WifiBand.BAND_5GHZ, bssid = "11:22:33:44:55:66"),
                accessPoint(rssi = -85, band = WifiBand.BAND_5GHZ, bssid = "77:88:99:AA:BB:CC")
            )
        )

        @Test
        @DisplayName("reports the strongest RSSI across BSSIDs")
        fun bestRssi() {
            assertEquals(-45, multiBand.bestRssi)
        }

        @Test
        @DisplayName("reports the best signal quality across BSSIDs")
        fun signalQuality() {
            assertEquals(100, multiBand.signalQualityPercent)
        }

        @Test
        @DisplayName("reports the best signal level across BSSIDs")
        fun signalLevel() {
            assertEquals(SignalLevel.EXCELLENT, multiBand.signalLevel)
        }

        @Test
        @DisplayName("falls back to POOR when there are no access points")
        fun emptyFallsBackToPoor() {
            assertEquals(SignalLevel.POOR, network(accessPoints = emptyList()).signalLevel)
        }

        @Test
        @DisplayName("counts its BSSIDs")
        fun bssidCount() {
            assertEquals(3, multiBand.bssidCount)
            assertEquals(1, network().bssidCount)
        }
    }

    @Nested
    @DisplayName("bands")
    inner class Bands {

        @Test
        @DisplayName("deduplicates the bands its BSSIDs serve")
        fun deduplicates() {
            val net = network(
                accessPoints = listOf(
                    accessPoint(band = WifiBand.BAND_5GHZ),
                    accessPoint(band = WifiBand.BAND_5GHZ, bssid = "11:22:33:44:55:66"),
                    accessPoint(band = WifiBand.BAND_2_4GHZ, bssid = "77:88:99:AA:BB:CC")
                )
            )
            assertEquals(setOf(WifiBand.BAND_5GHZ, WifiBand.BAND_2_4GHZ), net.bands)
        }

        @Test
        @DisplayName("sorts from lowest to highest frequency regardless of scan order")
        fun sortedLowToHigh() {
            val net = network(
                accessPoints = listOf(
                    accessPoint(band = WifiBand.BAND_6GHZ),
                    accessPoint(band = WifiBand.BAND_2_4GHZ, bssid = "11:22:33:44:55:66"),
                    accessPoint(band = WifiBand.BAND_5GHZ, bssid = "77:88:99:AA:BB:CC")
                )
            )
            assertEquals(
                listOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ, WifiBand.BAND_6GHZ),
                net.sortedBands
            )
        }

        @Test
        @DisplayName("reports no bands when there are no access points")
        fun emptyNetwork() {
            val net = network(accessPoints = emptyList())
            assertTrue(net.bands.isEmpty())
            assertTrue(net.sortedBands.isEmpty())
        }
    }

    @Nested
    @DisplayName("isConnected")
    inner class Connected {

        @Test
        @DisplayName("is true when any BSSID is the associated one")
        fun anyConnected() {
            val net = network(
                accessPoints = listOf(
                    accessPoint(isConnected = false),
                    accessPoint(isConnected = true, bssid = "11:22:33:44:55:66")
                )
            )
            assertTrue(net.isConnected)
        }

        @Test
        @DisplayName("is false when no BSSID is associated")
        fun noneConnected() {
            assertFalse(network().isConnected)
        }
    }

    @Nested
    @DisplayName("colorIndex")
    inner class ColorIndex {

        @Test
        @DisplayName("stays inside the palette for a wide range of names")
        fun withinPalette() {
            (0..500).forEach { i ->
                val index = network(ssid = "net-$i").colorIndex
                assertTrue(
                    index in 0 until WifiNetwork.PALETTE_SIZE,
                    "colorIndex $index out of palette range for net-$i"
                )
            }
        }

        @Test
        @DisplayName("is stable across rescans of the same SSID and security pair")
        fun stableAcrossScans() {
            val first = network(accessPoints = listOf(accessPoint(rssi = -40)))
            val second = network(accessPoints = listOf(accessPoint(rssi = -90, bssid = "00:00:00:00:00:01")))
            assertEquals(first.colorIndex, second.colorIndex)
        }

        @Test
        @DisplayName("stays within the palette for a blank SSID")
        fun blankSsid() {
            assertTrue(network(ssid = "").colorIndex in 0 until WifiNetwork.PALETTE_SIZE)
        }
    }

    @Nested
    @DisplayName("id")
    inner class Id {

        @Test
        @DisplayName("identifies a visible network by SSID and security")
        fun visibleNetwork() {
            assertEquals("HomeNet|${WifiSecurity.entries.first().name}", network().id)
        }

        @Test
        @DisplayName("stays stable for a visible network when its BSSIDs change")
        fun stableAcrossRescans() {
            val first = network(accessPoints = listOf(accessPoint(bssid = "AA:AA:AA:AA:AA:AA")))
            val second = network(
                accessPoints = listOf(
                    accessPoint(bssid = "BB:BB:BB:BB:BB:BB"),
                    accessPoint(bssid = "CC:CC:CC:CC:CC:CC")
                )
            )
            assertEquals(first.id, second.id)
        }

        @Test
        @DisplayName("distinguishes two hidden networks by BSSID")
        fun hiddenNetworksAreDistinct() {
            val first = network(ssid = "", accessPoints = listOf(accessPoint(ssid = "", bssid = "AA:AA:AA:AA:AA:AA")))
            val second = network(ssid = "", accessPoints = listOf(accessPoint(ssid = "", bssid = "BB:BB:BB:BB:BB:BB")))
            assertNotEquals(first.id, second.id)
        }

        @Test
        @DisplayName("does not collide with a visible network whose SSID looks like the hidden prefix")
        fun noCollisionWithVisibleNetwork() {
            val hidden = network(ssid = "", accessPoints = listOf(accessPoint(ssid = "", bssid = "AA:AA:AA:AA:AA:AA")))
            val visible = network(ssid = "hidden")
            assertNotEquals(hidden.id, visible.id)
        }

        @Test
        @DisplayName("is defined for a hidden network with no access points")
        fun hiddenWithNoAccessPoints() {
            assertEquals("|hidden|", network(ssid = "", accessPoints = emptyList()).id)
        }

        @Test
        @DisplayName("is unique across a realistic grouped scan")
        fun uniqueAcrossGroupedScan() {
            val scanned = listOf(
                accessPoint(ssid = "HomeNet", bssid = "AA:AA:AA:AA:AA:AA"),
                accessPoint(ssid = "HomeNet", bssid = "AA:AA:AA:AA:AA:AB", band = WifiBand.BAND_5GHZ),
                accessPoint(ssid = "Cafe", bssid = "BB:BB:BB:BB:BB:BB"),
                accessPoint(ssid = "", bssid = "CC:CC:CC:CC:CC:C1"),
                accessPoint(ssid = "", bssid = "CC:CC:CC:CC:CC:C2"),
                accessPoint(ssid = "", bssid = "CC:CC:CC:CC:CC:C3")
            )
            val ids = WifiNetworkGrouper.group(scanned).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "duplicate list keys: $ids")
        }
    }

    @Test
    @DisplayName("compares by value")
    fun valueSemantics() {
        val a = network()
        assertEquals(a, network())
        assertEquals(a.hashCode(), network().hashCode())
        assertTrue(a.toString().contains("HomeNet"))
        assertEquals("Other", a.copy(ssid = "Other").ssid)
        assertEquals("HomeNet", a.component1())
        assertEquals(WifiSecurity.entries.first(), a.component2())
        assertEquals(1, a.component3().size)
    }
}
