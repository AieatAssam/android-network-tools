package net.aieat.netswissknife.core.network.ping

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.aieat.netswissknife.core.network.operation.OperationSession

/**
 * Bounds platform DNS/reachability calls that can ignore coroutine interruption. Active and
 * queued futures are scope-owned; cancellation removes queued work and interrupts active work.
 * A platform call may outlive interruption, so the fixed workers and finite queue cap residue.
 */
internal object PingBlockingCallExecutor {
    const val WORKER_COUNT = 2
    const val QUEUE_CAPACITY = 8

    private val sequence = java.util.concurrent.atomic.AtomicInteger()
    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable, "ping-blocking-${sequence.incrementAndGet()}").apply { isDaemon = true }
    }
    private val workers = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        threadFactory,
        ThreadPoolExecutor.AbortPolicy(),
    )

    internal val activeCallCount: Int get() = workers.activeCount
    internal val queuedCallCount: Int get() = workers.queue.size

    @OptIn(InternalCoroutinesApi::class)
    suspend fun <T> run(session: OperationSession, block: () -> T): T {
        var registeredLease: FutureLease<T>? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val task = ScopedFutureTask(block, continuation)
                val lease = FutureLease(task, workers)
                registeredLease = lease
                try {
                    session.resources.register(lease)
                    continuation.invokeOnCancellation { lease.close() }
                    workers.execute(task)
                } catch (failure: Exception) {
                    session.resources.release(lease)
                    task.suppressCancellation()
                    lease.close()
                    resumeFailure(continuation, failure)
                }
            }
        } finally {
            registeredLease?.let(session.resources::release)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private class ScopedFutureTask<T>(
        block: () -> T,
        private val continuation: CancellableContinuation<T>,
    ) : FutureTask<T>(Callable(block)) {
        @Volatile
        private var cancellationSuppressed = false

        override fun done() {
            if (isCancelled) {
                if (!cancellationSuppressed) {
                    continuation.cancel(CancellationException("Ping blocking call cancelled"))
                }
                return
            }
            try {
                val token = continuation.tryResume(get()) ?: return
                continuation.completeResume(token)
            } catch (failure: ExecutionException) {
                resumeFailure(continuation, failure.cause ?: failure)
            } catch (failure: CancellationException) {
                continuation.cancel(failure)
            } catch (failure: Exception) {
                resumeFailure(continuation, failure)
            }
        }

        fun suppressCancellation() {
            cancellationSuppressed = true
        }
    }

    private class FutureLease<T>(
        private val task: FutureTask<T>,
        private val executor: ThreadPoolExecutor,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            task.cancel(true)
            executor.remove(task)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun <T> resumeFailure(continuation: CancellableContinuation<T>, failure: Throwable) {
        val token = continuation.tryResumeWithException(failure) ?: return
        continuation.completeResume(token)
    }
}
