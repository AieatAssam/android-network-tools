package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HopResultProbeStatsTest {
    @Test
    fun `probe stats ignore timeouts and retain successful RTTs`() {
        val hop = HopResult(
            hopNumber = 1,
            ip = "192.0.2.1",
            hostname = null,
            rtTimeMs = 1L,
            status = HopStatus.SUCCESS,
            probeRttsMs = listOf(1L, null, 2L),
        )

        assertEquals(1L, hop.rttMinMs)
        assertEquals(1.5, hop.rttAvgMs)
        assertEquals(2L, hop.rttMaxMs)
    }

    @Test
    fun `probe stats are absent when there are no successful probes`() {
        val hop = HopResult(
            hopNumber = 2,
            ip = null,
            hostname = null,
            rtTimeMs = null,
            status = HopStatus.TIMEOUT,
            probeRttsMs = listOf(null, null, null),
        )

        assertNull(hop.rttMinMs)
        assertNull(hop.rttAvgMs)
        assertNull(hop.rttMaxMs)
    }
}
