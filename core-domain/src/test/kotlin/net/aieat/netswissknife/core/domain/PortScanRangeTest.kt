package net.aieat.netswissknife.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PortScanRangeTest {
    @Test
    fun `custom range cap is 10 000 ports`() {
        assertEquals(10_000, PortScanRange.MAX_PORTS)
    }

    @Test
    fun `range of exactly the cap is accepted`() {
        assertNull(PortScanRange.problemOf(1, 10_000))
    }

    @Test
    fun `range one port over the cap is too large`() {
        assertEquals(PortScanRange.Problem.TOO_LARGE, PortScanRange.problemOf(1, 10_001))
    }

    @Test
    fun `20000 ports is too large`() {
        assertEquals(PortScanRange.Problem.TOO_LARGE, PortScanRange.problemOf(1, 20_000))
    }

    @Test
    fun `inverted range is reported before size`() {
        assertEquals(PortScanRange.Problem.INVERTED, PortScanRange.problemOf(500, 100))
    }

    @Test
    fun `ports outside 1 to 65535 are out of range`() {
        assertEquals(PortScanRange.Problem.PORT_OUT_OF_RANGE, PortScanRange.problemOf(0, 80))
        assertEquals(PortScanRange.Problem.PORT_OUT_OF_RANGE, PortScanRange.problemOf(1, 65_536))
    }

    @Test
    fun `slider moving the end thumb past the cap stops at the cap`() {
        // Start stays at 1 and the end thumb is dragged to 26 825.
        assertEquals(1 to 10_000, PortScanRange.clampSliderChange(1, 1, 26_825))
    }

    @Test
    fun `slider moving the start thumb far below the end stops at the cap`() {
        // End stays at 20 000 and the start thumb is dragged down to 1.
        assertEquals(10_001 to 20_000, PortScanRange.clampSliderChange(15_000, 1, 20_000))
    }

    @Test
    fun `slider change inside the cap is passed through unchanged`() {
        assertEquals(100 to 900, PortScanRange.clampSliderChange(1, 100, 900))
    }
}
