package net.aieat.netswissknife.core.network.topology

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyBlockingCallTest {

    @Test
    fun `factory-built executor bounds active and queued calls and recovers after queued cancellation`() = runBlocking {
        supervisorScope {
            val executor = createTopologyBlockingExecutor()
            val releaseWorkers = CountDownLatch(1)
            val activeEntered = CountDownLatch(TopologyBlockingCall.WORKER_COUNT)
            val blockStarts = AtomicInteger()
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Int>>()

            fun submit(trackForCleanup: Boolean = true): kotlinx.coroutines.Deferred<Int> =
                async(Dispatchers.Default) {
                    TopologyBlockingCall.runWithExecutor(
                        executor = executor,
                        deadline = OperationBudget.start(
                            requirement = OperationRequirement.LOCAL_NETWORK,
                            timeoutMillis = 60_000,
                            maxConcurrentProbes = 1,
                        ).deadline,
                    ) {
                        blockStarts.incrementAndGet()
                        activeEntered.countDown()
                        releaseWorkers.await()
                        1
                    }
                }.also { if (trackForCleanup) jobs += it }

            try {
                assertEquals(TopologyBlockingCall.WORKER_COUNT, executor.corePoolSize)
                assertEquals(TopologyBlockingCall.WORKER_COUNT, executor.maximumPoolSize)
                assertEquals(TopologyBlockingCall.QUEUE_CAPACITY, executor.queue.remainingCapacity())
                assertTrue(executor.rejectedExecutionHandler is ThreadPoolExecutor.AbortPolicy)

                repeat(TopologyBlockingCall.WORKER_COUNT) { submit() }
                assertTrue(
                    withContext(Dispatchers.IO) { activeEntered.await(3, TimeUnit.SECONDS) },
                    "all factory-built workers should enter blocking work",
                )

                val queued = List(TopologyBlockingCall.QUEUE_CAPACITY) { submit() }
                withTimeout(3_000) {
                    while (executor.queue.size != TopologyBlockingCall.QUEUE_CAPACITY) kotlinx.coroutines.yield()
                }
                assertEquals(TopologyBlockingCall.WORKER_COUNT, executor.activeCount)
                assertEquals(TopologyBlockingCall.QUEUE_CAPACITY, executor.queue.size)
                assertEquals(TopologyBlockingCall.WORKER_COUNT, blockStarts.get())

                val overflowFailure = runCatching { submit(trackForCleanup = false).await() }.exceptionOrNull()
                assertInstanceOf(RejectedExecutionException::class.java, overflowFailure)
                assertEquals(
                    TopologyBlockingCall.WORKER_COUNT,
                    blockStarts.get(),
                    "overflow must be rejected before its blocking body starts",
                )

                queued.first().cancelAndJoin()
                withTimeout(3_000) {
                    while (executor.queue.size != TopologyBlockingCall.QUEUE_CAPACITY - 1) kotlinx.coroutines.yield()
                }
                submit()
                withTimeout(3_000) {
                    while (executor.queue.size != TopologyBlockingCall.QUEUE_CAPACITY) kotlinx.coroutines.yield()
                }
                assertEquals(TopologyBlockingCall.WORKER_COUNT, blockStarts.get())

                releaseWorkers.countDown()
                val results = (jobs.filter { it !== queued.first() }).map { it.await() }
                assertEquals(TopologyBlockingCall.WORKER_COUNT + TopologyBlockingCall.QUEUE_CAPACITY, results.size)
                assertTrue(results.all { it == 1 })
                assertEquals(TopologyBlockingCall.WORKER_COUNT + TopologyBlockingCall.QUEUE_CAPACITY, blockStarts.get())
                assertTrue(executor.queue.isEmpty())
            } finally {
                releaseWorkers.countDown()
                jobs.forEach { it.cancel() }
                jobs.joinAll()
                executor.shutdownNow()
                assertTrue(
                    withContext(Dispatchers.IO) { executor.awaitTermination(3, TimeUnit.SECONDS) },
                    "test executor should terminate after accepted work drains",
                )
            }
        }
    }

    @Test
    fun `cancellation between active check and executor enqueue removes the late queued task`() = runBlocking {
        supervisorScope {
            val executor = createTopologyBlockingExecutor()
            val releaseWorkers = CountDownLatch(1)
            val workersEntered = CountDownLatch(TopologyBlockingCall.WORKER_COUNT)
            val executeReached = CountDownLatch(1)
            val allowExecute = CountDownLatch(1)
            val blockStarts = AtomicInteger()
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Int>>()
            var raced: kotlinx.coroutines.Deferred<Int>? = null

            fun deadline() = OperationBudget.start(
                requirement = OperationRequirement.LOCAL_NETWORK,
                timeoutMillis = 60_000,
                maxConcurrentProbes = 1,
            ).deadline

            fun submitWorker(): kotlinx.coroutines.Deferred<Int> = async(Dispatchers.Default) {
                TopologyBlockingCall.runWithExecutor(executor, deadline()) {
                    blockStarts.incrementAndGet()
                    workersEntered.countDown()
                    releaseWorkers.await()
                    1
                }
            }.also(jobs::add)

            try {
                repeat(TopologyBlockingCall.WORKER_COUNT) { submitWorker() }
                assertTrue(
                    withContext(Dispatchers.IO) { workersEntered.await(3, TimeUnit.SECONDS) },
                    "all factory-built workers should be held before the raced submission",
                )

                val racedSubmission = async(Dispatchers.Default) {
                    TopologyBlockingCall.runWithExecutor(
                        executor = executor,
                        deadline = deadline(),
                        beforeExecuteSubmission = {
                            executeReached.countDown()
                            allowExecute.await()
                        },
                    ) {
                        blockStarts.incrementAndGet()
                        2
                    }
                }
                raced = racedSubmission
                assertTrue(
                    withContext(Dispatchers.IO) { executeReached.await(3, TimeUnit.SECONDS) },
                    "submission did not reach the cancellation barrier",
                )

                racedSubmission.cancel()
                allowExecute.countDown()
                racedSubmission.cancelAndJoin()
                assertTrue(
                    executor.queue.isEmpty(),
                    "the cancelled FutureTask must be removed even when enqueue follows cancellation",
                )
                assertEquals(TopologyBlockingCall.WORKER_COUNT, blockStarts.get())

                val recovered = async(Dispatchers.Default) {
                    TopologyBlockingCall.runWithExecutor(executor, deadline()) {
                        blockStarts.incrementAndGet()
                        3
                    }
                }.also(jobs::add)
                withTimeout(3_000) {
                    while (executor.queue.size != 1) kotlinx.coroutines.yield()
                }
                assertEquals(1, executor.queue.size, "the recovered request should consume exactly one queue slot")

                releaseWorkers.countDown()
                assertEquals(3, recovered.await())
                val workerResults = jobs.filter { it !== recovered }.map { it.await() }
                assertEquals(TopologyBlockingCall.WORKER_COUNT, workerResults.size)
                assertTrue(workerResults.all { it == 1 })
                assertEquals(TopologyBlockingCall.WORKER_COUNT + 1, blockStarts.get())
                assertTrue(executor.queue.isEmpty())
            } finally {
                allowExecute.countDown()
                releaseWorkers.countDown()
                raced?.cancelAndJoin()
                jobs.forEach { it.cancel() }
                jobs.joinAll()
                executor.shutdownNow()
                assertTrue(
                    withContext(Dispatchers.IO) { executor.awaitTermination(3, TimeUnit.SECONDS) },
                    "test executor should terminate after accepted work drains",
                )
            }
        }
    }
}
