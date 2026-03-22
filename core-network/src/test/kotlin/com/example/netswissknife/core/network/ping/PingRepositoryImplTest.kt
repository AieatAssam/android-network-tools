package com.example.netswissknife.core.network.ping

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PingRepositoryImpl")
class PingRepositoryImplTest {

    private fun successChecker(rtMs: Long = 15L): (String, Int) -> ReachabilityResult = { _, _ ->
        ReachabilityResult(reachable = true, rtTimeMs = rtMs)
    }

    private fun timeoutChecker(): (String, Int) -> ReachabilityResult = { _, _ ->
        ReachabilityResult(reachable = false, rtTimeMs = 3000L)
    }

    private fun errorChecker(msg: String = "unknown host"): (String, Int) -> ReachabilityResult = { _, _ ->
        ReachabilityResult(reachable = false, rtTimeMs = 0L, errorMessage = msg)
    }

    @Nested
    @DisplayName("packet count")
    inner class PacketCount {

        @Test
        fun `emits exactly count packets`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 4, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(4, packets.size)
        }

        @Test
        fun `emits 1 packet when count is 1`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(1, packets.size)
        }

        @Test
        fun `sequence numbers start at 1 and increment`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 3, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(listOf(1, 2, 3), packets.map { it.sequence })
        }
    }

    @Nested
    @DisplayName("success packets")
    inner class SuccessPackets {

        @Test
        fun `successful ping sets status to SUCCESS`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(rtMs = 12L))
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(PingStatus.SUCCESS, packets[0].status)
        }

        @Test
        fun `successful ping captures rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(rtMs = 12L))
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(12L, packets[0].rtTimeMs)
        }

        @Test
        fun `successful ping has null errorMessage`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertNull(packets[0].errorMessage)
        }

        @Test
        fun `host address is set on each packet`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("example.com", count = 2, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals("example.com", packets[0].host)
            assertEquals("example.com", packets[1].host)
        }
    }

    @Nested
    @DisplayName("timeout packets")
    inner class TimeoutPackets {

        @Test
        fun `timed-out ping sets status to TIMEOUT`() = runTest {
            val repo = PingRepositoryImpl(checker = timeoutChecker())
            val packets = repo.ping("10.0.0.1", count = 1, timeoutMs = 100, packetSize = 56).toList()
            assertEquals(PingStatus.TIMEOUT, packets[0].status)
        }

        @Test
        fun `timed-out ping has null rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = timeoutChecker())
            val packets = repo.ping("10.0.0.1", count = 1, timeoutMs = 100, packetSize = 56).toList()
            assertNull(packets[0].rtTimeMs)
        }
    }

    @Nested
    @DisplayName("error packets")
    inner class ErrorPackets {

        @Test
        fun `checker error sets status to ERROR`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker("unknown host: badhost"))
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(PingStatus.ERROR, packets[0].status)
        }

        @Test
        fun `error message is propagated to packet`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker("unknown host: badhost"))
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertNotNull(packets[0].errorMessage)
            assertEquals("unknown host: badhost", packets[0].errorMessage)
        }

        @Test
        fun `error packet has null rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker())
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000, packetSize = 56).toList()
            assertNull(packets[0].rtTimeMs)
        }
    }

    @Nested
    @DisplayName("mixed results")
    inner class MixedResults {

        @Test
        fun `emits all packets even when some fail`() = runTest {
            var callCount = 0
            val alternating: (String, Int) -> ReachabilityResult = { _, _ ->
                callCount++
                if (callCount % 2 == 0) ReachabilityResult(true, 10L)
                else ReachabilityResult(false, 3000L)
            }
            val repo = PingRepositoryImpl(checker = alternating)
            val packets = repo.ping("host", count = 4, timeoutMs = 1000, packetSize = 56).toList()
            assertEquals(4, packets.size)
            assertEquals(PingStatus.TIMEOUT, packets[0].status)
            assertEquals(PingStatus.SUCCESS, packets[1].status)
            assertEquals(PingStatus.TIMEOUT, packets[2].status)
            assertEquals(PingStatus.SUCCESS, packets[3].status)
        }
    }
}
