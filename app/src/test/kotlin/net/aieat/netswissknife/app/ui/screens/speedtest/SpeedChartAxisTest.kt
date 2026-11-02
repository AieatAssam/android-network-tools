package net.aieat.netswissknife.app.ui.screens.speedtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Speed chart axis")
class SpeedChartAxisTest {
    @Test
    fun `a 29 Mbps peak gets round ticks 0, 10, 20, 30`() {
        assertEquals(10.0, niceAxisStep(29.4), 1e-9)
    }

    @Test
    fun `a 13 point 8 Mbps peak gets round ticks 0, 5, 10, 15`() {
        assertEquals(5.0, niceAxisStep(13.8), 1e-9)
    }

    @Test
    fun `a sub-unit peak still gives a step that is a round 1, 2 or 5`() {
        assertEquals(0.5, niceAxisStep(0.9), 1e-9)
    }
}
