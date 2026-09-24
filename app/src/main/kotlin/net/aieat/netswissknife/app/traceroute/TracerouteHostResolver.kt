package net.aieat.netswissknife.app.traceroute

import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation

/** Resolves hostnames away from the traceroute collector before the native engine is created. */
fun interface TracerouteHostResolver {
    suspend fun resolve(host: String, operationSession: OperationSession): String
}

/**
 * Uses the process-bounded name-service pool shared with reverse DNS. Java's platform resolver
 * may ignore interruption, so the caller is cancellable while permanently stuck lookups remain
 * constrained by a fixed worker and queue limit.
 */
internal class BoundedTracerouteHostResolver(
    private val executor: ThreadPoolExecutor = TracerouteNameResolutionWorkers.executor,
    private val resolver: (String) -> String = ::resolveAddress,
) : TracerouteHostResolver {
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun resolve(host: String, operationSession: OperationSession): String {
        // Literal parsing is local and avoids spending a resolver worker for IP targets.
        if (HostValidator.isValidIpv4(host)) return host
        if (HostValidator.isValidIpv6(host)) return host.removePrefix("[").removeSuffix("]")

        var leaseForCleanup: HostResolutionLease? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val task = HostResolutionFutureTask(host, resolver, continuation)
                val lease = HostResolutionLease(task, executor)
                leaseForCleanup = lease
                try {
                    operationSession.resources.register(lease)
                    continuation.invokeOnCancellation { lease.close() }
                    executor.execute(task)
                    if (task.isCancelled) executor.remove(task)
                } catch (failure: RejectedExecutionException) {
                    operationSession.resources.release(lease)
                    lease.close()
                    resumeHostFailure(continuation, failure)
                } catch (failure: Exception) {
                    operationSession.resources.release(lease)
                    lease.close()
                    resumeHostFailure(continuation, failure)
                }
            }
        } finally {
            leaseForCleanup?.let(operationSession.resources::release)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private class HostResolutionFutureTask(
        host: String,
        resolver: (String) -> String,
        private val continuation: CancellableContinuation<String>,
    ) : FutureTask<String>(Callable { resolver(host) }) {
        override fun done() {
            if (isCancelled) return
            try {
                val token = continuation.tryResume(get()) ?: return
                continuation.completeResume(token)
            } catch (failure: ExecutionException) {
                resumeHostFailure(continuation, failure.cause ?: failure)
            } catch (failure: Exception) {
                resumeHostFailure(continuation, failure)
            }
        }
    }

    private class HostResolutionLease(
        private val task: FutureTask<*>,
        private val executor: ThreadPoolExecutor,
    ) : AutoCloseable {
        override fun close() {
            task.cancel(true)
            executor.remove(task)
        }
    }

    companion object {
        private fun resolveAddress(host: String): String =
            requireNotNull(InetAddress.getByName(host).hostAddress) {
                "Hostname did not resolve to an address"
            }
    }
}

@OptIn(InternalCoroutinesApi::class)
private fun resumeHostFailure(
    continuation: CancellableContinuation<String>,
    failure: Throwable,
) {
    val token = continuation.tryResumeWithException(
        if (failure is CancellationException) IllegalStateException("Hostname lookup failed", failure) else failure,
    ) ?: return
    continuation.completeResume(token)
}

/** Stable upper bound for an individual hostname lookup, including unbounded caller sessions. */
internal const val MAX_HOSTNAME_RESOLUTION_WAIT_MILLIS = TracerouteOperation.MAX_HOST_RESOLUTION_WAIT_MILLIS
