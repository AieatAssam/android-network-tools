package net.aieat.netswissknife.core.network.speedtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SpeedTest model helpers")
class SpeedTestModelsTest {

    @Nested
    @DisplayName("LatencyStats.compute")
    inner class LatencyStatsCompute {

        @Test
        fun `returns EMPTY for no samples`() {
            assertEquals(LatencyStats.EMPTY, LatencyStats.compute(emptyList()))
        }

        @Test
        fun `computes min avg max from samples`() {
            val samples = listOf(
                LatencySample(1, 10L),
                LatencySample(2, 20L),
                LatencySample(3, 30L)
            )
            val stats = LatencyStats.compute(samples)
            assertEquals(10L, stats.minMs)
            assertEquals(20.0, stats.avgMs)
            assertEquals(30L, stats.maxMs)
        }

        @Test
        fun `computes jitter as mean absolute difference between consecutive samples`() {
            val samples = listOf(
                LatencySample(1, 10L),
                LatencySample(2, 20L),
                LatencySample(3, 15L)
            )
            // |20-10| = 10, |15-20| = 5 -> avg = 7.5
            val stats = LatencyStats.compute(samples)
            assertEquals(7.5, stats.jitterMs)
        }

        @Test
        fun `jitter is zero for a single sample`() {
            val stats = LatencyStats.compute(listOf(LatencySample(1, 10L)))
            assertEquals(0.0, stats.jitterMs)
        }
    }

    @Nested
    @DisplayName("ThroughputResult.from")
    inner class ThroughputResultFrom {

        @Test
        fun `computes average Mbps from total bytes and duration`() {
            // 12,500,000 bytes in 10s = 100,000,000 bits / 10s = 10 Mbps
            val result = ThroughputResult.from(12_500_000L, 10_000L, emptyList())
            assertEquals(10.0, result.avgMbps, 0.0001)
        }

        @Test
        fun `peak Mbps is the maximum instantaneous sample`() {
            val samples = listOf(
                ThroughputSample(1000L, 1_000_000L, 8.0),
                ThroughputSample(2000L, 2_000_000L, 15.5),
                ThroughputSample(3000L, 3_000_000L, 12.0)
            )
            val result = ThroughputResult.from(3_000_000L, 3000L, samples)
            assertEquals(15.5, result.peakMbps)
        }

        @Test
        fun `avgMbps is zero when duration is zero`() {
            val result = ThroughputResult.from(1_000L, 0L, emptyList())
            assertEquals(0.0, result.avgMbps)
        }

        @Test
        fun `falls back to avgMbps for peak when there are no samples`() {
            val result = ThroughputResult.from(12_500_000L, 10_000L, emptyList())
            assertEquals(result.avgMbps, result.peakMbps)
        }
    }
}
