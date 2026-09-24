package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession

class WhoisCancellationTest {
    private val publicAddress = InetAddress.getByName("8.8.8.8")

    @Test
    fun `caller response byte budget limits the whole lookup`() = runTest {
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream(): InputStream = "12345678".byteInputStream()
            override fun close() = Unit
        }
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 7))
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory { socket },
        )

        val result = withContext(Dispatchers.IO) { repository.lookup("8.8.8.8", 1_000, session) }

        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("7 bytes"))
    }

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
    fun `caller session response cap is enforced by repository transport`() = runTest {
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream(): InputStream = "12345678".byteInputStream()
            override fun close() = Unit
        }
        val session = OperationSession(OperationBudget.start(maxResponseBytes = 7))
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory { socket },
        )

        val result = withContext(Dispatchers.IO) {
            repository.lookup("8.8.8.8", 1_000, session)
        }

        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("7 bytes"))
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
        assertEquals(1, socket.closeCalls.get(), "socket close must have one owner")
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
        assertEquals(1, socket.closeCalls.get(), "socket close must have one owner")
        assertTrue(lookup.isCancelled, "cancellation must not be converted to a NetworkResult.Error")
        assertEquals("8.8.8.8\r\n", socket.request.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `caller-owned USER_STOP session closes active socket and emits no later hop`() = runTest {
        val socket = BlockingReadSocket()
        val session = WhoisOperation.newSession(2_000)
        val repository = repositoryWith(socket)
        val progress = java.util.Collections.synchronizedList(mutableListOf<WhoisHop>())
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            repository.hopProgress.collect { progress += it }
        }
        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 2_000, session) }
        awaitLatch(socket.readEntered)

        session.cancel(CancellationReason.USER_STOP)
        val failure = try {
            lookup.await()
            null
        } catch (cancelled: OperationCancellationException) {
            cancelled
        }
        lookup.cancelAndJoin()
        collector.cancelAndJoin()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertEquals(CancellationReason.USER_STOP, failure?.reason)
        assertTrue(socket.closed.await(1, TimeUnit.SECONDS), "session stop should close the active socket")
        assertEquals(1, socket.closeCalls.get(), "session cleanup must close the socket exactly once")
        assertTrue(progress.isEmpty(), "cancellation during the first hop must not emit late progress")
    }

    @Test
    fun `operation deadline maps to the existing total deadline error`() = runTest {
        val socket = BlockingReadSocket()
        val repository = repositoryWith(socket)
        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 500) }

        val result = withContext(Dispatchers.IO) { lookup.await() }
        assertTrue(result is NetworkResult.Error)
        assertEquals("WHOIS lookup exceeded its total deadline", (result as NetworkResult.Error).message)
        assertTrue(socket.closed.await(1, TimeUnit.SECONDS))
        assertEquals(1, socket.closeCalls.get(), "deadline cleanup must close the socket once")
    }

    @Test
    fun `inline expired operation budget maps to total deadline without waiting for watcher`() = runTest {
        val now = AtomicLong(0L)
        val clock = MonotonicClock { now.get() }
        val socket = ResponseSocket(byteArrayOf())
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver {
                now.set(2_000_000_000L)
                publicAddress
            },
            socketFactory = WhoisSocketFactory { socket },
            clock = clock,
        )

        val result = withContext(Dispatchers.IO) { repository.lookup("8.8.8.8", 500) }
        assertTrue(result is NetworkResult.Error)
        assertEquals("WHOIS lookup exceeded its total deadline", (result as NetworkResult.Error).message)
        assertEquals(1, socket.closeCalls.get())
    }

    @Test
    fun `socket created after operation scope closes is closed by failed registration`() = runTest {
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val socket = CountingSocket()
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory {
                factoryEntered.countDown()
                // Model a socket factory that does not respond to interruption.
                while (true) {
                    try {
                        releaseFactory.await()
                        break
                    } catch (_: InterruptedException) {
                        // Keep the registration race deterministic.
                    }
                }
                socket
            }
        )
        val lookup = async(Dispatchers.Default) { repository.lookup("8.8.8.8", 500) }
        awaitLatch(factoryEntered)
        val result = withContext(Dispatchers.IO) { lookup.await() }
        assertTrue(result is NetworkResult.Error)
        assertEquals("WHOIS lookup exceeded its total deadline", (result as NetworkResult.Error).message)

        releaseFactory.countDown()
        assertTrue(socket.closed.await(1, TimeUnit.SECONDS), "late socket registration should close immediately")
        assertEquals(1, socket.closeCalls.get())
    }

    @Test
    fun `normal transport completion releases and closes its socket once`() = runTest {
        val socket = ResponseSocket(byteArrayOf())
        val repository = repositoryWith(socket)

        val result = withContext(Dispatchers.IO) { repository.lookup("8.8.8.8", 2_000) }
        assertTrue(result is NetworkResult.Success)
        assertEquals(1, socket.closeCalls.get())
    }

    @Test
    fun `cancellation during a later hop emits no progress after cancellation`() = runTest {
        val socket = BlockingReadSocket()
        val firstHop = ResponseSocket("% IANA WHOIS server\\n".toByteArray())
        val sockets = java.util.concurrent.atomic.AtomicInteger()
        val repository = WhoisRepositoryImpl(
            resolver = WhoisHostResolver { publicAddress },
            socketFactory = WhoisSocketFactory {
                if (sockets.getAndIncrement() == 0) firstHop else socket
            }
        )
        val progress = java.util.Collections.synchronizedList(mutableListOf<WhoisHop>())
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            repository.hopProgress.collect { progress += it }
        }
        val lookup = async(Dispatchers.Default) { repository.lookup("example.com", 2_000) }
        awaitLatch(socket.readEntered)
        lookup.cancelAndJoin()
        collector.cancelAndJoin()

        assertEquals(1, progress.size, "only the completed first hop may have emitted progress")
        assertTrue(socket.closed.await(1, TimeUnit.SECONDS))
        assertEquals(1, socket.closeCalls.get())
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
        val closeCalls = AtomicInteger()

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            connectEntered.countDown()
            closed.await()
            throw IOException("socket closed")
        }

        override fun close() {
            closeCalls.incrementAndGet()
            closed.countDown()
        }
    }

    private class BlockingReadSocket : Socket() {
        val readEntered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closeCalls = AtomicInteger()
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
            closeCalls.incrementAndGet()
            closed.countDown()
        }
    }

    private class CountingSocket : Socket() {
        val closed = CountDownLatch(1)
        val closeCalls = AtomicInteger()

        override fun close() {
            closeCalls.incrementAndGet()
            closed.countDown()
        }
    }

    private class ResponseSocket(private val response: ByteArray) : Socket() {
        val closeCalls = AtomicInteger()

        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = response.inputStream()
        override fun close() { closeCalls.incrementAndGet() }
    }
}
