package net.aieat.netswissknife.app.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PortConcurrencyStepsTest {
    @Test
    fun `steps run from 1 to 500 in ascending order`() {
        assertEquals(1, PORT_CONCURRENCY_STEPS.first())
        assertEquals(500, PORT_CONCURRENCY_STEPS.last())
        assertEquals(PORT_CONCURRENCY_STEPS.sorted(), PORT_CONCURRENCY_STEPS)
    }

    @Test
    fun `a value on a step maps to that step`() {
        assertEquals(PORT_CONCURRENCY_STEPS.indexOf(50), concurrencyIndexOf(50))
        assertEquals(PORT_CONCURRENCY_STEPS.indexOf(500), concurrencyIndexOf(500))
    }

    @Test
    fun `a stored value between steps is shown at the nearest step`() {
        assertEquals(PORT_CONCURRENCY_STEPS.indexOf(25), concurrencyIndexOf(18))
        assertEquals(PORT_CONCURRENCY_STEPS.indexOf(10), concurrencyIndexOf(12))
    }

    @Test
    fun `values beyond either end map to the first or last step`() {
        assertEquals(0, concurrencyIndexOf(0))
        assertEquals(PORT_CONCURRENCY_STEPS.lastIndex, concurrencyIndexOf(493))
    }
}
