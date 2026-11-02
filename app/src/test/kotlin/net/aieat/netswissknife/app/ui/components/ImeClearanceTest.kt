package net.aieat.netswissknife.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImeClearanceTest {
    // Phone at density 3.0: the bottom bar is 80dp = 240px. The 68px navigation bar inset sits
    // below the bar, so the reserved band is 240 + 68 = 308px on this device.
    private val bar = 308

    @Test
    fun `no keyboard adds no padding`() {
        assertEquals(0, imeOverlapAboveBottomBar(imeHeightPx = 0, reservedBelowContentPx = bar))
    }

    @Test
    fun `keyboard shorter than the reserved band adds no padding`() {
        assertEquals(0, imeOverlapAboveBottomBar(imeHeightPx = 100, reservedBelowContentPx = bar))
    }

    @Test
    fun `keyboard taller than the bar pads only by the part above the bar`() {
        // The content slot already reserves the 308px band; the keyboard covers 777px.
        assertEquals(469, imeOverlapAboveBottomBar(imeHeightPx = 777, reservedBelowContentPx = bar))
    }

    @Test
    fun `keyboard equal to the bar adds no padding`() {
        assertEquals(0, imeOverlapAboveBottomBar(imeHeightPx = bar, reservedBelowContentPx = bar))
    }
}
