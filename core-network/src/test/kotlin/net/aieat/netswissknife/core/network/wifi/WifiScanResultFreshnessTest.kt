package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiScanResultFreshnessTest {

    @Test
    fun `new result fields preserve the existing constructor defaults`() {
        val result = WifiScanResult(
            accessPoints = emptyList(),
            channels = emptyList(),
            connectedNetwork = null,
            scanTimestampMs = 1_000L,
            isWifiEnabled = true
        )

        assertTrue(result.isFresh)
        assertEquals(WifiScanRefreshStatus.NOT_REQUESTED, result.refreshStatus)
        assertTrue(result.locationEnabled)
        assertEquals(null, result.scanAgeMs)
    }

    @Test
    fun `freshness computes age from elapsed realtime microseconds`() {
        val freshness = WifiScanFreshness.compute(
            newestTimestampUs = 100_000_000L,
            nowElapsedMs = 100_042L
        )

        assertEquals(42L, freshness.ageMs)
        assertTrue(freshness.isFresh)
    }

    @Test
    fun `freshness expires after fifteen seconds`() {
        val freshness = WifiScanFreshness.compute(
            newestTimestampUs = 100_000_000L,
            nowElapsedMs = 115_001L
        )

        assertEquals(15_001L, freshness.ageMs)
        assertFalse(freshness.isFresh)
    }

    @Test
    fun `missing timestamp has unknown age and is not fresh`() {
        val freshness = WifiScanFreshness.compute(
            newestTimestampUs = null,
            nowElapsedMs = 100L
        )

        assertEquals(null, freshness.ageMs)
        assertFalse(freshness.isFresh)
    }
}
