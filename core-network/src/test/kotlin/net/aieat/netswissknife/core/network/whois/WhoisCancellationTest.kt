package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WhoisCancellationTest {
    private val publicAddress = InetAddress.getByName("8.8.8.8")

    @Test
    fun `resolver cancellation is rethrown instead of returned as an error`() = runTest {
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { throw CancellationException("resolver cancelled") }
        )

        try {
            // Use a real dispatcher so runTest cannot fast-forward the repository's
            // total-deadline timeout while its blocking transport worker is running.
            val result = withContext(Dispatchers.IO) { repository.lookup("8.8.8.8", 2_000) }
            throw AssertionError("expected resolver cancellation to propagate, but lookup returned $result")
        } catch (cancelled: CancellationException) {
            assertEquals("resolver cancelled", cancelled.message)
        }
    }

    @Test
    fun `cancellation during name resolution propagates without creating a socket`() = runTest {
        val resolverEntered = CountDownLatch(1)
        val resolverInterrupted = CountDownLatch(1)
        val socketCreated = AtomicBoolean(false)
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver {
                resolverEntered.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (interrupted: InterruptedException) {
                    resolverInterrupted.countDown()
                    throw IOException("resolver interrupted", interrupted)
                }
                publicAddress
            },
            socketFactory = WhoisSocketFactory {
                socketCreated.set(true)
                Socket()
            }
        )

        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 2_000) }
        awaitLatch(resolverEntered)
        lookup.cancelAndJoin()

        assertTrue(resolverInterrupted.await(1, TimeUnit.SECONDS), "cancel should interrupt the resolver worker")
        assertTrue(!socketCreated.get())
        assertTrue(lookup.isCancelled, "cancellation must not be converted to a NetworkResult.Error")
    }

    @Test
    fun `cancellation during connect closes the in-flight socket`() = runTest {
        val socket = BlockingConnectSocket()
        val repository = repositoryWith(socket)

        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 2_000) }
        awaitLatch(socket.connectEntered)
        lookup.cancelAndJoin()

        assertTrue(socket.closed.await(1, TimeUnit.SECONDS), "socket should close on cancellation")
        assertTrue(lookup.isCancelled, "cancellation must not be converted to a NetworkResult.Error")
    }

    @Test
    fun `cancellation during response read closes the in-flight socket`() = runTest {
        val socket = BlockingReadSocket()
        val repository = repositoryWith(socket)

        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 2_000) }
        awaitLatch(socket.readEntered)
        lookup.cancelAndJoin()

        assertTrue(socket.closed.await(1, TimeUnit.SECONDS), "socket should close on cancellation")
        assertTrue(lookup.isCancelled, "cancellation must not be converted to a NetworkResult.Error")
        assertEquals("8.8.8.8\r\n", socket.request.toString(Charsets.UTF_8.name()))
    }

    private fun repositoryWith(socket: Socket) = WhoisRepositoryImpl(
        resolver = WhoisHostResolver { publicAddress },
        socketFactory = WhoisSocketFactory { socket }
    )

    private suspend fun awaitLatch(latch: CountDownLatch) {
        val entered = withContext(Dispatchers.IO) { latch.await(2, TimeUnit.SECONDS) }
        assertTrue(entered, "operation did not start")
    }

    private class BlockingConnectSocket : Socket() {
        val connectEntered = CountDownLatch(1)
        val closed = CountDownLatch(1)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            connectEntered.countDown()
            closed.await()
            throw IOException("socket closed")
        }

        override fun close() {
            closed.countDown()
        }
    }

    private class BlockingReadSocket : Socket() {
        val readEntered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val request = ByteArrayOutputStream()

        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        override fun getOutputStream() = request
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                readEntered.countDown()
                closed.await()
                throw IOException("socket closed")
            }
        }

        override fun close() {
            closed.countDown()
        }
    }
}
