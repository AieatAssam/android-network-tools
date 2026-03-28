package net.aieat.netswissknife.core.network.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PingStats")
class PingStatsTest {

    @Nested
    @DisplayName("lossPercent")
    inner class LossPercent {

        @Test
        fun `zero loss when all packets received`() {
            val stats = PingStats(sent = 4, received = 4, lossPercent = 0f,
                minMs = 10, maxMs = 30, avgMs = 20.0, jitterMs = 5.0)
            assertEquals(0f, stats.lossPercent)
        }

        @Test
        fun `100 percent loss when none received`() {
            val stats = PingStats(sent = 4, received = 0, lossPercent = 100f,
                minMs = 0, maxMs = 0, avgMs = 0.0, jitterMs = 0.0)
            assertEquals(100f, stats.lossPercent)
        }

        @Test
        fun `50 percent loss when half received`() {
            val stats = PingStats(sent = 4, received = 2, lossPercent = 50f,
                minMs = 10, maxMs = 20, avgMs = 15.0, jitterMs = 5.0)
            assertEquals(50f, stats.lossPercent)
        }
    }

    @Nested
    @DisplayName("rtt values")
    inner class RttValues {

        @Test
        fun `minMs is preserved`() {
            val stats = PingStats(sent = 4, received = 4, lossPercent = 0f,
                minMs = 5, maxMs = 50, avgMs = 20.0, jitterMs = 10.0)
            assertEquals(5L, stats.minMs)
        }

        @Test
        fun `maxMs is preserved`() {
            val stats = PingStats(sent = 4, received = 4, lossPercent = 0f,
                minMs = 5, maxMs = 50, avgMs = 20.0, jitterMs = 10.0)
            assertEquals(50L, stats.maxMs)
        }

        @Test
        fun `avgMs is preserved`() {
            val stats = PingStats(sent = 4, received = 4, lossPercent = 0f,
                minMs = 5, maxMs = 50, avgMs = 27.5, jitterMs = 10.0)
            assertEquals(27.5, stats.avgMs, 0.001)
        }

        @Test
        fun `jitterMs is preserved`() {
            val stats = PingStats(sent = 4, received = 4, lossPercent = 0f,
                minMs = 5, maxMs = 50, avgMs = 20.0, jitterMs = 12.3)
            assertEquals(12.3, stats.jitterMs, 0.001)
        }
    }
}
