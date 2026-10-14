package net.aieat.netswissknife.core.network.operation

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Runs blocking closes on a fixed-size worker set with a finite queue and caller wait bound. */
internal object OperationCleanupExecutor {
    const val WAIT_TIMEOUT_MILLIS = 1_000L

    private const val WORKER_COUNT = 4
    private const val QUEUE_CAPACITY = 64
    private val threadIds = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        ThreadFactory { task ->
            Thread(task, "operation-cleanup-${threadIds.incrementAndGet()}").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun submit(resources: ResourceScope): CompletableFuture<Unit> {
        val result = CompletableFuture<Unit>()
        try {
            executor.execute {
                try {
                    resources.close()
                    result.complete(Unit)
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                }
            }
        } catch (failure: Throwable) {
            result.completeExceptionally(failure)
        }
        return result
    }

    /** Returns null on success or the bounded wait/close failure for the operation to surface. */
    fun await(result: CompletableFuture<Unit>): Throwable? = try {
        result.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        null
    } catch (_: TimeoutException) {
        OperationCleanupTimeoutException(WAIT_TIMEOUT_MILLIS)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        OperationCleanupInterruptedException(interrupted)
    } catch (failure: ExecutionException) {
        failure.cause ?: failure
    }
}

class OperationCleanupTimeoutException(timeoutMillis: Long) :
    Exception("Operation cleanup did not finish within ${timeoutMillis}ms")

class OperationCleanupInterruptedException(cause: InterruptedException) :
    Exception("Interrupted while waiting for operation cleanup", cause)
