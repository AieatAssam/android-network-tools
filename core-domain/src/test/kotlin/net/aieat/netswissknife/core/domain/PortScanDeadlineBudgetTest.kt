package net.aieat.netswissknife.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortScanDeadlineBudgetTest {
    @Test
    fun `budget uses waves at effective concurrency with banner time inside per-port timeout`() {
        val estimate = PortScanDeadlineBudget.estimate(
            portCount = 101,
            timeoutMs = 2_000,
            requestedConcurrency = 50,
            sessionConcurrency = 40,
        )

        assertEquals(40, estimate.effectiveConcurrency)
        assertEquals(10_000L + 3 * 2_000L, estimate.timeoutMillis)
        assertFalse(estimate.exceedsHardCeiling)
    }

    @Test
    fun `maximum custom scan fits at maximum concurrency but exceeds ceiling when serialized`() {
        val parallel = PortScanDeadlineBudget.estimate(
            portCount = 10_000,
            timeoutMs = 30_000,
            requestedConcurrency = 500,
        )
        val serial = PortScanDeadlineBudget.estimate(
            portCount = 10_000,
            timeoutMs = 30_000,
            requestedConcurrency = 1,
        )

        assertEquals(610_000L, parallel.timeoutMillis)
        assertFalse(parallel.exceedsHardCeiling)
        assertEquals(300_010_000L, serial.timeoutMillis)
        assertTrue(serial.exceedsHardCeiling)
        assertEquals(900_000L, PortScanDeadlineBudget.MAX_OPERATION_TIMEOUT_MILLIS)
    }

    @Test
    fun `session timeout stays bounded for malformed or over-ceiling requests`() {
        val overCeiling = PortScanParams(
            host = "example.com",
            preset = PortScanPreset.CUSTOM,
            startPort = 1,
            endPort = 10_000,
            timeoutMs = 30_000,
            concurrency = 1,
        )

        assertEquals(
            PortScanDeadlineBudget.MAX_OPERATION_TIMEOUT_MILLIS,
            PortScanDeadlineBudget.sessionTimeoutMillis(overCeiling),
        )
    }
}
