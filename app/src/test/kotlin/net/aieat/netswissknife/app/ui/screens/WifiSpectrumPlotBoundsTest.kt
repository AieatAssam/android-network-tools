package net.aieat.netswissknife.app.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WifiSpectrumPlotBoundsTest {
    @Test
    fun `plot bounds follow the mirrored RTL coordinates`() {
        assertEquals(36f..400f, wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = false))
        assertEquals(0f..364f, wifiSpectrumPlotBounds(widthPx = 400f, leftPadPx = 36f, isRtl = true))
    }
}
