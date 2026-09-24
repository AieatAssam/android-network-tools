package net.aieat.netswissknife.app.traceroute

import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Optional reverse-DNS enrichment seam. The numeric hop is kept when this lookup returns null. */
fun interface TracerouteReverseDnsLookup {
    suspend fun lookup(ip: String, operationSession: OperationSession): String?
}

/**
 * Platform name-service lookups can ignore interruption. Keep them off the trace collector,
 * bound both active and queued work, and make the waiting caller promptly cancellable.
 */
internal class BoundedTracerouteReverseDnsLookup(
    private val executor: ThreadPoolExecutor = TracerouteNameResolutionWorkers.executor,
    private val resolver: (String) -> String? = ::resolveCanonicalHostname,
) : TracerouteReverseDnsLookup {
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun lookup(ip: String, operationSession: OperationSession): String? {
        var leaseForCleanup: ReverseDnsLease? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val task = ReverseDnsFutureTask(ip, resolver, continuation)
                val lease = ReverseDnsLease(task, executor)
                leaseForCleanup = lease
                try {
                    operationSession.resources.register(lease)
                    continuation.invokeOnCancellation { lease.close() }
                    executor.execute(task)
                    // Cancellation may win between registering the callback and queue insertion.
                    if (task.isCancelled) executor.remove(task)
                } catch (failure: RejectedExecutionException) {
                    operationSession.resources.release(lease)
                    lease.close()
                    resumeFailure(continuation, failure)
                } catch (failure: Exception) {
                    operationSession.resources.release(lease)
                    lease.close()
                    resumeFailure(continuation, failure)
                }
            }
        } finally {
            leaseForCleanup?.let(operationSession.resources::release)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private class ReverseDnsFutureTask(
        ip: String,
        resolver: (String) -> String?,
        private val continuation: CancellableContinuation<String?>,
    ) : FutureTask<String?>(Callable { resolver(ip) }) {
        override fun done() {
            // Cancellation is initiated by the waiting continuation or the owning session.
            // Do not replace its typed reason with a generic Future cancellation exception.
            if (isCancelled) return
            try {
                val token = continuation.tryResume(get()) ?: return
                continuation.completeResume(token)
            } catch (failure: ExecutionException) {
                val cause = failure.cause ?: failure
                // A resolver may itself throw CancellationException. That is an optional
                // lookup failure, unlike FutureTask cancellation from Stop/deadline (handled
                // by isCancelled above), so keep it from cancelling the traceroute collector.
                resumeFailure(
                    continuation,
                    if (cause is CancellationException) {
                        IllegalStateException("Reverse DNS lookup failed", cause)
                    } else {
                        cause
                    },
                )
            } catch (failure: Exception) {
                resumeFailure(continuation, failure)
            }
        }
    }

    private class ReverseDnsLease(
        private val task: FutureTask<*>,
        private val executor: ThreadPoolExecutor,
    ) : AutoCloseable {
        override fun close() {
            task.cancel(true)
            executor.remove(task)
        }
    }

    companion object {
        private fun resolveCanonicalHostname(ip: String): String? {
            val canonical = InetAddress.getByName(ip).canonicalHostName
            return canonical.takeUnless { it == ip }
        }
    }
}

internal object TracerouteNameResolutionWorkers {
    private val sequence = AtomicInteger()
    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable, "traceroute-reverse-dns-${sequence.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    // getCanonicalHostName may remain stuck in the platform resolver after interruption.
    // A fixed worker count and finite queue cap the permanent residue in that case.
    val executor = ThreadPoolExecutor(
        REVERSE_DNS_WORKER_COUNT,
        REVERSE_DNS_WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(REVERSE_DNS_QUEUE_CAPACITY),
        threadFactory,
        ThreadPoolExecutor.AbortPolicy(),
    )
}

internal const val REVERSE_DNS_WORKER_COUNT = 2
internal const val REVERSE_DNS_QUEUE_CAPACITY = 8

@OptIn(InternalCoroutinesApi::class)
private fun resumeFailure(
    continuation: CancellableContinuation<String?>,
    failure: Throwable,
) {
    val token = continuation.tryResumeWithException(failure) ?: return
    continuation.completeResume(token)
}
