package net.aieat.netswissknife.core.network.testkit

import java.io.InputStream
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Socket fixture whose input stream blocks until the socket is closed.
 *
 * The read uses a latch rather than polling or sleeping, so tests can exercise
 * close-driven cancellation without depending on wall-clock delays.
 */
class ScriptedSocket(
    private val blockConnectUntilClosed: Boolean = false,
) : Socket() {
    private val closed = AtomicBoolean(false)
    private val closeCalls = AtomicInteger(0)
    private val closedSignal = CountDownLatch(1)

    /** Signals when the first read has entered its blocking section. */
    val blockingReadStarted = CountDownLatch(1)

    /** Signals when a caller attempts to connect; this fixture never opens a real socket. */
    val connectStarted = CountDownLatch(1)

    /** Number of calls made to [close], including repeated calls. */
    val closeCallCount: Int
        get() = closeCalls.get()

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        connectStarted.countDown()
        if (blockConnectUntilClosed) {
            try {
                closedSignal.await()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SocketException("Connect interrupted").also { it.initCause(interrupted) }
            }
        }
        if (closed.get()) throw SocketException("Socket closed during connect")
    }

    override fun getInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            blockingReadStarted.countDown()
            try {
                closedSignal.await()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SocketException("Read interrupted").also { it.initCause(interrupted) }
            }
            return -1
        }
    }

    override fun close() {
        closeCalls.incrementAndGet()
        if (closed.compareAndSet(false, true)) {
            closedSignal.countDown()
        }
        super.close()
    }

    override fun isClosed(): Boolean = closed.get()

    /** Waits for a test worker's read to reach the blocking section. */
    fun awaitBlockingRead(timeout: Long, unit: TimeUnit): Boolean =
        blockingReadStarted.await(timeout, unit)
}
