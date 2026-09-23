package net.aieat.netswissknife.core.network.tls

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.SocketAddress
import java.net.SocketException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSession
import java.math.BigInteger
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TlsInspectorRepositoryImplTest {

    private lateinit var repository: TlsInspectorRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = TlsInspectorRepositoryImpl()
    }

    @Test
    fun `inspect returns Error for blank host`() = runTest {
        val result = repository.inspect("", 443, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Expected Error for blank host but got $result")
    }

    @Test
    fun `inspect returns Error for whitespace-only host`() = runTest {
        val result = repository.inspect("   ", 443, 5_000)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect returns Error for port 0`() = runTest {
        val result = repository.inspect("example.com", 0, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Port 0 is out of range, expected Error")
    }

    @Test
    fun `inspect returns Error for port 65536`() = runTest {
        val result = repository.inspect("example.com", 65_536, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Port 65536 is out of range, expected Error")
    }

    @Test
    fun `inspect returns Error for negative port`() = runTest {
        val result = repository.inspect("example.com", -1, 5_000)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect returns Error for timeout below 500 ms`() = runTest {
        val result = repository.inspect("example.com", 443, 499)
        assertTrue(result is NetworkResult.Error,
            "Timeout < 500 ms should return Error")
    }

    @Test
    fun `inspect returns Error for timeout of zero`() = runTest {
        val result = repository.inspect("example.com", 443, 0)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect returns Error above maximum timeout`() = runTest {
        val result = repository.inspect("example.com", 443, 30_001)
        assertTrue(result is NetworkResult.Error)
        assertEquals("Timeout must be between 500 ms and 30 000 ms", (result as NetworkResult.Error).message)
    }

    @Test
    fun `inspect accepts valid port boundary 1`() = runTest {
        // Port 1 is valid — may succeed or fail for network reasons, but not an input-validation error
        val result = repository.inspect("   ", 1, 5_000)
        // Host is blank so it must still return Error (blank host check comes first)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect accepts valid port boundary 65535`() = runTest {
        // Port 65535 is valid — only the blank host should cause Error here
        val result = repository.inspect("", 65_535, 5_000)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `parent cancellation closes a blocked connect socket once`() = runTest {
        assertCancellationClosesBlockedSocket(blockedOperation = SocketOperation.CONNECT)
    }

    @Test
    fun `parent cancellation closes a blocked handshake socket once`() = runTest {
        assertCancellationClosesBlockedSocket(blockedOperation = SocketOperation.HANDSHAKE)
    }

    @Test
    fun `user stop session cancels blocked connect without late error`() = runTest {
        val session = TlsInspectorOperation.newSession(5_000)
        assertCancellationClosesBlockedSocket(SocketOperation.CONNECT, session) {
            session.cancel(CancellationReason.USER_STOP)
        }
        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
    }

    @Test
    fun `deadline closes blocked connect and maps timeout to Error`() = runTest {
        val blockedSocket = blockingSocket(SocketOperation.CONNECT)
        val repository = TlsInspectorRepositoryImpl().apply {
            socketFactory = TlsInspectorSocketFactory { blockedSocket.socket }
        }
        val session = TlsInspectorOperation.newSession(500)
        val result = AtomicReference<NetworkResult<TlsInspectorResult>?>(null)

        coroutineScope {
            val inspection = async(Dispatchers.IO) {
                result.set(repository.inspect("example.com", 443, 500, session))
            }
            withContext(Dispatchers.IO) {
                assertTrue(blockedSocket.entered.await(1, TimeUnit.SECONDS), "connect was not reached")
            }
            inspection.join()
        }

        assertTrue(result.get() is NetworkResult.Error)
        assertEquals("TLS inspection timed out", (result.get() as NetworkResult.Error).message)
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(1, blockedSocket.closeCount.get())
    }

    @Test
    fun `deadline Error preserves a throwing socket close failure`() = runTest {
        val closeFailure = IOException("deadline socket close failed")
        val blockedSocket = blockingSocket(SocketOperation.CONNECT, closeFailure)
        val repository = TlsInspectorRepositoryImpl().apply {
            socketFactory = TlsInspectorSocketFactory { blockedSocket.socket }
        }
        val session = TlsInspectorOperation.newSession(500)
        val result = AtomicReference<NetworkResult<TlsInspectorResult>?>(null)

        coroutineScope {
            val inspection = async(Dispatchers.IO) {
                result.set(repository.inspect("example.com", 443, 500, session))
            }
            withContext(Dispatchers.IO) {
                assertTrue(blockedSocket.entered.await(1, TimeUnit.SECONDS), "connect was not reached")
            }
            inspection.join()
        }

        assertTrue(result.get() is NetworkResult.Error)
        val error = result.get() as NetworkResult.Error
        assertEquals("TLS inspection timed out", error.message)
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(1, blockedSocket.closeCount.get())
        assertTrue(
            error.cause?.containsFailure(closeFailure.message!!) == true,
            "Error cause chain: ${error.cause?.describeFailureChain()}",
        )
    }

    @Test
    fun `deadline closes blocked handshake and maps timeout to Error`() = runTest {
        val blockedSocket = blockingSocket(SocketOperation.HANDSHAKE)
        val repository = TlsInspectorRepositoryImpl().apply {
            socketFactory = TlsInspectorSocketFactory { blockedSocket.socket }
        }
        val session = TlsInspectorOperation.newSession(500)
        val result = AtomicReference<NetworkResult<TlsInspectorResult>?>(null)

        coroutineScope {
            val inspection = async(Dispatchers.IO) {
                result.set(repository.inspect("example.com", 443, 500, session))
            }
            withContext(Dispatchers.IO) {
                assertTrue(blockedSocket.entered.await(1, TimeUnit.SECONDS), "handshake was not reached")
            }
            inspection.join()
        }

        assertTrue(result.get() is NetworkResult.Error)
        assertEquals("TLS inspection timed out", (result.get() as NetworkResult.Error).message)
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, session.cancellationReason)
        assertEquals(1, blockedSocket.closeCount.get())
    }

    @Test
    fun `ordinary connect failure returns Error and closes socket`() = runTest {
        val socket = mockk<SSLSocket>(relaxed = true)
        every { socket.connect(any<SocketAddress>(), any()) } throws SocketException("connection refused")
        every { socket.close() } throws java.io.IOException("socket close failed")
        val repository = TlsInspectorRepositoryImpl().apply {
            socketFactory = TlsInspectorSocketFactory { socket }
        }

        val result = repository.inspect("example.com", 443, 5_000)

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals("connection refused", error.message)
        assertTrue(
            error.cause?.containsFailure("socket close failed") == true,
            "Error cause chain: ${error.cause?.describeFailureChain()}",
        )
        verify(exactly = 1) { socket.close() }
    }

    @Test
    fun `successful inspection maps session and certificate`() = runTest {
        val socket = mockk<SSLSocket>(relaxed = true)
        val sslSession = mockk<SSLSession>()
        val certificate = mockk<X509Certificate>()
        val publicKey = mockk<PublicKey>()
        every { socket.sslParameters } returns SSLParameters()
        every { socket.session } returns sslSession
        every { sslSession.protocol } returns "TLSv1.3"
        every { sslSession.cipherSuite } returns "TLS_AES_128_GCM_SHA256"
        every { sslSession.peerCertificates } returns arrayOf(certificate)
        every { certificate.subjectX500Principal } returns X500Principal("CN=example.com,O=Example")
        every { certificate.issuerX500Principal } returns X500Principal("CN=Example CA,O=Example")
        every { certificate.notBefore } returns Date(1L)
        every { certificate.notAfter } returns Date(Long.MAX_VALUE)
        every { certificate.subjectAlternativeNames } returns listOf(listOf(2, "example.com"))
        every { certificate.serialNumber } returns BigInteger.ONE
        every { certificate.sigAlgName } returns "SHA256withRSA"
        every { certificate.publicKey } returns publicKey
        every { publicKey.algorithm } returns "RSA"
        every { certificate.encoded } returns byteArrayOf(1, 2, 3)
        val repository = TlsInspectorRepositoryImpl().apply {
            socketFactory = TlsInspectorSocketFactory { socket }
        }

        val result = repository.inspect("example.com", 443, 5_000)

        assertTrue(result is NetworkResult.Success)
        val actual = (result as NetworkResult.Success).data
        assertEquals("TLSv1.3", actual.tlsVersion)
        assertEquals("TLS_AES_128_GCM_SHA256", actual.cipherSuite)
        assertEquals("example.com", actual.chain.single().subjectCN)
        assertEquals(listOf("DNS:example.com"), actual.chain.single().sans)
        verify(exactly = 1) { socket.close() }
    }

    private suspend fun assertCancellationClosesBlockedSocket(
        blockedOperation: SocketOperation,
        session: OperationSession? = null,
        cancel: suspend () -> Unit = {},
    ) {
        val socket = mockk<SSLSocket>(relaxed = true)
        val operationEntered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closeCount = java.util.concurrent.atomic.AtomicInteger()
        val result = AtomicReference<NetworkResult<TlsInspectorResult>?>(null)
        every { socket.sslParameters } returns SSLParameters()
        every { socket.connect(any<SocketAddress>(), any()) } answers {
            if (blockedOperation == SocketOperation.CONNECT) blockUntilClosed(operationEntered, closed)
        }
        every { socket.startHandshake() } answers {
            if (blockedOperation == SocketOperation.HANDSHAKE) blockUntilClosed(operationEntered, closed)
        }
        every { socket.close() } answers {
            closeCount.incrementAndGet()
            closed.countDown()
        }
        val repository = TlsInspectorRepositoryImpl().apply {
            clock = MonotonicClock { System.nanoTime() }
            socketFactory = TlsInspectorSocketFactory { socket }
        }

        coroutineScope {
            val inspection = async(Dispatchers.IO) {
                result.set(
                    if (session == null) repository.inspect("example.com", 443, 5_000)
                    else repository.inspect("example.com", 443, 5_000, session)
                )
            }
            withContext(Dispatchers.IO) {
                assertTrue(operationEntered.await(1, TimeUnit.SECONDS), "TLS operation never started")
            }

            if (session == null) {
                inspection.cancelAndJoin()
            } else {
                cancel()
                inspection.join()
            }

            assertTrue(inspection.isCancelled)
            assertEquals(null, result.get(), "cancelled inspection must not publish a late result")
            assertEquals(1, closeCount.get())
            verify(exactly = 1) { socket.close() }
        }
    }

    private fun blockUntilClosed(entered: CountDownLatch, closed: CountDownLatch) {
        entered.countDown()
        if (!closed.await(5, TimeUnit.SECONDS)) throw AssertionError("socket close did not unblock TLS operation")
        throw SocketException("closed during cancellation")
    }

    private fun Throwable.containsFailure(message: String): Boolean =
        this.message == message || cause?.containsFailure(message) == true ||
            suppressed.any { it.containsFailure(message) }

    private fun Throwable.describeFailureChain(): String = buildString {
        append(this@describeFailureChain.javaClass.simpleName).append(':').append(this@describeFailureChain.message)
        this@describeFailureChain.cause?.let {
            append(" caused by [").append(it.describeFailureChain()).append(']')
        }
        this@describeFailureChain.suppressed.forEach {
            append(" suppressed [").append(it.describeFailureChain()).append(']')
        }
    }

    private fun blockingSocket(
        blockedOperation: SocketOperation,
        closeFailure: IOException? = null,
    ): BlockingTlsSocket {
        val socket = mockk<SSLSocket>(relaxed = true)
        val result = BlockingTlsSocket(socket)
        every { socket.sslParameters } returns SSLParameters()
        every { socket.connect(any<SocketAddress>(), any()) } answers {
            if (blockedOperation == SocketOperation.CONNECT) result.block()
        }
        every { socket.startHandshake() } answers {
            if (blockedOperation == SocketOperation.HANDSHAKE) result.block()
        }
        every { socket.close() } answers {
            result.closeCount.incrementAndGet()
            result.closed.countDown()
            closeFailure?.let { throw it }
        }
        return result
    }

    private class BlockingTlsSocket(
        val socket: SSLSocket,
    ) {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closeCount = java.util.concurrent.atomic.AtomicInteger()
        fun block() {
            entered.countDown()
            if (!closed.await(5, TimeUnit.SECONDS)) throw AssertionError("socket close did not unblock TLS operation")
            throw SocketException("closed during cancellation")
        }
    }

    private enum class SocketOperation { CONNECT, HANDSHAKE }
}
