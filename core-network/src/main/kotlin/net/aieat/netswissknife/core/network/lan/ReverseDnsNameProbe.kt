package net.aieat.netswissknife.core.network.lan

import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Builds the bounded executor used for production reverse-DNS enrichment. */
internal fun createProductionReverseDnsWorkers(): ThreadPoolExecutor = ThreadPoolExecutor(
    2,
    2,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(4),
    ThreadFactory { task -> Thread(task, "lan-reverse-dns").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy(),
)

/** Reverse-DNS enrichment probe; DNS failure never affects host presence. */
class ReverseDnsNameProbe internal constructor(
    private val lookup: (String) -> String?,
    private val workers: ThreadPoolExecutor,
) : NameProbe {
    constructor() : this(DEFAULT_LOOKUP, sharedWorkers)

    constructor(lookup: (String) -> String?) : this(lookup, sharedWorkers)

    override suspend fun resolveName(ip: String, timeoutMs: Int): String? =
        withTimeoutOrNull(timeoutMs.coerceAtLeast(1).toLong()) {
            resolveOnBoundedWorker(ip)
        }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun resolveOnBoundedWorker(ip: String): String? =
        suspendCancellableCoroutine { continuation ->
            val cancellationRequested = AtomicBoolean(false)
            val task = FutureTask(Callable {
                try {
                    val result = lookup(ip)
                    continuation.tryResume(result)?.let(continuation::completeResume)
                } catch (failure: Throwable) {
                    continuation.tryResumeWithException(failure)?.let(continuation::completeResume)
                }
                Unit
            })

            continuation.invokeOnCancellation {
                cancellationRequested.set(true)
                task.cancel(true)
                workers.remove(task)
            }
            if (cancellationRequested.get()) return@suspendCancellableCoroutine

            try {
                workers.execute(task)
                if (cancellationRequested.get()) {
                    task.cancel(true)
                    workers.remove(task)
                }
            } catch (_: RejectedExecutionException) {
                continuation.tryResume(null)?.let(continuation::completeResume)
            }
        }

    private companion object {
        private val DEFAULT_LOOKUP: (String) -> String? = { ip ->
            try {
                InetAddress.getByName(ip).canonicalHostName.takeUnless { it == ip }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

        private val sharedWorkers = createProductionReverseDnsWorkers()
    }
}
