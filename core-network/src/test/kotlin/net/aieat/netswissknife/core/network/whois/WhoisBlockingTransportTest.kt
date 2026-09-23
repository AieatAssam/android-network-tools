package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WhoisBlockingTransportTest {
    @Test
    fun `cancellation racing with executor submission removes queued work`() = runTest {
        val blockerEntered = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val executor = SubmissionRaceExecutor(releaseBlocker)
        val resolverCalled = AtomicBoolean(false)
        val socketCreated = AtomicBoolean(false)
        try {
            executor.execute {
                blockerEntered.countDown()
                releaseBlocker.await()
            }
            awaitLatch(blockerEntered)
            executor.pauseNextSubmission = true

            val query = async(Dispatchers.Default) {
                WhoisBlockingTransport.query(
                    host = "whois.example",
                    query = "example.com",
                    port = 43,
                    timeoutMs = 1_000,
                    resolver = WhoisHostResolver {
                        resolverCalled.set(true)
                        InetAddress.getByName("8.8.8.8")
                    },
                    socketFactory = WhoisSocketFactory {
                        socketCreated.set(true)
                        Socket()
                    },
                    isDisallowedAddress = { false },
                    executor = executor,
                )
            }

            awaitLatch(executor.submissionPaused)
            query.cancel()
            executor.allowSubmission.countDown()
            awaitLatch(executor.submissionReturned)
            query.join()

            assertTrue(executor.queue.isEmpty(), "cancelled FutureTask should be removed after enqueue")
            assertFalse(resolverCalled.get(), "cancelled queued resolver work must never run")
            assertFalse(socketCreated.get())
        } finally {
            releaseBlocker.countDown()
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private suspend fun awaitLatch(latch: CountDownLatch) {
        assertTrue(withContext(Dispatchers.IO) { latch.await(2, TimeUnit.SECONDS) }, "latch timed out")
    }

    private class SubmissionRaceExecutor(
        private val releaseBlocker: CountDownLatch,
    ) : ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(4),
    ) {
        val submissionPaused = CountDownLatch(1)
        val allowSubmission = CountDownLatch(1)
        val submissionReturned = CountDownLatch(1)
        @Volatile var pauseNextSubmission = false

        override fun execute(command: Runnable) {
            if (pauseNextSubmission) {
                pauseNextSubmission = false
                submissionPaused.countDown()
                allowSubmission.await()
                super.execute(command)
                submissionReturned.countDown()
            } else {
                super.execute(command)
            }
        }
    }
}
