package net.aieat.netswissknife.core.network.testkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FakeClockTest {
    @Test
    fun `advances deterministically and supports direct setting`() {
        val clock = FakeClock(10L)

        assertEquals(15L, clock.advanceBy(5L))
        assertEquals(15L, clock.nowNanos())

        clock.setNow(3L)
        assertEquals(3L, clock.nowNanos())
    }

    @Test
    fun `advance wraps like a monotonic nano time source`() {
        val clock = FakeClock(Long.MAX_VALUE)

        assertEquals(Long.MIN_VALUE, clock.advanceBy(1L))
    }

    @Test
    fun `negative advance is rejected`() {
        val clock = FakeClock(1L)

        assertThrows(IllegalArgumentException::class.java) { clock.advanceBy(-1L) }
        assertEquals(1L, clock.nowNanos())
    }
}
