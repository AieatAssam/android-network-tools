package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SpeedTestHostResolverTest {
    @Test
    fun `user stop interrupts blocked DNS and does not create connect socket`() =
        runBlocking {
            val executor =
                ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    ArrayBlockingQueue(1),
                )
            val lookupStarted = CountDownLatch(1)
            val lookupInterrupted = CountDownLatch(1)
            val socketCreations = AtomicInteger()
            val resolver =
                SpeedTestHostResolver(executor) {
                    lookupStarted.countDown()
                    try {
                        CountDownLatch(1).await()
                        InetAddress.getByName("192.0.2.1")
                    } catch (interrupted: InterruptedException) {
                        lookupInterrupted.countDown()
                        throw UnknownHostException("test lookup interrupted").also {
                            it.initCause(interrupted)
                        }
                    }
                }
            val engine =
                OkHttpTransferEngine().apply {
                    hostResolver = resolver
                    socketFactory = {
                        socketCreations.incrementAndGet()
                        java.net.Socket()
                    }
                }
            val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))
            val operation =
                async(Dispatchers.IO) {
                    OperationRunner.run(session) {
                        engine.connectRtt("blocked.example", 443, session)
                    }
                }

            try {
                assertTrue(withContext(Dispatchers.IO) { lookupStarted.await(2, TimeUnit.SECONDS) })
                session.cancel(CancellationReason.USER_STOP)
                withTimeout(1_000) {
                    runCatching { operation.await() }
                }

                assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
                assertTrue(withContext(Dispatchers.IO) { lookupInterrupted.await(1, TimeUnit.SECONDS) })
                assertEquals(0, socketCreations.get(), "connect socket must not be created before DNS succeeds")
            } finally {
                operation.cancel()
                executor.shutdownNow()
            }
        }

    @Test
    fun `deadline interrupts blocked DNS and does not create connect socket`() =
        runBlocking {
            val executor =
                ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    ArrayBlockingQueue(1),
                )
            val lookupStarted = CountDownLatch(1)
            val lookupInterrupted = CountDownLatch(1)
            val socketCreations = AtomicInteger()
            val resolver =
                SpeedTestHostResolver(executor) {
                    lookupStarted.countDown()
                    try {
                        CountDownLatch(1).await()
                        InetAddress.getByName("192.0.2.1")
                    } catch (interrupted: InterruptedException) {
                        lookupInterrupted.countDown()
                        throw UnknownHostException("test lookup interrupted").also {
                            it.initCause(interrupted)
                        }
                    }
                }
            val engine =
                OkHttpTransferEngine().apply {
                    hostResolver = resolver
                    socketFactory = {
                        socketCreations.incrementAndGet()
                        java.net.Socket()
                    }
                }
            val session = OperationSession(OperationBudget.start(timeoutMillis = 1_000))
            val operation =
                async(Dispatchers.IO) {
                    runCatching {
                        OperationRunner.run(session) {
                            engine.connectRtt("blocked.example", 443, session)
                        }
                    }
                }

            try {
                assertTrue(withContext(Dispatchers.IO) { lookupStarted.await(2, TimeUnit.SECONDS) })
                val failure = withTimeout(2_000) { operation.await().exceptionOrNull() }

                assertTrue(failure is OperationDeadlineExceededException, "expected deadline, got $failure")
                assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
                assertTrue(withContext(Dispatchers.IO) { lookupInterrupted.await(1, TimeUnit.SECONDS) })
                assertEquals(0, socketCreations.get(), "connect socket must not be created before DNS succeeds")
            } finally {
                operation.cancel()
                executor.shutdownNow()
            }
        }

    @Test
    fun `production resolver factory caps active and queued lookups and rejects overflow`() =
        runBlocking {
            val executor = SpeedTestHostResolutionWorkers.createWorkerExecutor()
            val active = AtomicInteger()
            val maximumActive = AtomicInteger()
            val workersStarted = CountDownLatch(SpeedTestHostResolutionWorkers.WORKER_COUNT)
            val releaseWorkers = CountDownLatch(1)
            val answer = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))
            val resolver =
                SpeedTestHostResolver(executor) {
                    val current = active.incrementAndGet()
                    maximumActive.updateAndGet { previous -> maxOf(previous, current) }
                    workersStarted.countDown()
                    try {
                        releaseWorkers.await()
                        answer
                    } finally {
                        active.decrementAndGet()
                    }
                }

            try {
                assertEquals(SpeedTestHostResolutionWorkers.WORKER_COUNT, executor.corePoolSize)
                assertEquals(SpeedTestHostResolutionWorkers.WORKER_COUNT, executor.maximumPoolSize)
                assertEquals(SpeedTestHostResolutionWorkers.QUEUE_CAPACITY, executor.queue.remainingCapacity())

                val acceptedCount =
                    SpeedTestHostResolutionWorkers.WORKER_COUNT +
                        SpeedTestHostResolutionWorkers.QUEUE_CAPACITY
                val accepted =
                    (0 until acceptedCount).map { index ->
                        async(Dispatchers.IO) {
                            resolver.resolve(
                                "host-$index.example",
                                OperationSession(OperationBudget.startUnbounded()),
                            )
                        }
                    }
                assertTrue(withContext(Dispatchers.IO) { workersStarted.await(2, TimeUnit.SECONDS) })
                withTimeout(2_000) {
                    while (executor.queue.size != SpeedTestHostResolutionWorkers.QUEUE_CAPACITY) delay(1)
                }
                assertEquals(SpeedTestHostResolutionWorkers.WORKER_COUNT, executor.activeCount)
                assertEquals(SpeedTestHostResolutionWorkers.QUEUE_CAPACITY, executor.queue.size)

                val overflow =
                    runCatching {
                        resolver.resolve(
                            "overflow.example",
                            OperationSession(OperationBudget.startUnbounded()),
                        )
                    }.exceptionOrNull()
                assertTrue(overflow is RejectedExecutionException, "overflow must be rejected by the bounded executor")

                releaseWorkers.countDown()
                assertEquals(acceptedCount, accepted.awaitAll().size)
                assertEquals(SpeedTestHostResolutionWorkers.WORKER_COUNT, maximumActive.get())
                assertTrue(executor.queue.isEmpty())
            } finally {
                releaseWorkers.countDown()
                executor.shutdownNow()
            }
        }
}
