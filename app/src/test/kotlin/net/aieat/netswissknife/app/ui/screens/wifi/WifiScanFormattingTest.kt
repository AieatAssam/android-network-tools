package net.aieat.netswissknife.app.ui.screens.wifi

import net.aieat.netswissknife.core.network.wifi.SignalLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiScanFormattingTest {
    @Test
    fun `age is unknown when the platform gave no timestamp`() {
        assertNull(wifiScanAgeSeconds(scanAgeMs = null, cacheReadElapsedRealtimeMs = 1_000L, nowElapsedRealtimeMs = 9_000L))
    }

    @Test
    fun `age advances with the time since the cache was read`() {
        assertEquals(
            12L,
            wifiScanAgeSeconds(scanAgeMs = 5_000L, cacheReadElapsedRealtimeMs = 100_000L, nowElapsedRealtimeMs = 107_000L),
        )
    }

    @Test
    fun `age keeps growing on later ticks without a new scan`() {
        val first = wifiScanAgeSeconds(2_000L, 100_000L, 101_000L)
        val later = wifiScanAgeSeconds(2_000L, 100_000L, 110_000L)

        assertEquals(3L, first)
        assertEquals(12L, later)
    }

    @Test
    fun `age uses the reported value when the cache read time is missing`() {
        assertEquals(7L, wifiScanAgeSeconds(scanAgeMs = 7_400L, cacheReadElapsedRealtimeMs = 0L, nowElapsedRealtimeMs = 900_000L))
    }

    @Test
    fun `a clock that moved backwards does not make the age negative`() {
        assertEquals(5L, wifiScanAgeSeconds(scanAgeMs = 5_000L, cacheReadElapsedRealtimeMs = 100_000L, nowElapsedRealtimeMs = 90_000L))
    }

    @Test
    fun `every signal level has its own label`() {
        val labels = SignalLevel.entries.map { signalLevelLabelRes(it) }

        assertEquals(SignalLevel.entries.size, labels.toSet().size)
    }
}
