package net.aieat.netswissknife.app.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PortTimeoutRoundingTest {
    @Test
    fun `slider values below the minimum clamp to 200 ms`() {
        assertEquals(200, roundTimeoutMs(0f))
        assertEquals(200, roundTimeoutMs(149f))
    }

    @Test
    fun `slider values snap to the nearest 100 ms step`() {
        assertEquals(200, roundTimeoutMs(151f))
        assertEquals(300, roundTimeoutMs(250f))
        assertEquals(4_700, roundTimeoutMs(4_712f))
    }

    @Test
    fun `slider values above the maximum clamp to 10000 ms`() {
        assertEquals(10_000, roundTimeoutMs(9_999.9f))
        assertEquals(10_000, roundTimeoutMs(10_000f))
        assertEquals(10_000, roundTimeoutMs(99_999f))
    }
}
