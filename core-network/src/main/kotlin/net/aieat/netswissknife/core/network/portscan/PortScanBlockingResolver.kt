package net.aieat.netswissknife.core.network.portscan

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
import kotlinx.coroutines.withTimeoutOrNull
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs platform DNS resolution away from scan coroutines on a fixed, bounded worker pool.
 * Cancelling the caller interrupts the Future and removes queued work. Some platform resolvers
 * ignore interruption, so their running call may outlive the operation; the fixed worker and
 * queue bounds cap that residue and keep it from holding the caller past its operation deadline.
 */
internal object PortScanBlockingResolver {
    const val WORKER_COUNT = 2
    const val QUEUE_CAPACITY = 8
    const val DEFAULT_RESOLUTION_TIMEOUT_MILLIS =
        PortScanOperationBudget.SETUP_AND_RESOLUTION_ALLOWANCE_MILLIS

    private val sequence = java.util.concurrent.atomic.AtomicInteger()
    private val workers = createWorkerExecutor()
    internal val productionExecutor: ThreadPoolExecutor get() = workers

    internal fun createWorkerExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "port-scan-dns-${sequence.incrementAndGet()}").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    suspend fun resolve(
        session: OperationSession,
        executor: ThreadPoolExecutor = workers,
        resolutionTimeoutMillis: Long = DEFAULT_RESOLUTION_TIMEOUT_MILLIS,
        resolver: () -> java.net.InetAddress,
    ): java.net.InetAddress {
        require(resolutionTimeoutMillis > 0L) { "Resolution timeout must be positive" }
        session.budget.throwIfExpired()
        val phaseCapMillis = minOf(resolutionTimeoutMillis, DEFAULT_RESOLUTION_TIMEOUT_MILLIS)
        val sessionBudgetLimitsResolution = session.budget.hasDeadline &&
            session.budget.remainingNanos() <= phaseCapMillis * NANOS_PER_MILLISECOND
        val allowedMillis = minOf(
            phaseCapMillis,
            session.budget.remainingTimeoutMillis(),
        ).coerceAtLeast(1L)
        val resolved = withTimeoutOrNull(allowedMillis) { run(session, executor, resolver) }
        if (resolved != null) return resolved

        // Preserve a session's first-wins cancellation even if its reason raced the local timeout.
        when (val reason = session.cancellationReason) {
            CancellationReason.DEADLINE_EXCEEDED -> throw OperationDeadlineExceededException()
            CancellationReason.USER_STOP,
            CancellationReason.LIFECYCLE_PAUSE,
            CancellationReason.PERMISSION_DENIED,
            CancellationReason.NETWORK_LOST,
            CancellationReason.PARENT_CANCELLED -> throw OperationCancellationException(reason)
            null -> Unit
        }
        // A session deadline may tie the resolution phase cap. The shared budget remains
        // authoritative even if its watcher has not yet recorded the first-wins reason.
        session.budget.throwIfExpired()
        if (sessionBudgetLimitsResolution) throw OperationDeadlineExceededException()
        throw PortScanHostResolutionTimeoutException(allowedMillis)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun <T> run(
        session: OperationSession,
        executor: ThreadPoolExecutor,
        block: () -> T,
    ): T {
        var registeredLease: FutureLease<T>? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val task = ResolverFutureTask(block, continuation)
                val lease = FutureLease(task, executor)
                registeredLease = lease
                try {
                    session.resources.register(lease)
                    continuation.invokeOnCancellation { lease.close() }
                    if (continuation.isActive) executor.execute(task)
                    // Close the narrow cancellation-before-enqueue race. If cancellation arrived
                    // just before execute(), the first remove could not yet see this task.
                    if (task.isCancelled) executor.remove(task)
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
    private class ResolverFutureTask<T>(
        block: () -> T,
        private val continuation: CancellableContinuation<T>,
    ) : FutureTask<T>(Callable(block)) {
        @Volatile
        private var cancellationSuppressed = false

        override fun done() {
            if (isCancelled) {
                if (!cancellationSuppressed) {
                    continuation.cancel(CancellationException("Port scan host resolution cancelled"))
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

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}

class PortScanHostResolutionTimeoutException(timeoutMillis: Long) :
    java.io.IOException("Timed out resolving host within ${timeoutMillis} ms")
