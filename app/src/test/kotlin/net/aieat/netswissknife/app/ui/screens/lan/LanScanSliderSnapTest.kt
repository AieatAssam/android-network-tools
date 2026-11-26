package net.aieat.netswissknife.app.ui.screens.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanScanSliderSnapTest {
    @Test
    fun `timeout snaps to the nearest 100 ms`() {
        assertEquals(1200, snapTimeoutMs(1240f))
        assertEquals(1300, snapTimeoutMs(1290f))
    }

    @Test
    fun `timeout stays inside the 100 to 10000 ms range`() {
        assertEquals(100, snapTimeoutMs(0f))
        assertEquals(10_000, snapTimeoutMs(20_000f))
    }
}
