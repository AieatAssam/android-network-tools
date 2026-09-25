package net.aieat.netswissknife.core.network.topology

import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.operation.OperationDeadline
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Isolates blocking platform/SNMP4J setup calls from operation coroutines. The pool is bounded
 * because cancelling a Future cannot guarantee that a native DNS resolver stops immediately.
 */
internal object TopologyBlockingCall {
    internal const val WORKER_COUNT = 4
    internal const val QUEUE_CAPACITY = 32

    private val workers = createTopologyBlockingExecutor()

    suspend fun <T> run(
        deadline: OperationDeadline,
        requestTimeoutMillis: Long = Long.MAX_VALUE,
        timeoutMessage: String = "Topology blocking call timed out",
        block: () -> T,
    ): T = runWithExecutor(
        executor = workers,
        deadline = deadline,
        requestTimeoutMillis = requestTimeoutMillis,
        timeoutMessage = timeoutMessage,
        block = block,
    )

    /** Test seam that keeps the exact production executor policy while isolating pool state. */
    internal suspend fun <T> runWithExecutor(
        executor: ThreadPoolExecutor,
        deadline: OperationDeadline,
        requestTimeoutMillis: Long = Long.MAX_VALUE,
        timeoutMessage: String = "Topology blocking call timed out",
        beforeExecuteSubmission: () -> Unit = {},
        block: () -> T,
    ): T {
        deadline.throwIfExpired()
        val remainingMillis = deadline.remainingTimeoutMillis()
        val waitMillis = minOf(remainingMillis, requestTimeoutMillis).coerceAtLeast(1L)

        try {
            val result = withTimeout(waitMillis) {
                suspendCancellableCoroutine { continuation ->
                    val task = object : FutureTask<T>({ block() }) {
                        override fun done() {
                            if (!continuation.isActive) return
                            try {
                                continuation.resume(get())
                            } catch (failure: ExecutionException) {
                                continuation.resumeWithException(failure.cause ?: failure)
                            } catch (failure: Throwable) {
                                continuation.resumeWithException(failure)
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        task.cancel(true)
                        executor.remove(task)
                    }
                    if (continuation.isActive) {
                        try {
                            beforeExecuteSubmission()
                            executor.execute(task)
                            // Cancellation may win after the active check and remove the task
                            // before execute enqueues it. Remove again after enqueue so a
                            // cancelled future cannot occupy a bounded queue slot indefinitely.
                            if (!continuation.isActive) executor.remove(task)
                        } catch (rejected: RejectedExecutionException) {
                            continuation.resumeWithException(rejected)
                        }
                    }
                }
            }
            deadline.throwIfExpired()
            return result
        } catch (timeout: TimeoutCancellationException) {
            if (deadline.remainingNanos() == 0L) {
                throw OperationDeadlineExceededException().also { it.initCause(timeout) }
            }
            throw SocketTimeoutException(timeoutMessage)
                .also { it.initCause(timeout) }
        }
    }
}

/** Same bounded pool factory used by the production singleton and saturation regression. */
internal fun createTopologyBlockingExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    TopologyBlockingCall.WORKER_COUNT,
    TopologyBlockingCall.WORKER_COUNT,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(TopologyBlockingCall.QUEUE_CAPACITY),
    ThreadFactory { runnable ->
        Thread(runnable, "topology-blocking-call").apply { isDaemon = true }
    },
    ThreadPoolExecutor.AbortPolicy(),
)
