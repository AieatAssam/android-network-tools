package net.aieat.netswissknife.core.network.operation

import net.aieat.netswissknife.core.network.MonotonicClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationBudgetTest {
    @Test
    fun `deadline uses monotonic elapsed time and expires at the exact boundary`() {
        val clock = FakeClock(Long.MAX_VALUE - 50)
        val budget = OperationBudget.start(
            requirement = OperationRequirement.LOCAL_NETWORK,
            timeoutMillis = 100,
            maxConcurrentProbes = 2,
            clock = clock,
        )

        assertEquals(100_000_000L, budget.remainingNanos())
        assertEquals(100L, budget.remainingTimeoutMillis())

        clock.advanceBy(99_000_000L)
        assertEquals(1_000_000L, budget.remainingNanos())
        assertEquals(1L, budget.remainingTimeoutMillis())

        clock.advanceBy(1_000_000L)
        assertEquals(0L, budget.remainingNanos())
        val deadlineFailure = assertThrows(OperationDeadlineExceededException::class.java) {
            budget.throwIfExpired()
        }
        assertFalse(
            java.util.concurrent.CancellationException::class.java.isAssignableFrom(deadlineFailure.javaClass)
        )

        // Signed nanoTime wrap must not make an expired deadline live again.
        clock.advanceBy(1)
        assertEquals(0L, budget.remainingNanos())
    }

    @Test
    fun `clock moving backwards cannot restore time already consumed`() {
        val clock = FakeClock(1_000_000_000L)
        val budget = OperationBudget.start(timeoutMillis = 100, clock = clock)

        clock.advanceBy(60_000_000L)
        assertEquals(40_000_000L, budget.remainingNanos())

        clock.setNow(900_000_000L)
        assertEquals(40_000_000L, budget.remainingNanos())
    }

    @Test
    fun `budget carries one operation id requirement and shared limits`() {
        val budget = OperationBudget.start(
            operationId = OperationId.from("run-17"),
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = 500,
            maxConcurrentProbes = 8,
            maxResponseBytes = 4_096,
            maxExportBytes = 8_192,
            clock = FakeClock(0),
        )

        assertEquals(OperationId.from("run-17"), budget.operationId)
        assertEquals(OperationRequirement.INTERNET, budget.requirement)
        assertEquals(8, budget.maxConcurrentProbes)
        assertEquals(4_096L, budget.maxResponseBytes)
        assertEquals(8_192L, budget.maxExportBytes)
        assertEquals(500_000_000L, budget.deadline.remainingNanos())
    }

    @Test
    fun `invalid identifiers timeouts workers and byte limits are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { OperationId.from(" ") }
        assertThrows(IllegalArgumentException::class.java) { OperationId.from(" padded ") }
        assertThrows(IllegalArgumentException::class.java) { OperationId.from("x".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) {
            OperationBudget.start(timeoutMillis = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationBudget.start(timeoutMillis = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationBudget.start(maxConcurrentProbes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationBudget.start(maxResponseBytes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationBudget.start(maxExportBytes = -1)
        }
    }

    @Test
    fun `default budget uses finite configured limits and distinct run ids`() {
        val first = OperationBudget.start(clock = FakeClock(0))
        val second = OperationBudget.start(clock = FakeClock(0))

        assertNotEquals(first.operationId, second.operationId)
        assertEquals(OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS * 1_000_000L, first.deadline.timeoutNanos)
        assertEquals(OperationBudget.DEFAULT_MAX_CONCURRENT_PROBES, first.maxConcurrentProbes)
        assertEquals(OperationBudget.DEFAULT_MAX_RESPONSE_BYTES, first.maxResponseBytes)
        assertEquals(OperationBudget.DEFAULT_MAX_EXPORT_BYTES, first.maxExportBytes)
        assertTrue(first.remainingNanos() > 0)
    }

    @Test
    fun `unbounded budget keeps resource limits without expiring`() {
        val clock = FakeClock(0)
        val budget = OperationBudget.startUnbounded(
            requirement = OperationRequirement.ANY_NETWORK,
            maxConcurrentProbes = 1,
            clock = clock,
        )

        assertFalse(budget.hasDeadline)
        assertEquals(Long.MAX_VALUE, budget.remainingNanos())
        assertEquals(Long.MAX_VALUE, budget.remainingTimeoutMillis())
        assertEquals(1, budget.maxConcurrentProbes)

        clock.advanceBy(Long.MAX_VALUE / 2)
        budget.throwIfExpired()
        assertEquals(Long.MAX_VALUE, budget.remainingNanos())
    }

    private class FakeClock(startAtNanos: Long) : MonotonicClock {
        private var now = startAtNanos

        override fun nowNanos(): Long = now

        fun advanceBy(deltaNanos: Long) {
            now += deltaNanos
        }

        fun setNow(value: Long) {
            now = value
        }
    }
}
