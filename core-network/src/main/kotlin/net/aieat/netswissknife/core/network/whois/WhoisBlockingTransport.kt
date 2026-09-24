package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.aieat.netswissknife.core.network.operation.OperationResourcesContext
import net.aieat.netswissknife.core.network.operation.ResourceScope
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface WhoisHostResolver {
    fun resolve(host: String): InetAddress
}

object InetAddressWhoisHostResolver : WhoisHostResolver {
    override fun resolve(host: String): InetAddress = InetAddress.getByName(host)
}

fun interface WhoisSocketFactory {
    fun create(): Socket
}

/**
 * Runs the blocking JDK socket and resolver calls on a bounded worker pool and
 * bridges them to cancellable coroutines. Closing the active socket interrupts
 * connect/read promptly. Platform DNS resolution is not reliably interruptible;
 * cancellation removes queued work and returns to the caller promptly, while a
 * resolver already stuck inside the OS may occupy one bounded worker until it
 * returns. A finite queue prevents that limitation from creating unbounded threads
 * or queued work.
 */
internal object WhoisBlockingTransport {
    private const val RESPONSE_CHUNK_SIZE = 8192
    private const val WORKER_COUNT = 2
    private const val QUEUE_CAPACITY = 16

    private val workers = createWorkerExecutor()

    /** The production pool factory is shared with tests so its hard bounds stay observable. */
    internal fun createWorkerExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        ThreadFactory { task -> Thread(task, "whois-io").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    suspend fun query(
        host: String,
        query: String,
        port: Int,
        timeoutMs: Int,
        resolver: WhoisHostResolver,
        socketFactory: WhoisSocketFactory,
        isDisallowedAddress: (InetAddress) -> Boolean,
        responseBudget: WhoisResponseBudget = WhoisResponseBudget(
            net.aieat.netswissknife.core.network.operation.OperationBudget.DEFAULT_MAX_RESPONSE_BYTES,
        ),
        executor: ThreadPoolExecutor = workers,
    ): Pair<Long, String> {
        val resources = currentCoroutineContext()[OperationResourcesContext]?.resources
        return suspendCancellableCoroutine { continuation ->
            val cancelled = AtomicBoolean(false)
            val activeSocket = AtomicReference<SocketLease?>(null)
            val task = FutureTask<Unit> {
                var result: Pair<Long, String>? = null
                try {
                    val startedAt = System.nanoTime()
                    val address = resolver.resolve(host)
                    checkNotCancelled(cancelled)
                    if (isDisallowedAddress(address)) {
                        throw java.io.IOException("Refused to connect to non-public WHOIS referral address: $host")
                    }

                    val socket = socketFactory.create()
                    val lease = SocketLease(socket)
                    resources?.register(lease)
                    activeSocket.set(lease)
                    checkNotCancelled(cancelled)

                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    checkNotCancelled(cancelled)
                    socket.soTimeout = timeoutMs
                    socket.getOutputStream().write("$query\r\n".toByteArray(Charsets.UTF_8))
                    // SO_TIMEOUT bounds idle reads; the repository's total deadline
                    // additionally bounds a peer that trickles bytes indefinitely.
                    val input = socket.getInputStream()
                    val buffer = ByteArray(RESPONSE_CHUNK_SIZE)
                    val response = ByteArrayOutputStream()
                    while (true) {
                        checkNotCancelled(cancelled)
                        val count = input.read(buffer)
                        if (count == -1) break
                        responseBudget.consume(count)
                        response.write(buffer, 0, count)
                    }
                    result = Pair(
                        (System.nanoTime() - startedAt) / 1_000_000L,
                        String(response.toByteArray(), Charsets.UTF_8),
                    )
                } catch (failure: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                } finally {
                    closeWorkerOwned(activeSocket.getAndSet(null), resources)
                }
                result?.let { if (continuation.isActive) continuation.resume(it) }
            }

            continuation.invokeOnCancellation {
                cancelled.set(true)
                closeWorkerOwned(activeSocket.getAndSet(null), resources)
                task.cancel(true)
                executor.remove(task)
            }

            if (continuation.isActive) {
                try {
                    executor.execute(task)
                } catch (rejected: java.util.concurrent.RejectedExecutionException) {
                    if (continuation.isActive) continuation.resumeWithException(rejected)
                }
                // Cancellation can win after invokeOnCancellation's remove() and before
                // execute() enqueues the task. Recheck after submission to close that gap.
                if (cancelled.get()) {
                    task.cancel(true)
                    executor.remove(task)
                }
            } else {
                cancelled.set(true)
            }

        }
    }

    private fun checkNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw java.util.concurrent.CancellationException("WHOIS operation cancelled")
        }
    }

    private fun closeWorkerOwned(lease: SocketLease?, resources: ResourceScope?) {
        if (lease == null) return
        // If release loses to scope closure, ResourceScope owns the close. This prevents
        // cancellation callback, operation cleanup, and worker finally from competing.
        if (resources == null || resources.release(lease)) lease.close()
    }

    private class SocketLease(private val socket: Socket) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                socket.close()
            } catch (_: Exception) {
                // Cancellation/cleanup is best effort; the active operation is already ending.
            }
        }
    }

}

/** Shared across the referral chain so one session cannot exceed its total response cap. */
internal class WhoisResponseBudget(private val maxBytes: Long) {
    private val consumed = AtomicLong()

    init {
        require(maxBytes > 0) { "Maximum WHOIS response bytes must be positive" }
    }

    fun consume(bytes: Int) {
        val total = consumed.addAndGet(bytes.toLong())
        if (total > maxBytes) {
            throw java.io.IOException("WHOIS response exceeded $maxBytes bytes")
        }
    }
}
