package net.aieat.netswissknife.core.network.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PingOperationTest {
    @Test
    fun `finite budget covers the requested packet count and fallback probe`() {
        val session = PingOperation.newSession(
            PingRequest(
                host = "example.com",
                count = 100,
                timeoutMs = 30_000,
                intervalMs = 10_000,
            )
        )

        assertTrue(session.budget.hasDeadline)
        assertEquals(8_070_000_000_000L, session.budget.deadline.timeoutNanos)
        assertEquals(1, session.budget.maxConcurrentProbes)
    }

    @Test
    fun `continuous budget remains explicit cancellation with bounded concurrency`() {
        val session = PingOperation.newSession(
            PingRequest(host = "example.com", count = 0, timeoutMs = 2_000)
        )

        assertFalse(session.budget.hasDeadline)
        assertEquals(1, session.budget.maxConcurrentProbes)
    }

    @Test
    fun `extreme invalid request values saturate instead of overflowing the deadline`() {
        val session = PingOperation.newSession(
            PingRequest(
                host = "example.com",
                count = Int.MAX_VALUE,
                timeoutMs = Int.MAX_VALUE,
                intervalMs = Int.MAX_VALUE,
            )
        )

        assertTrue(session.budget.hasDeadline)
        assertTrue(session.budget.deadline.timeoutNanos > 0)
    }
}
