package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.screens.wifi.SpectrumLabelBox
import net.aieat.netswissknife.app.ui.screens.wifi.placeSpectrumLabels
import net.aieat.netswissknife.app.ui.screens.wifi.spectrumLabelCandidates
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WifiSpectrumLabelLayoutTest {
    private val bounds = 36f..400f
    private val height = 12f

    @Test
    fun `labels that do not collide stay on the first tier above their peak`() {
        val placements =
            placeSpectrumLabels(
                listOf(box(0, centerX = 100f, peakY = 150f), box(1, centerX = 300f, peakY = 150f)),
                bounds,
            )

        assertEquals(listOf(0, 1), placements.map { it.id })
        assertEquals(1, placements.map { it.top }.distinct().size)
    }

    @Test
    fun `colliding labels stack upward instead of drawing over each other`() {
        val placements =
            placeSpectrumLabels(
                listOf(box(0, centerX = 100f, peakY = 150f), box(1, centerX = 104f, peakY = 150f)),
                bounds,
            )

        assertEquals(2, placements.size)
        val top0 = placements.first { it.id == 0 }.top
        val top1 = placements.first { it.id == 1 }.top
        assertTrue(abs(top0 - top1) >= height, "labels overlap: $top0 and $top1")
    }

    @Test
    fun `labels that run out of tiers are omitted rather than drawn over others`() {
        val placements =
            placeSpectrumLabels(
                List(4) { box(it, centerX = 100f, peakY = 150f) },
                bounds,
                maxTiers = 3,
            )

        assertEquals(listOf(0, 1, 2), placements.map { it.id })
    }

    @Test
    fun `labels near the top of the plot are clamped inside it`() {
        val placements = placeSpectrumLabels(listOf(box(0, centerX = 100f, peakY = 5f)), bounds)

        assertEquals(0f, placements.single().top)
    }

    @Test
    fun `label candidates are one per network, strongest first`() {
        val aps =
            listOf(
                accessPoint("home-far", ssid = "Home", bssid = "00:00:00:00:00:01", rssi = -70),
                accessPoint("guest", ssid = "Guest", bssid = "00:00:00:00:00:02", rssi = -60),
                accessPoint("home-near", ssid = "Home", bssid = "00:00:00:00:00:03", rssi = -40),
                accessPoint("hidden-a", ssid = "", bssid = "00:00:00:00:00:04", rssi = -55),
                accessPoint("hidden-b", ssid = "", bssid = "00:00:00:00:00:05", rssi = -45),
            )

        val candidates = spectrumLabelCandidates(aps).map { it.bssid }

        assertEquals(
            listOf("00:00:00:00:00:03", "00:00:00:00:00:05", "00:00:00:00:00:04", "00:00:00:00:00:02"),
            candidates,
        )
    }

    private fun box(
        id: Int,
        centerX: Float,
        peakY: Float,
    ) = SpectrumLabelBox(id = id, centerX = centerX, width = 60f, height = height, peakY = peakY)

    private fun accessPoint(
        tag: String,
        ssid: String,
        bssid: String,
        rssi: Int,
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = 2412,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 1,
        band = WifiBand.BAND_2_4GHZ,
        standard = WifiStandard.WIFI_4,
        security = WifiSecurity.WPA2,
        isConnected = false,
        vendor = tag,
        centerFrequency0 = 2412,
        centerFrequency1 = 0,
        timestampUs = 0,
    )
}
