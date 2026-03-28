package net.aieat.netswissknife.core.network.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PingResult")
class PingResultTest {

    private val sampleStats = PingStats(
        sent = 4, received = 4, lossPercent = 0f,
        minMs = 10, maxMs = 30, avgMs = 20.0, jitterMs = 5.0
    )

    private val samplePackets = listOf(
        PingPacketResult(1, "8.8.8.8", 10L, PingStatus.SUCCESS),
        PingPacketResult(2, "8.8.8.8", 20L, PingStatus.SUCCESS),
        PingPacketResult(3, "8.8.8.8", 25L, PingStatus.SUCCESS),
        PingPacketResult(4, "8.8.8.8", 30L, PingStatus.SUCCESS),
    )

    @Nested
    @DisplayName("construction")
    inner class Construction {

        @Test
        fun `host is preserved`() {
            val result = PingResult("8.8.8.8", samplePackets, sampleStats, "raw output")
            assertEquals("8.8.8.8", result.host)
        }

        @Test
        fun `packets list is preserved`() {
            val result = PingResult("8.8.8.8", samplePackets, sampleStats, "raw output")
            assertEquals(4, result.packets.size)
        }

        @Test
        fun `stats are preserved`() {
            val result = PingResult("8.8.8.8", samplePackets, sampleStats, "raw output")
            assertEquals(sampleStats, result.stats)
        }

        @Test
        fun `rawOutput is preserved`() {
            val raw = "PING 8.8.8.8: 56 data bytes\n4 packets transmitted"
            val result = PingResult("8.8.8.8", samplePackets, sampleStats, raw)
            assertEquals(raw, result.rawOutput)
            assertTrue(result.rawOutput.contains("PING"))
        }
    }
}
