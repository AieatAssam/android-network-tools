package net.aieat.netswissknife.core.network.mdns

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.testkit.FakeClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MdnsOperationTest {
    @Test
    fun `session uses local network single worker safety budget and receive cap`() {
        val session = MdnsOperation.newSession(FakeClock(17L))

        assertEquals(OperationRequirement.LOCAL_NETWORK, session.budget.requirement)
        assertEquals(120_000_000_000L, session.budget.deadline.timeoutNanos)
        assertEquals(1, session.budget.maxConcurrentProbes)
        assertEquals(MdnsOperation.RECEIVE_BUFFER_SIZE_BYTES.toLong(), session.budget.maxResponseBytes)
    }

    @Test
    fun `requested scan duration clamps to one through one hundred twenty seconds`() {
        assertEquals(1L, MdnsOperation.clampScanDuration(Long.MIN_VALUE))
        assertEquals(1L, MdnsOperation.clampScanDuration(0L))
        assertEquals(8_000L, MdnsOperation.clampScanDuration(8_000L))
        assertEquals(120_000L, MdnsOperation.clampScanDuration(Long.MAX_VALUE))
        assertEquals(OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS, MdnsOperation.MAX_SCAN_DURATION_MILLIS)
    }

    @Test
    fun `session deadline uses clamped requested scan window`() {
        val session = MdnsOperation.newSession(0L, FakeClock(17L))

        assertEquals(1_000_000L, session.budget.deadline.timeoutNanos)
    }
}
