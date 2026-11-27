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
}
