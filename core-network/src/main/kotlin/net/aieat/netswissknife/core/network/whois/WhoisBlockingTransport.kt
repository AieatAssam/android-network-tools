package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStreamReader
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
    private const val MAX_RESPONSE_BYTES = 1_048_576

    private val workers = ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16),
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
        isDisallowedAddress: (InetAddress) -> Boolean
    ): Pair<Long, String> = suspendCancellableCoroutine { continuation ->
        val cancelled = AtomicBoolean(false)
        val activeSocket = AtomicReference<Socket?>(null)
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
                activeSocket.set(socket)
                if (cancelled.get()) closeQuietly(socket)
                checkNotCancelled(cancelled)

                socket.use {
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    checkNotCancelled(cancelled)
                    socket.soTimeout = timeoutMs
                    socket.getOutputStream().write("$query\r\n".toByteArray(Charsets.UTF_8))
                    // SO_TIMEOUT bounds idle reads; the repository's total deadline
                    // additionally bounds a peer that trickles bytes indefinitely.
                    val response = InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
                        .buffered()
                        .use { reader ->
                            val buffer = CharArray(RESPONSE_CHUNK_SIZE)
                            val text = StringBuilder()
                            var totalRead = 0
                            while (true) {
                                checkNotCancelled(cancelled)
                                val count = reader.read(buffer)
                                if (count == -1) break
                                totalRead += count
                                if (totalRead > MAX_RESPONSE_BYTES) {
                                    throw java.io.IOException("WHOIS response exceeded $MAX_RESPONSE_BYTES bytes")
                                }
                                text.append(buffer, 0, count)
                            }
                            text.toString()
                        }
                    result = Pair((System.nanoTime() - startedAt) / 1_000_000L, response)
                }
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally {
                activeSocket.getAndSet(null)?.let(::closeQuietly)
            }
            result?.let { if (continuation.isActive) continuation.resume(it) }
        }

        continuation.invokeOnCancellation {
            cancelled.set(true)
            activeSocket.getAndSet(null)?.let(::closeQuietly)
            task.cancel(true)
            workers.remove(task)
        }

        if (continuation.isActive) {
            try {
                workers.execute(task)
            } catch (rejected: java.util.concurrent.RejectedExecutionException) {
                if (continuation.isActive) continuation.resumeWithException(rejected)
            }
        } else {
            cancelled.set(true)
        }

    }

    private fun checkNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw java.util.concurrent.CancellationException("WHOIS operation cancelled")
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
            // Cancellation/cleanup is best effort; the active operation is already ending.
        }
    }
}
