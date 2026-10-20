package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.MonotonicClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TracerouteOperationTest {
    @Test
    fun `budget follows hop timeout and sequential probe waves under a twenty minute ceiling`() {
        assertEquals(1_029_000L, TracerouteOperation.requestedTimeoutMillis(64, 10_000))
        assertEquals(1_085_000L, TracerouteOperation.requestedTimeoutMillis(30, 30_000))
        assertEquals(325_000L, TracerouteOperation.requestedTimeoutMillis(20, 10_000, 5, 5))
        assertEquals(1_125_000L, TracerouteOperation.requestedTimeoutMillis(20, 10_000, 5, 1))
        assertNull(TracerouteOperation.requestedTimeoutMillis(64, 20_000, 5, 1))
        assertNull(TracerouteOperation.requestedTimeoutMillis(0, 1_000))
    }

    @Test
    fun `session deadline counts down from request budget using monotonic time`() {
        var nowNanos = 12_000_000L
        val clock = MonotonicClock { nowNanos }

        val session = TracerouteOperation.newSession(64, 10_000, clock = clock)

        assertEquals(1_029_000L, session.budget.remainingTimeoutMillis())
        nowNanos += 125_000_000L
        assertEquals(1_028_875L, session.budget.remainingTimeoutMillis())
        nowNanos += 1_028_875_000_000L
        assertTrue(session.budget.remainingTimeoutMillis() <= 0L)
    }

    @Test
    fun `session stores caller concurrency used to size request budget`() {
        val session = TracerouteOperation.newSession(20, 10_000, 5, maxConcurrentProbes = 1)

        assertEquals(1, session.budget.maxConcurrentProbes)
        assertEquals(1_125_000L, session.budget.deadline.timeoutNanos / 1_000_000L)
    }
}
