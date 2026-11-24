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

    @Test
    fun `concurrency keeps every value up to 16`() {
        assertEquals(1, snapConcurrency(0f))
        assertEquals(7, snapConcurrency(7f))
        assertEquals(16, snapConcurrency(16f))
    }

    @Test
    fun `concurrency above 16 snaps to multiples of 8`() {
        assertEquals(24, snapConcurrency(20f))
        assertEquals(104, snapConcurrency(100f))
    }

    @Test
    fun `concurrency is capped at 496`() {
        assertEquals(496, snapConcurrency(1_000f))
    }
}
