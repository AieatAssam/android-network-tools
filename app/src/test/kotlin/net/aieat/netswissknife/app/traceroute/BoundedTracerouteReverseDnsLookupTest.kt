package net.aieat.netswissknife.app.traceroute

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedTracerouteReverseDnsLookupTest {

    @Test
    fun `resolver-originated cancellation is optional failure and keeps numeric hop`() = runBlocking {
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            ThreadFactory { Thread(it, "reverse-dns-cancel-test").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        try {
            val repository = IcmpEnginTracerouteRepositoryImpl(
                nativeTraceFactory = { _, _, _, _, _, _ ->
                    flowOf(HopResult(1, "192.0.2.8", null, 3, HopStatus.SUCCESS))
                },
                reverseDnsLookup = BoundedTracerouteReverseDnsLookup(executor) {
                    throw CancellationException("platform resolver cancelled internally")
                },
            )

            val hop = repository.trace(
                "192.0.2.1", 2, 500, 1, TracerouteProbeType.ICMP, 56,
            ).first()

            assertEquals("192.0.2.8", hop.ip)
            assertEquals(null, hop.hostname)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancellation removes queued reverse dns work and cannot produce a late result`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            ThreadFactory { Thread(it, "reverse-dns-test").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        val lookup = BoundedTracerouteReverseDnsLookup(executor) {
            started.countDown()
            // Model a platform resolver that ignores interruption until the simulated native
            // call returns. Cancellation must still detach the suspended flow immediately.
            var completed = false
            while (!completed) {
                try {
                    completed = release.await(10, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    // Deliberately remain blocked to exercise queue bounds/cancellation.
                }
            }
            "late.example"
        }
        val first = async { runCatching { lookup.lookup("192.0.2.1", session()) } }

        try {
            assertTrue(started.await(1, TimeUnit.SECONDS))
            val queued = async { runCatching { lookup.lookup("192.0.2.2", session()) } }
            withTimeout(1_000) {
                while (executor.queue.size != 1) kotlinx.coroutines.yield()
            }

            queued.cancelAndJoin()
            assertTrue(executor.queue.isEmpty(), "cancelled queued lookup must release its slot")
            first.cancelAndJoin()
            assertFalse(first.isActive)
            release.countDown()
            withTimeout(1_000) {
                while (executor.activeCount != 0) kotlinx.coroutines.delay(5)
            }
            assertEquals(0, executor.queue.size)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun session() = OperationSession(OperationBudget.start(timeoutMillis = 5_000))
}
