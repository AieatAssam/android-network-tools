package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.HostResolver
import net.aieat.netswissknife.core.network.operation.CancellationReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
            val packets = repo.ping("8.8.8.8", count = 4, timeoutMs = 1000).toList()
            assertEquals(4, packets.size)
        }

        @Test
        fun `emits 1 packet when count is 1`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000).toList()
            assertEquals(1, packets.size)
        }

        @Test
        fun `sequence numbers start at 1 and increment`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 3, timeoutMs = 1000).toList()
            assertEquals(listOf(1, 2, 3), packets.map { it.sequence })
        }
    }

    @Nested
    @DisplayName("success packets")
    inner class SuccessPackets {

        @Test
        fun `successful ping sets status to SUCCESS`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(rtMs = 12L))
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000).toList()
            assertEquals(PingStatus.SUCCESS, packets[0].status)
        }

        @Test
        fun `successful ping captures rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(rtMs = 12L))
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000).toList()
            assertEquals(12L, packets[0].rtTimeMs)
        }

        @Test
        fun `successful ping has null errorMessage`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("8.8.8.8", count = 1, timeoutMs = 1000).toList()
            assertNull(packets[0].errorMessage)
        }

        @Test
        fun `host address is set on each packet`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker())
            val packets = repo.ping("example.com", count = 2, timeoutMs = 1000).toList()
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
            val packets = repo.ping("10.0.0.1", count = 1, timeoutMs = 100).toList()
            assertEquals(PingStatus.TIMEOUT, packets[0].status)
        }

        @Test
        fun `timed-out ping has null rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = timeoutChecker())
            val packets = repo.ping("10.0.0.1", count = 1, timeoutMs = 100).toList()
            assertNull(packets[0].rtTimeMs)
        }
    }

    @Nested
    @DisplayName("error packets")
    inner class ErrorPackets {

        @Test
        fun `checker error sets status to ERROR`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker("unknown host: badhost"))
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000).toList()
            assertEquals(PingStatus.ERROR, packets[0].status)
        }

        @Test
        fun `error message is propagated to packet`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker("unknown host: badhost"))
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000).toList()
            assertNotNull(packets[0].errorMessage)
            assertEquals("unknown host: badhost", packets[0].errorMessage)
        }

        @Test
        fun `error packet has null rtTimeMs`() = runTest {
            val repo = PingRepositoryImpl(checker = errorChecker())
            val packets = repo.ping("badhost", count = 1, timeoutMs = 1000).toList()
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
            val packets = repo.ping("host", count = 4, timeoutMs = 1000).toList()
            assertEquals(4, packets.size)
            assertEquals(PingStatus.TIMEOUT, packets[0].status)
            assertEquals(PingStatus.SUCCESS, packets[1].status)
            assertEquals(PingStatus.TIMEOUT, packets[2].status)
            assertEquals(PingStatus.SUCCESS, packets[3].status)
        }
    }

    @Nested
    @DisplayName("continuousPing")
    inner class ContinuousPing {

        @Test
        fun `emits packets until flow is cancelled`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(), delayBetweenProbesMs = 0L)
            val packets = repo.continuousPing("8.8.8.8", timeoutMs = 1000)
                .take(5)
                .toList()
            assertEquals(5, packets.size)
        }

        @Test
        fun `sequence numbers increment from 1`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(), delayBetweenProbesMs = 0L)
            val packets = repo.continuousPing("8.8.8.8", timeoutMs = 1000)
                .take(3)
                .toList()
            assertEquals(listOf(1, 2, 3), packets.map { it.sequence })
        }

        @Test
        fun `successful probes produce SUCCESS packets`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(rtMs = 20L), delayBetweenProbesMs = 0L)
            val packet = repo.continuousPing("8.8.8.8", timeoutMs = 1000)
                .take(1)
                .toList()
                .first()
            assertEquals(PingStatus.SUCCESS, packet.status)
            assertEquals(20L, packet.rtTimeMs)
        }

        @Test
        fun `timeout probes produce TIMEOUT packets with null rtt`() = runTest {
            val repo = PingRepositoryImpl(checker = timeoutChecker(), delayBetweenProbesMs = 0L)
            val packet = repo.continuousPing("10.0.0.1", timeoutMs = 100)
                .take(1)
                .toList()
                .first()
            assertEquals(PingStatus.TIMEOUT, packet.status)
            assertNull(packet.rtTimeMs)
        }

        @Test
        fun `host is set on every packet`() = runTest {
            val repo = PingRepositoryImpl(checker = successChecker(), delayBetweenProbesMs = 0L)
            val packets = repo.continuousPing("example.com", timeoutMs = 1000)
                .take(3)
                .toList()
            assertTrue(packets.all { it.host == "example.com" })
        }

        @Test
        fun `resolves target once and uses the same source IP for every packet`() = runTest {
            val resolutionCount = AtomicInteger()
            val probedIps = CopyOnWriteArrayList<String>()
            val repository = PingRepositoryImpl(
                checker = { ip, _ ->
                    probedIps += ip
                    ReachabilityResult(reachable = true, rtTimeMs = 1L)
                },
                delayBetweenProbesMs = 0L,
                resolver = HostResolver {
                    if (resolutionCount.incrementAndGet() == 1) "192.0.2.10" else "192.0.2.20"
                },
            )

            val packets = repository.continuousPing(
                PingRequest("changing.example", count = 0, timeoutMs = 1000, intervalMs = 25),
            )
                .take(3)
                .toList()

            assertEquals(1, resolutionCount.get())
            assertTrue(probedIps.isNotEmpty())
            assertTrue(probedIps.all { it == "192.0.2.10" })
            assertEquals(listOf("192.0.2.10", "192.0.2.10", "192.0.2.10"), packets.map { it.fromIp })
        }

        @Test
        fun `resolution failure retries at interval then pins first successful address`() = runTest {
            val resolutionCount = AtomicInteger()
            val probeCount = AtomicInteger()
            val eventOrder = CopyOnWriteArrayList<String>()
            val resolutionTimesNanos = CopyOnWriteArrayList<Long>()
            val repository = PingRepositoryImpl(
                checker = { ip, _ ->
                    probeCount.incrementAndGet()
                    eventOrder += "probe:$ip"
                    ReachabilityResult(reachable = true, rtTimeMs = 1L)
                },
                delayBetweenProbesMs = 0L,
                resolver = HostResolver {
                    val attempt = resolutionCount.incrementAndGet()
                    resolutionTimesNanos += System.nanoTime()
                    eventOrder += "resolve:$attempt"
                    if (attempt == 1) throw java.net.UnknownHostException("NXDOMAIN")
                    if (attempt == 2) "192.0.2.10" else "192.0.2.20"
                },
            )

            val packets = repository.continuousPing(
                PingRequest("changing.example", count = 0, timeoutMs = 1000, intervalMs = 50),
            ).take(4).toList()

            assertEquals(2, resolutionCount.get())
            assertEquals(3, probeCount.get())
            assertEquals(listOf(1, 2, 3, 4), packets.map { it.sequence })
            assertEquals(PingStatus.ERROR, packets.first().status)
            assertEquals("NXDOMAIN", packets.first().errorMessage)
            assertNull(packets.first().fromIp)
            assertTrue(packets.drop(1).all { it.status == PingStatus.SUCCESS })
            assertEquals(listOf("192.0.2.10", "192.0.2.10", "192.0.2.10"), packets.drop(1).map { it.fromIp })
            assertTrue(eventOrder.indexOf("probe:192.0.2.10") > eventOrder.indexOf("resolve:2"))
            val retryDelayMillis = (resolutionTimesNanos[1] - resolutionTimesNanos[0]) / 1_000_000L
            assertTrue(retryDelayMillis >= 30L, "retry should respect the configured 50 ms interval")
            assertTrue(retryDelayMillis < 5_000L, "resolution retry should remain responsive")
        }
    }

    @Test
    fun `caller session cancellation interrupts blocking reachability without a late packet`() = runTest {
        val checkerStarted = CountDownLatch(1)
        val checkerInterrupted = CountDownLatch(1)
        val neverReleased = CountDownLatch(1)
        val collectorFinished = CountDownLatch(1)
        val checker: (String, Int) -> ReachabilityResult = { _, _ ->
            checkerStarted.countDown()
            try {
                neverReleased.await()
                ReachabilityResult(reachable = true, rtTimeMs = 1)
            } catch (interrupted: InterruptedException) {
                checkerInterrupted.countDown()
                throw IllegalStateException("reachability interrupted", interrupted)
            }
        }
        val resolver = HostResolver { "192.0.2.1" }
        val repository = PingRepositoryImpl(checker = checker, resolver = resolver)
        val session = PingOperation.newSession()
        val packets = mutableListOf<PingPacketResult>()
        val collector = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                repository.ping(PingRequest("host.test", count = 1, timeoutMs = 1_000), session)
                    .collect(packets::add)
            } finally {
                collectorFinished.countDown()
            }
        }

        assertTrue(withContext(Dispatchers.IO) { checkerStarted.await(2, TimeUnit.SECONDS) })
        session.cancel(CancellationReason.USER_STOP)
        assertTrue(withContext(Dispatchers.IO) { collectorFinished.await(2, TimeUnit.SECONDS) })
        collector.join()

        assertTrue(withContext(Dispatchers.IO) { checkerInterrupted.await(2, TimeUnit.SECONDS) })
        assertTrue(session.resources.isClosed)
        assertTrue(packets.isEmpty())
    }
}
