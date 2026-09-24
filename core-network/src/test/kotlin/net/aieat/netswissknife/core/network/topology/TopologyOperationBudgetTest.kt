package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyOperationBudgetTest {
    @Test
    fun `estimates timeout from per-device request waves retries and a bounded node count`() {
        val request = TopologyParams(
            targetIp = "192.168.1.1",
            maxHops = 3,
            timeoutMs = 3_000,
            retries = 1,
        )

        // 20 bounded SNMP request windows × 2 attempts × 3 seconds; branching is capped to 3 nodes.
        val estimate = TopologyOperationBudget.estimate(request)
        assertEquals(3, estimate?.maxNodes)
        assertEquals(451_000L, estimate?.timeoutMillis)
        assertEquals(451_000L, TopologyOperationBudget.estimatedTimeoutMillis(request))
        assertEquals(451_000L, TopologyOperationBudget.timeoutMillisOrNull(request))
    }

    @Test
    fun `small request gets a smaller budget and requests above hard ceiling are rejected`() {
        val small = TopologyParams(
            targetIp = "192.168.1.1",
            maxHops = 1,
            timeoutMs = 500,
            retries = 0,
        )
        val oversized = TopologyParams(
            targetIp = "192.168.1.1",
            maxHops = 10,
            timeoutMs = 30_000,
            retries = 5,
        )

        val smallEstimate = TopologyOperationBudget.estimate(small)
        assertEquals(47, smallEstimate?.maxNodes)
        assertEquals(588_500L, smallEstimate?.timeoutMillis)
        assertTrue(TopologyOperationBudget.estimatedTimeoutMillis(oversized) > TopologyOperationBudget.HARD_CEILING_MILLIS)
        assertNull(TopologyOperationBudget.timeoutMillisOrNull(oversized))
    }

    @Test
    fun `branching graph maximum is constrained by the derived node allowance`() {
        val request = TopologyParams(
            targetIp = "192.168.1.1",
            maxHops = 1,
            timeoutMs = 1_000,
            retries = 0,
        )

        val estimate = TopologyOperationBudget.estimate(request)

        assertEquals(23, estimate?.maxNodes)
        assertTrue(checkNotNull(estimate).timeoutMillis <= TopologyOperationBudget.HARD_CEILING_MILLIS)
    }

    @Test
    fun `low session concurrency reduces fanout allowance to preserve deadline`() {
        val request = TopologyParams(
            targetIp = "192.168.1.1",
            timeoutMs = 3_000,
            retries = 1,
        )

        val default = TopologyOperationBudget.estimate(request)
        val serialSession = TopologyOperationBudget.estimate(request, sessionConcurrency = 1)

        assertEquals(3, default?.maxNodes)
        assertEquals(1, serialSession?.maxNodes)
        assertEquals(331_000L, serialSession?.timeoutMillis)
    }
}
