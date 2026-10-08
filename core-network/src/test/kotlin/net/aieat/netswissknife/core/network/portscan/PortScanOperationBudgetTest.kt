package net.aieat.netswissknife.core.network.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PortScanOperationBudgetTest {
    @Test
    fun `banner allowance is not counted twice when it shares each port timeout`() {
        val estimate = PortScanOperationBudget.estimate(
            portCount = 400,
            timeoutMs = 2_000,
            requestedConcurrency = 1,
        )

        assertEquals(810_000L, estimate.timeoutMillis)
        assertFalse(estimate.exceedsHardCeiling)
        assertEquals(estimate.timeoutMillis, PortScanOperationBudget.sessionTimeoutMillis(400, 2_000, 1))
    }
}
