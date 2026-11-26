package net.aieat.netswissknife.app.ui.screens.wifi

import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiNetwork
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiBandSelectionTest {
    @Test
    fun `spectrum follows an explicit band choice over the connected band`() {
        assertEquals(
            WifiBand.BAND_6GHZ,
            spectrumBandFor(WifiBand.BAND_6GHZ, WifiBand.BAND_5GHZ, listOf(WifiBand.BAND_5GHZ, WifiBand.BAND_6GHZ)),
        )
    }

    @Test
    fun `spectrum in All mode uses the connected band when it was scanned`() {
        assertEquals(
            WifiBand.BAND_5GHZ,
            spectrumBandFor(null, WifiBand.BAND_5GHZ, listOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ)),
        )
    }

    @Test
    fun `spectrum in All mode falls back to the first scanned band when not connected`() {
        assertEquals(
            WifiBand.BAND_2_4GHZ,
            spectrumBandFor(null, null, listOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ)),
        )
    }

    @Test
    fun `spectrum in All mode ignores a connected band that was not scanned`() {
        assertEquals(
            WifiBand.BAND_2_4GHZ,
            spectrumBandFor(null, WifiBand.BAND_5GHZ, listOf(WifiBand.BAND_2_4GHZ)),
        )
    }

    @Test
    fun `spectrum has no band when nothing was scanned`() {
        assertNull(spectrumBandFor(null, WifiBand.BAND_5GHZ, emptyList()))
    }

    @Test
    fun `the spectrum's band choice counts exactly the access points it would plot, in band order`() {
        val aps =
            listOf(
                ap("A", WifiBand.BAND_5GHZ, -40),
                ap("B", WifiBand.BAND_2_4GHZ, -50),
                ap("C", WifiBand.BAND_2_4GHZ, -60),
                ap("D", WifiBand.BAND_60GHZ, -60),
            )

        val counts = spectrumBandCounts(aps)

        assertEquals(listOf(WifiBand.BAND_2_4GHZ to 2, WifiBand.BAND_5GHZ to 1), counts)
        counts.forEach { (band, count) -> assertEquals(aps.count { it.band == band }, count) }
    }

    @Test
    fun `on the All tab a band chosen on the chart is plotted while it has access points`() {
        val counts = listOf(WifiBand.BAND_2_4GHZ to 3, WifiBand.BAND_5GHZ to 2)
        val detected = listOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ)

        assertEquals(WifiBand.BAND_2_4GHZ, spectrumPlotBand(null, null, null, detected, counts))
        assertEquals(WifiBand.BAND_5GHZ, spectrumPlotBand(null, WifiBand.BAND_5GHZ, null, detected, counts))
        // A choice whose band has no access points left falls back, and a band tab wins over the choice.
        assertEquals(WifiBand.BAND_2_4GHZ, spectrumPlotBand(null, WifiBand.BAND_6GHZ, null, detected, counts))
        assertEquals(
            WifiBand.BAND_2_4GHZ,
            spectrumPlotBand(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ, null, detected, counts),
        )
    }

    @Test
    fun `the chart offers bands only on the All tab with more than one band`() {
        val counts = listOf(WifiBand.BAND_2_4GHZ to 3, WifiBand.BAND_5GHZ to 2)

        assertEquals(counts, spectrumBandChoices(null, counts))
        assertEquals(emptyList<Pair<WifiBand, Int>>(), spectrumBandChoices(WifiBand.BAND_5GHZ, counts))
        assertEquals(emptyList<Pair<WifiBand, Int>>(), spectrumBandChoices(null, counts.take(1)))
    }

    @Test
    fun `the spectrum offers no band without access points`() {
        assertEquals(emptyList<Pair<WifiBand, Int>>(), spectrumBandCounts(emptyList()))
    }

    @Test
    fun `All mode keeps access points from every band`() {
        val aps = listOf(ap("A", WifiBand.BAND_2_4GHZ, -40), ap("B", WifiBand.BAND_5GHZ, -60))

        val sorted = sortedAccessPoints(aps, null, ApSortOrder.SIGNAL)

        assertEquals(listOf("A", "B"), sorted.map { it.ssid })
    }

    @Test
    fun `a band filter removes access points and networks outside that band`() {
        val only2 = ap("Only2", WifiBand.BAND_2_4GHZ, -40)
        val only5 = ap("Only5", WifiBand.BAND_5GHZ, -40)
        val networks = listOf(network("Only2", only2), network("Only5", only5))

        assertEquals(
            listOf("Only5"),
            sortedAccessPoints(listOf(only2, only5), WifiBand.BAND_5GHZ, ApSortOrder.SSID).map { it.ssid },
        )
        assertEquals(
            listOf("Only5"),
            sortedNetworks(networks, WifiBand.BAND_5GHZ, ApSortOrder.SSID, frozenOrder = null).map { it.displaySsid },
        )
    }

    @Test
    fun `filtered lists are computed when the state is built`() {
        val result =
            WifiScanResult(
                accessPoints = listOf(ap("A", WifiBand.BAND_2_4GHZ, -40), ap("B", WifiBand.BAND_5GHZ, -60)),
                channels = emptyList(),
                connectedNetwork = null,
                scanTimestampMs = 0L,
                isWifiEnabled = true,
            )

        val state = WifiScanUiState.Success(result = result, bandFilter = WifiBand.BAND_5GHZ)

        assertEquals(listOf("B"), state.filteredAccessPoints.map { it.ssid })
        assertEquals(listOf("B"), state.filteredNetworks.map { it.displaySsid })
    }

    private fun ap(
        ssid: String,
        band: WifiBand,
        rssi: Int,
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = "AA:BB:CC:DD:EE:%02X".format(ssid.first().code),
        rssi = rssi,
        frequency = if (band == WifiBand.BAND_2_4GHZ) 2412 else 5180,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = if (band == WifiBand.BAND_2_4GHZ) 1 else 36,
        band = band,
        standard = WifiStandard.WIFI_5,
        security = WifiSecurity.WPA2,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 0,
        centerFrequency1 = 0,
        timestampUs = 0,
    )

    private fun network(
        ssid: String,
        vararg aps: WifiAccessPoint,
    ) = WifiNetwork(ssid = ssid, security = WifiSecurity.WPA2, accessPoints = aps.toList())
}
