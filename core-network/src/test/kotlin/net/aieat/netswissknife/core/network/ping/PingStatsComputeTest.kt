package net.aieat.netswissknife.core.network.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PingStats.compute")
class PingStatsComputeTest {

    private fun successPacket(seq: Int, rtMs: Long) =
        PingPacketResult(seq, "host", rtMs, PingStatus.SUCCESS)

    private fun timeoutPacket(seq: Int) =
        PingPacketResult(seq, "host", null, PingStatus.TIMEOUT)

    @Test
    fun `accumulator matches list computation while ignoring failed RTTs for jitter`() {
        val packets = listOf(
            successPacket(1, 10L),
            timeoutPacket(2),
            successPacket(3, 30L),
            PingPacketResult(4, "host", 90L, PingStatus.ERROR),
            successPacket(5, 20L),
        )
        val accumulator = PingStatsAccumulator()
        packets.forEach(accumulator::add)

        assertEquals(PingStats.compute(packets), accumulator.snapshot())
        assertEquals(5, accumulator.snapshot().sent)
        assertEquals(3, accumulator.snapshot().received)
        assertEquals(15.0, accumulator.snapshot().jitterMs, 0.001)
    }

    @Test
    fun `accumulator keeps whole-session loss and RTT statistics beyond a hundred packets`() {
        val accumulator = PingStatsAccumulator()
        repeat(150) { index ->
            val packet = if (index < 50) {
                timeoutPacket(index + 1)
            } else {
                successPacket(index + 1, 25L)
            }
            accumulator.add(packet)
        }

        val stats = accumulator.snapshot()
        assertEquals(150, stats.sent)
        assertEquals(100, stats.received)
        assertEquals(33.333336f, stats.lossPercent, 0.001f)
        assertEquals(25L, stats.minMs)
        assertEquals(25L, stats.maxMs)
        assertEquals(25.0, stats.avgMs, 0.001)
        assertEquals(0.0, stats.jitterMs, 0.001)
    }

    @Nested
    @DisplayName("empty packet list")
    inner class EmptyList {

        @Test
        fun `sent is zero`() {
            val stats = PingStats.compute(emptyList())
            assertEquals(0, stats.sent)
        }

        @Test
        fun `received is zero`() {
            val stats = PingStats.compute(emptyList())
            assertEquals(0, stats.received)
        }

        @Test
        fun `lossPercent is zero`() {
            val stats = PingStats.compute(emptyList())
            assertEquals(0f, stats.lossPercent)
        }
    }

    @Nested
    @DisplayName("all success")
    inner class AllSuccess {

        private val packets = listOf(
            successPacket(1, 10L),
            successPacket(2, 20L),
            successPacket(3, 30L),
            successPacket(4, 40L)
        )

        @Test
        fun `sent equals packet count`() {
            assertEquals(4, PingStats.compute(packets).sent)
        }

        @Test
        fun `received equals packet count`() {
            assertEquals(4, PingStats.compute(packets).received)
        }

        @Test
        fun `lossPercent is zero`() {
            assertEquals(0f, PingStats.compute(packets).lossPercent)
        }

        @Test
        fun `minMs is smallest rtt`() {
            assertEquals(10L, PingStats.compute(packets).minMs)
        }

        @Test
        fun `maxMs is largest rtt`() {
            assertEquals(40L, PingStats.compute(packets).maxMs)
        }

        @Test
        fun `avgMs is mean of rtts`() {
            assertEquals(25.0, PingStats.compute(packets).avgMs, 0.001)
        }

        @Test
        fun `jitterMs is mean absolute deviation of successive differences`() {
            // diffs = |20-10|=10, |30-20|=10, |40-30|=10 → avg=10
            assertEquals(10.0, PingStats.compute(packets).jitterMs, 0.001)
        }
    }

    @Nested
    @DisplayName("all timeout")
    inner class AllTimeout {

        private val packets = listOf(timeoutPacket(1), timeoutPacket(2), timeoutPacket(3))

        @Test
        fun `received is zero`() {
            assertEquals(0, PingStats.compute(packets).received)
        }

        @Test
        fun `lossPercent is 100`() {
            assertEquals(100f, PingStats.compute(packets).lossPercent, 0.01f)
        }

        @Test
        fun `minMs is zero`() {
            assertEquals(0L, PingStats.compute(packets).minMs)
        }

        @Test
        fun `avgMs is zero`() {
            assertEquals(0.0, PingStats.compute(packets).avgMs, 0.001)
        }

        @Test
        fun `jitterMs is zero when no successes`() {
            assertEquals(0.0, PingStats.compute(packets).jitterMs, 0.001)
        }
    }

    @Nested
    @DisplayName("mixed results")
    inner class Mixed {

        private val packets = listOf(
            successPacket(1, 10L),
            timeoutPacket(2),
            successPacket(3, 30L),
            timeoutPacket(4)
        )

        @Test
        fun `sent is total count`() {
            assertEquals(4, PingStats.compute(packets).sent)
        }

        @Test
        fun `received counts only successes`() {
            assertEquals(2, PingStats.compute(packets).received)
        }

        @Test
        fun `lossPercent is 50`() {
            assertEquals(50f, PingStats.compute(packets).lossPercent, 0.01f)
        }

        @Test
        fun `minMs uses only successful packets`() {
            assertEquals(10L, PingStats.compute(packets).minMs)
        }

        @Test
        fun `maxMs uses only successful packets`() {
            assertEquals(30L, PingStats.compute(packets).maxMs)
        }

        @Test
        fun `avgMs uses only successful rtts`() {
            assertEquals(20.0, PingStats.compute(packets).avgMs, 0.001)
        }
    }

    @Nested
    @DisplayName("single packet")
    inner class SinglePacket {

        @Test
        fun `jitterMs is zero for single success`() {
            val stats = PingStats.compute(listOf(successPacket(1, 15L)))
            assertEquals(0.0, stats.jitterMs, 0.001)
        }

        @Test
        fun `minMs equals maxMs for single success`() {
            val stats = PingStats.compute(listOf(successPacket(1, 15L)))
            assertEquals(15L, stats.minMs)
            assertEquals(15L, stats.maxMs)
        }
    }
}
