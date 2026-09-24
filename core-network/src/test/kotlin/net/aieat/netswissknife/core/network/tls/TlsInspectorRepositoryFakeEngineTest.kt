package net.aieat.netswissknife.core.network.tls

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.MonotonicClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException

class TlsInspectorRepositoryFakeEngineTest {
    private val certificate = TlsTestCertificates.read("valid-leaf")

    @Test
    fun `inspect reports hostname chain ALPN timings and normalized pin without opening sockets`() = runTest {
        val ticker = AtomicLong()
        val engine = FakeEngine(
            onConnect = { ticker.addAndGet(10_000_000) },
            onHandshake = { ticker.addAndGet(20_000_000) },
        )
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            clock = MonotonicClock { ticker.get() }
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }
        val expectedPin = TlsCertificateParser.sha256Fingerprint(certificate.encoded).replace(":", "")

        val result = repository.inspect(
            host = "www.example.com",
            port = 443,
            timeoutMs = 5_000,
            options = TlsInspectorOptions(expectedPinSha256 = expectedPin),
        )

        assertTrue(result is NetworkResult.Success)
        val actual = (result as NetworkResult.Success).data
        assertEquals(true, actual.hostnameMatches)
        assertEquals(expectedPin, actual.chain.single().sha256Fingerprint.replace(":", ""))
        assertEquals(true, actual.pinMatch)
        assertFalse(actual.isChainTrusted)
        assertTrue(ChainIssue.UNTRUSTED in actual.chainIssues)
        assertEquals("h2", actual.alpn)
        assertEquals(10, actual.connectTimeMs)
        assertEquals(20, actual.handshakeTimeMs)
        assertTrue(actual.chain.single().pemEncoded.startsWith("-----BEGIN CERTIFICATE-----"))
        assertEquals(1, engine.openCount.get())
        assertEquals(1, engine.closedCount.get())
    }

    @Test
    fun `hostname mismatch and incorrect pin remain distinct findings`() = runTest {
        val engine = FakeEngine()
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "wrong.example.net",
            443,
            5_000,
            TlsInspectorOptions(expectedPinSha256 = "00".repeat(32)),
        ) as NetworkResult.Success

        assertEquals(false, result.data.hostnameMatches)
        assertEquals(false, result.data.pinMatch)
        assertTrue(ChainIssue.HOSTNAME_MISMATCH in result.data.chainIssues)
    }

    @Test
    fun `protocol probing uses a fresh connection and distinguishes endpoint rejection`() = runTest {
        val engine = FakeEngine(
            enabled = setOf("TLSv1", "TLSv1.2", "TLSv1.3"),
            rejected = setOf("TLSv1"),
        )
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true),
        )

        assertTrue(result is NetworkResult.Success)
        val support = (result as NetworkResult.Success).data.protocolSupport
        assertEquals(mapOf("TLSv1" to false, "TLSv1.2" to true, "TLSv1.3" to true), support)
        assertEquals(4, engine.openCount.get(), "base inspection plus one fresh connection per enabled candidate")
        assertEquals(engine.openCount.get(), engine.closedCount.get())
        assertEquals(listOf(null, "TLSv1", "TLSv1.2", "TLSv1.3"), engine.requestedProtocols)
    }

    @Test
    fun `unattemptable protocols are absent rather than reported unsupported`() = runTest {
        val engine = FakeEngine(enabled = setOf("TLSv1.3"))
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true),
        ) as NetworkResult.Success

        assertEquals(mapOf("TLSv1.3" to true), result.data.protocolSupport)
        assertEquals(setOf("TLSv1", "TLSv1.1", "TLSv1.2"), result.data.protocolProbeNotTestable)
    }

    @Test
    fun `ambiguous handshake failure is unknown rather than unsupported`() = runTest {
        val engine = FakeEngine(
            enabled = setOf("TLSv1.2", "TLSv1.3"),
            ambiguous = setOf("TLSv1.2"),
        )
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true),
        ) as NetworkResult.Success

        assertEquals(mapOf("TLSv1.3" to true), result.data.protocolSupport)
        assertEquals(setOf("TLSv1.2"), result.data.protocolProbeUnknown)
        assertEquals(setOf("TLSv1", "TLSv1.1"), result.data.protocolProbeNotTestable)
    }

    private inner class FakeEngine(
        private val enabled: Set<String> = emptySet(),
        private val rejected: Set<String> = emptySet(),
        private val ambiguous: Set<String> = emptySet(),
        private val onConnect: () -> Unit = {},
        private val onHandshake: () -> Unit = {},
    ) : TlsHandshakeEngine {
        val openCount = AtomicInteger()
        val closedCount = AtomicInteger()
        val requestedProtocols = mutableListOf<String?>()

        override fun enabledProtocols(): Set<String> = enabled

        override fun openConnection(
            host: String,
            port: Int,
            timeoutMs: Int,
            protocol: String?,
        ): TlsHandshakeConnection {
            openCount.incrementAndGet()
            requestedProtocols += protocol
            return object : TlsHandshakeConnection {
                override fun connect() {
                    if (host.isBlank() || port != 443 || timeoutMs <= 0) throw IOException("bad request")
                    onConnect()
                }

                override fun handshake() {
                    onHandshake()
                    if (protocol in rejected) throw SSLHandshakeException("Received fatal alert: protocol_version")
                    if (protocol in ambiguous) throw SSLHandshakeException("Received fatal alert: handshake_failure")
                }

                override fun snapshot(): TlsHandshakeSnapshot = TlsHandshakeSnapshot(
                    protocol = protocol ?: "TLSv1.3",
                    cipherSuite = "TLS_AES_128_GCM_SHA256",
                    peerCertificates = listOf(certificate),
                    alpn = "h2",
                )

                override fun close() {
                    closedCount.incrementAndGet()
                }
            }
        }
    }

}
