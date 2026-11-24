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

    private fun probed(vararg rtts: Long?) =
        HopResult(
            hopNumber = 3,
            ip = "192.0.2.3",
            hostname = null,
            rtTimeMs = rtts.firstOrNull { it != null },
            status = HopStatus.SUCCESS,
            probeRttsMs = rtts.toList(),
        )

    @Test
    fun `the median of the answered probes is not set by one slow probe`() {
        assertEquals(8L, probed(90L, 7L, 8L).rttMedianMs)
        assertEquals(17L, probed(17L, 92L, 17L).rttMedianMs)
        assertEquals(33L, probed(110L, 33L, 14L).rttMedianMs)
        assertEquals(3, probed(90L, 7L, 8L).answeredProbes)
    }

    @Test
    fun `the median skips probes without a reply`() {
        assertEquals(80L, probed(80L, null).rttMedianMs)
        assertEquals(1, probed(80L, null).answeredProbes)
        assertEquals(20L, probed(null, 20L, 21L, 19L).rttMedianMs)
    }

    @Test
    fun `an even number of replies gives the rounded mean of the middle two`() {
        assertEquals(49L, probed(90L, 7L).rttMedianMs)
        assertEquals(15L, probed(10L, 14L, 16L, 40L).rttMedianMs)
    }

    @Test
    fun `no answered probe has no median`() {
        assertNull(probed(null, null, null).rttMedianMs)
        assertEquals(0, probed(null, null, null).answeredProbes)
    }

    @Test
    fun `a result without per-probe RTTs uses its single RTT`() {
        val bare = HopResult(4, "192.0.2.4", null, 12L, HopStatus.SUCCESS)

        assertEquals(12L, bare.rttMedianMs)
        assertEquals(1, bare.answeredProbes)
        assertNull(HopResult(5, null, null, null, HopStatus.TIMEOUT).rttMedianMs)
        assertEquals(0, HopResult(5, null, null, null, HopStatus.TIMEOUT).answeredProbes)
    }
}
