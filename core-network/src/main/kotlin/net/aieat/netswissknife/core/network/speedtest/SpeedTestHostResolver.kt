package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Runs platform name resolution off the caller and bounds uninterruptible resolver residue. */
internal class SpeedTestHostResolver(
    private val executor: ThreadPoolExecutor = SpeedTestHostResolutionWorkers.executor,
    private val resolveAddress: (String) -> InetAddress = InetAddress::getByName,
) {
    @OptIn(InternalCoroutinesApi::class)
    suspend fun resolve(
        host: String,
        operationSession: OperationSession,
    ): InetAddress {
        operationSession.budget.throwIfExpired()
        var registeredLease: ResolverLease? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val task = ResolverTask(host, resolveAddress, continuation)
                val lease = ResolverLease(task, executor)
                registeredLease = lease
                try {
                    operationSession.resources.register(lease)
                    continuation.invokeOnCancellation { lease.close() }
                    if (continuation.isActive) executor.execute(task)
                    // Close the cancellation-before-enqueue race if execute() ran after cancel.
                    if (task.isCancelled) executor.remove(task)
                } catch (failure: Exception) {
                    operationSession.resources.release(lease)
                    lease.close()
                    resumeFailure(continuation, failure)
                }
            }
        } finally {
            registeredLease?.let(operationSession.resources::release)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private class ResolverTask(
        host: String,
        resolveAddress: (String) -> InetAddress,
        private val continuation: CancellableContinuation<InetAddress>,
    ) : FutureTask<InetAddress>(Callable { resolveAddress(host) }) {
        override fun done() {
            if (isCancelled) return
            try {
                val token = continuation.tryResume(get()) ?: return
                continuation.completeResume(token)
            } catch (failure: ExecutionException) {
                resumeFailure(continuation, failure.cause ?: failure)
            } catch (failure: Exception) {
                resumeFailure(continuation, failure)
            }
        }
    }

    private class ResolverLease(
        private val task: FutureTask<*>,
        private val executor: ThreadPoolExecutor,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            task.cancel(true)
            executor.remove(task)
        }
    }
}

/** Shared production executor; both the adapter and its boundary tests use this factory. */
internal object SpeedTestHostResolutionWorkers {
    const val WORKER_COUNT = 2
    const val QUEUE_CAPACITY = 8

    private val sequence = AtomicInteger()
    val executor: ThreadPoolExecutor = createWorkerExecutor()

    fun createWorkerExecutor(): ThreadPoolExecutor =
        ThreadPoolExecutor(
            WORKER_COUNT,
            WORKER_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(QUEUE_CAPACITY),
            ThreadFactory { task ->
                Thread(task, "speed-test-dns-${sequence.incrementAndGet()}")
                    .apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )
}

@OptIn(InternalCoroutinesApi::class)
private fun <T> resumeFailure(
    continuation: CancellableContinuation<T>,
    failure: Throwable,
) {
    val token = continuation.tryResumeWithException(failure) ?: return
    continuation.completeResume(token)
}
