package net.aieat.netswissknife.app.ui.screens.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingIntervalSnapTest {
    @Test
    fun `an off-step interval snaps to the nearest 100 ms`() {
        assertEquals(7600, snapPingIntervalMs(7624))
        assertEquals(7700, snapPingIntervalMs(7650))
    }

    @Test
    fun `values on a step are unchanged`() {
        assertEquals(100, snapPingIntervalMs(100))
        assertEquals(1000, snapPingIntervalMs(1000))
    }

    @Test
    fun `values are clamped to the slider range`() {
        assertEquals(100, snapPingIntervalMs(10))
        assertEquals(10_000, snapPingIntervalMs(9_999))
        assertEquals(10_000, snapPingIntervalMs(20_000))
    }
}
