package net.aieat.netswissknife.core.network.lan

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReverseDnsNameProbeTest {
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
