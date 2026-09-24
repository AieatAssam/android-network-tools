package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanScanOperationBudgetTest {
    @Test
    fun `estimates all bounded sequential phases across worker waves`() {
        val oneWave = LanScanOperationBudget.estimate(50, 1_000, 50)
        val twoWaves = LanScanOperationBudget.estimate(51, 1_000, 50)

        assertEquals(19_250L, oneWave.timeoutMillis)
        assertEquals(37_500L, twoWaves.timeoutMillis)
        assertTrue(twoWaves.timeoutMillis > oneWave.timeoutMillis)
    }

    @Test
    fun `request estimate sizes ordinary work and rejects work above the hard ceiling`() {
        val ordinary = LanScanRequest("192.168.1.0/24", timeoutMs = 1_000, concurrency = 50)
        val oversized = LanScanRequest("10.0.0.0/16", timeoutMs = 10_000, concurrency = 1)

        assertEquals(
            LanScanOperationBudget.estimate(ordinary).timeoutMillis,
            LanScanOperationBudget.sessionTimeoutMillis(ordinary),
        )
        assertTrue(LanScanOperationBudget.estimate(oversized).exceedsHardCeiling)
        assertTrue(runCatching { LanScanOperationBudget.sessionTimeoutMillis(oversized) }.isFailure)
    }

    @Test
    fun `custom presence port count participates in the sequential tcp estimate`() {
        val largePortList = LanScanRequest(
            subnet = "192.168.1.0/30",
            timeoutMs = 10_000,
            concurrency = 1,
            presencePorts = List(10_000) { 80 },
            enableNameProbes = false,
        )

        assertTrue(LanScanOperationBudget.estimate(largePortList).exceedsHardCeiling)
    }
}
