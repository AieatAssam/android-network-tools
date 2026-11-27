package net.aieat.netswissknife.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortScanDeadlineBudgetTest {
    @Test
    fun `budget uses waves at effective concurrency with banner time inside per-port timeout`() {
        val estimate =
            PortScanDeadlineBudget.estimate(
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
        val parallel =
            PortScanDeadlineBudget.estimate(
                portCount = 10_000,
                timeoutMs = 30_000,
                requestedConcurrency = 500,
            )
        val serial =
            PortScanDeadlineBudget.estimate(
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
        val overCeiling =
            PortScanParams(
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

    @Test
    fun `plan flags well-known scan at 2 concurrent probes with 10 s timeout as over the limit`() {
        val plan =
            PortScanDeadlineBudget.plan(
                preset = PortScanPreset.WELL_KNOWN,
                startPort = null,
                endPort = null,
                timeoutMs = 10_000,
                concurrency = 2,
            )

        assertEquals(1_024, plan?.portCount)
        assertTrue(plan?.exceedsLimit == true)
        assertEquals(86L, plan?.worstCaseMinutes)
    }

    @Test
    fun `plan accepts the same preset once concurrency rises`() {
        val plan =
            PortScanDeadlineBudget.plan(
                preset = PortScanPreset.WELL_KNOWN,
                startPort = null,
                endPort = null,
                timeoutMs = 1_500,
                concurrency = 71,
            )

        assertFalse(plan?.exceedsLimit == true)
        assertEquals(32_500L, plan?.worstCaseMillis)
    }

    @Test
    fun `plan counts a custom range inclusively`() {
        val plan = PortScanDeadlineBudget.plan(PortScanPreset.CUSTOM, 20, 100, 2_000, 50)

        assertEquals(81, plan?.portCount)
    }

    @Test
    fun `plan is null while a custom range cannot be counted`() {
        assertNull(PortScanDeadlineBudget.plan(PortScanPreset.CUSTOM, null, 100, 2_000, 50))
        assertNull(PortScanDeadlineBudget.plan(PortScanPreset.CUSTOM, 100, 20, 2_000, 50))
        assertNull(PortScanDeadlineBudget.plan(PortScanPreset.CUSTOM, 1, 20_000, 2_000, 50))
        assertNull(PortScanDeadlineBudget.plan(PortScanPreset.CUSTOM, 1, 100, 0, 50))
    }

    @Test
    fun `worst case of exactly the limit is allowed and one millisecond over is not`() {
        val atLimit = PortScanPlan(portCount = 1, worstCaseMillis = PortScanDeadlineBudget.MAX_OPERATION_TIMEOUT_MILLIS)
        val overLimit =
            PortScanPlan(portCount = 1, worstCaseMillis = PortScanDeadlineBudget.MAX_OPERATION_TIMEOUT_MILLIS + 1)

        assertFalse(atLimit.exceedsLimit)
        assertTrue(overLimit.exceedsLimit)
        assertEquals(15L, atLimit.worstCaseMinutes)
        assertEquals(16L, overLimit.worstCaseMinutes)
    }
}
