package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.MonotonicClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TracerouteOperationTest {
    @Test
    fun `budget follows requested hops and timeout with a fifteen minute ceiling`() {
        assertEquals(800_000L, TracerouteOperation.requestedTimeoutMillis(50, 10_000))
        assertEquals(900_000L, TracerouteOperation.requestedTimeoutMillis(25, 30_000))
        assertNull(TracerouteOperation.requestedTimeoutMillis(64, 10_000))
        assertNull(TracerouteOperation.requestedTimeoutMillis(64, 15_000))
        assertNull(TracerouteOperation.requestedTimeoutMillis(0, 1_000))
    }

    @Test
    fun `session deadline counts down from request budget using monotonic time`() {
        var nowNanos = 12_000_000L
        val clock = MonotonicClock { nowNanos }

        val session = TracerouteOperation.newSession(50, 10_000, clock)

        assertEquals(800_000L, session.budget.remainingTimeoutMillis())
        nowNanos += 125_000_000L
        assertEquals(799_875L, session.budget.remainingTimeoutMillis())
        nowNanos += 799_875_000_000L
        assertTrue(session.budget.remainingTimeoutMillis() <= 0L)
    }
}
