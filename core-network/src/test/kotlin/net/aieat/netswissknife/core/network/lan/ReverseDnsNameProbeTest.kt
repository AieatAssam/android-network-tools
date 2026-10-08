package net.aieat.netswissknife.core.network.lan

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReverseDnsNameProbeTest {
    @Test
    fun `production pool accepts six lookups rejects overflow and releases workers`() = runTest {
        val workers = createProductionReverseDnsWorkers()
        val lookupStarted = CountDownLatch(2)
        val releaseLookups = CountDownLatch(1)
        val activeLookups = AtomicInteger()
        val maxActiveLookups = AtomicInteger()
        val probe = ReverseDnsNameProbe(
            { ip ->
                val active = activeLookups.incrementAndGet()
                maxActiveLookups.updateAndGet { maximum -> maxOf(maximum, active) }
                lookupStarted.countDown()
                try {
                    check(releaseLookups.await(2, TimeUnit.SECONDS)) {
                        "test did not release reverse-DNS lookup"
                    }
                    "host-$ip"
                } finally {
                    activeLookups.decrementAndGet()
                }
            },
            workers,
        )
        val requests = mutableListOf<Deferred<String?>>()

        try {
            assertEquals(2, workers.corePoolSize)
            assertEquals(2, workers.maximumPoolSize)
            assertEquals(4, workers.queue.remainingCapacity())

            repeat(2) { index ->
                requests += async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    probe.resolveName("192.0.2.${index + 1}", timeoutMs = 10_000)
                }
            }
            assertTrue(
                withContext(Dispatchers.IO) { lookupStarted.await(1, TimeUnit.SECONDS) },
                "both production workers should start lookups",
            )

            repeat(4) { index ->
                requests += async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    probe.resolveName("192.0.2.${index + 3}", timeoutMs = 10_000)
                }
            }
            assertEquals(4, workers.queue.size, "the production queue should accept four waiting lookups")

            assertNull(probe.resolveName("192.0.2.99", timeoutMs = 10_000))
            assertEquals(2, workers.largestPoolSize)

            releaseLookups.countDown()
            val results = requests.awaitAll()

            assertEquals((1..6).map { "host-192.0.2.$it" }, results)
            assertEquals(2, maxActiveLookups.get())
        } finally {
            releaseLookups.countDown()
            requests.forEach { it.cancelAndJoin() }
            workers.shutdownNow()
            assertTrue(
                withContext(Dispatchers.IO) { workers.awaitTermination(1, TimeUnit.SECONDS) },
                "production workers should terminate after cleanup",
            )
        }
        assertEquals(0, workers.poolSize)
    }

    @Test
    fun `lookup timeout cancels bounded worker and returns no name`() = runTest {
        val lookupStarted = CountDownLatch(1)
        val lookupInterrupted = CountDownLatch(1)
        val probe = ReverseDnsNameProbe {
            lookupStarted.countDown()
            try {
                CountDownLatch(1).await()
                "host.example"
            } catch (interrupted: InterruptedException) {
                lookupInterrupted.countDown()
                null
            }
        }

        val result = withContext(Dispatchers.IO) {
            probe.resolveName("192.0.2.1", timeoutMs = 100)
        }

        assertNull(result)
        assertTrue(lookupStarted.await(1, TimeUnit.SECONDS), "reverse DNS lookup did not start")
        assertTrue(lookupInterrupted.await(1, TimeUnit.SECONDS), "timeout must interrupt its worker")
    }

    @Test
    fun `cancellation racing with submission removes a subsequently enqueued task`() = runTest {
        val ownerJob = Job()
        val lookupCalled = AtomicBoolean(false)
        val workers = object : ThreadPoolExecutor(
            0,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
        ) {
            override fun execute(command: Runnable) {
                ownerJob.cancel()
                check(queue.offer(command))
            }
        }
        val probe = ReverseDnsNameProbe(
            { lookupCalled.set(true); "host.example" },
            workers,
        )
        val operation = CoroutineScope(Dispatchers.IO + ownerJob).launch {
            probe.resolveName("192.0.2.1", timeoutMs = 10_000)
        }

        operation.join()

        assertTrue(operation.isCancelled)
        assertTrue(workers.queue.isEmpty(), "cancelled work must not remain queued")
        assertFalse(lookupCalled.get(), "cancelled lookup must never run")
        workers.shutdownNow()
    }
}
