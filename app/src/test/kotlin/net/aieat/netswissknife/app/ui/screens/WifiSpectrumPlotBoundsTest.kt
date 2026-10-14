package net.aieat.netswissknife.app.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WifiSpectrumPlotBoundsTest {
    @Test
    fun `plot bounds follow the mirrored RTL coordinates`() {
        assertEquals(36f..400f, wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = false))
        assertEquals(0f..364f, wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = true))
    }

    @Test
    fun `ssid labels stay inside the plot bounds at both edges`() {
        val ltrBounds = wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = false)
        assertEquals(36f, wifiSpectrumLabelLeft(centerXPx = 40f, labelWidthPx = 60f, plotBounds = ltrBounds), 0.001f)
        assertEquals(340f, wifiSpectrumLabelLeft(centerXPx = 398f, labelWidthPx = 60f, plotBounds = ltrBounds), 0.001f)

        val rtlBounds = wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = true)
        assertEquals(0f, wifiSpectrumLabelLeft(centerXPx = 4f, labelWidthPx = 60f, plotBounds = rtlBounds), 0.001f)
        assertEquals(304f, wifiSpectrumLabelLeft(centerXPx = 362f, labelWidthPx = 60f, plotBounds = rtlBounds), 0.001f)
    }

    @Test
    fun `ssid labels remain centered away from chart edges`() {
        val bounds = wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = false)
        assertEquals(170f, wifiSpectrumLabelLeft(centerXPx = 200f, labelWidthPx = 60f, plotBounds = bounds), 0.001f)
    }
}
