package net.aieat.netswissknife.app.ui.screens.speedtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GaugeScaleTest {
    @Test
    fun `zero and tiny speeds use the smallest scale`() {
        assertEquals(10, gaugeScaleMaxMbps(0.0))
        assertEquals(10, gaugeScaleMaxMbps(10.0))
    }

    @Test
    fun `an exact step fits its own scale`() {
        assertEquals(100, gaugeScaleMaxMbps(100.0))
    }

    @Test
    fun `a value just above a step moves to the next scale`() {
        assertEquals(250, gaugeScaleMaxMbps(100.1))
    }

    @Test
    fun `values above the top step stay on the top scale`() {
        assertEquals(10_000, gaugeScaleMaxMbps(9_999.9))
        assertEquals(10_000, gaugeScaleMaxMbps(10_000.0))
        assertEquals(10_000, gaugeScaleMaxMbps(50_000.0))
    }
}
