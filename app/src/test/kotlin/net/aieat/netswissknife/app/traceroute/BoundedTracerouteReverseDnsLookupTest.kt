package net.aieat.netswissknife.app.traceroute

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.OperationRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedTracerouteReverseDnsLookupTest {

    @Test
    fun `resolver-originated cancellation is optional lookup failure`() = runBlocking {
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
            val lookup = BoundedTracerouteReverseDnsLookup(executor) {
                throw CancellationException("platform resolver cancelled internally")
            }

            val failure = runCatching { lookup.lookup("192.0.2.8", session()) }.exceptionOrNull()
            assertInstanceOf(IllegalStateException::class.java, failure)
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
        val first = async(Dispatchers.Default) {
            runCatching { lookup.lookup("192.0.2.1", session()) }
        }

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

    @Test
    fun `owning session stop cancels queued lookup and releases its task`() = runBlocking {
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            ThreadFactory { Thread(it, "reverse-dns-session-stop-test").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        val session = session()
        val lookup = BoundedTracerouteReverseDnsLookup(executor) { "router.example" }

        try {
            // Occupy the only worker so the lookup lease is observably queued.
            executor.execute {
                workerStarted.countDown()
                var released = false
                while (!released) {
                    try {
                        released = releaseWorker.await(10, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        // Keep the worker occupied until cleanup to make queue removal deterministic.
                    }
                }
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
            val collection = async {
                OperationRunner.run(session) {
                    lookup.lookup("192.0.2.9", session)
                }
            }
            withTimeout(1_000) {
                while (executor.queue.size != 1) kotlinx.coroutines.yield()
            }

            session.cancel(CancellationReason.USER_STOP)
            val failure = runCatching { withTimeout(1_000) { collection.await() } }.exceptionOrNull()

            assertTrue(failure is kotlinx.coroutines.CancellationException)
            assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
            assertTrue(executor.queue.isEmpty(), "session cancellation must remove the lookup lease task")
            releaseWorker.countDown()
            withTimeout(1_000) {
                while (executor.activeCount != 0) kotlinx.coroutines.delay(5)
            }
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `executor saturation returns no optional reverse dns result`() = runBlocking {
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            ThreadFactory { Thread(it, "reverse-dns-saturation-test").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        try {
            executor.execute {
                workerStarted.countDown()
                releaseWorker.await()
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
            executor.execute { /* Fill the sole waiting slot. */ }

            val lookup = BoundedTracerouteReverseDnsLookup(executor) { "router.example" }
            val failure = runCatching { lookup.lookup("192.0.2.9", session()) }.exceptionOrNull()
            assertInstanceOf(RejectedExecutionException::class.java, failure)
            assertEquals(1, executor.queue.size, "the unrelated queued task must remain intact")
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `production worker factory bounds active and queued lookups`() = runBlocking {
        val executor = TracerouteNameResolutionWorkers.createExecutor()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val workersStarted = CountDownLatch(REVERSE_DNS_WORKER_COUNT)
        val releaseWorkers = CountDownLatch(1)
        val lookup = BoundedTracerouteReverseDnsLookup(executor) {
            val current = active.incrementAndGet()
            maximumActive.updateAndGet { previous -> maxOf(previous, current) }
            workersStarted.countDown()
            try {
                releaseWorkers.await()
                "router.example"
            } finally {
                active.decrementAndGet()
            }
        }
        val acceptedCount =
            REVERSE_DNS_WORKER_COUNT + REVERSE_DNS_QUEUE_CAPACITY
        val accepted =
            (0 until acceptedCount).map { index ->
                async(Dispatchers.IO) {
                    lookup.lookup("192.0.2.$index", session())
                }
            }

        try {
            assertEquals(REVERSE_DNS_WORKER_COUNT, executor.corePoolSize)
            assertEquals(REVERSE_DNS_WORKER_COUNT, executor.maximumPoolSize)
            assertEquals(
                REVERSE_DNS_QUEUE_CAPACITY,
                executor.queue.size + executor.queue.remainingCapacity(),
            )
            assertTrue(workersStarted.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) {
                while (executor.queue.size != REVERSE_DNS_QUEUE_CAPACITY) {
                    kotlinx.coroutines.delay(1)
                }
            }
            assertEquals(REVERSE_DNS_WORKER_COUNT, executor.activeCount)
            assertEquals(REVERSE_DNS_QUEUE_CAPACITY, executor.queue.size)

            val overflow =
                runCatching {
                    lookup.lookup("192.0.2.250", session())
                }.exceptionOrNull()
            assertInstanceOf(RejectedExecutionException::class.java, overflow)
            assertEquals(REVERSE_DNS_QUEUE_CAPACITY, executor.queue.size)

            releaseWorkers.countDown()
            assertEquals(List(acceptedCount) { "router.example" }, accepted.awaitAll())
            assertEquals(REVERSE_DNS_WORKER_COUNT, maximumActive.get())
            withTimeout(1_000) {
                while (executor.activeCount != 0) kotlinx.coroutines.delay(1)
            }
            assertTrue(executor.queue.isEmpty())
        } finally {
            releaseWorkers.countDown()
            accepted.forEach { it.cancelAndJoin() }
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }
    }

    private fun session() = OperationSession(OperationBudget.start(timeoutMillis = 5_000))
}
