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
import javax.net.ssl.SSLProtocolException

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

    @Test
    fun `local protocol configuration failure is not testable rather than unknown`() = runTest {
        val engine = FakeEngine(
            enabled = setOf("TLSv1.2", "TLSv1.3"),
            configurationFailure = setOf("TLSv1.2"),
        )
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true),
        ) as NetworkResult.Success

        assertEquals(mapOf("TLSv1.3" to true), result.data.protocolSupport)
        assertEquals(setOf("TLSv1", "TLSv1.1", "TLSv1.2"), result.data.protocolProbeNotTestable)
        assertTrue(result.data.protocolProbeUnknown.isEmpty())
        assertEquals(engine.openCount.get() - 1, engine.closedCount.get())
    }

    @Test
    fun `explicit protocol version alert on SSLProtocolException is unsupported`() = runTest {
        val engine = FakeEngine(
            enabled = setOf("TLSv1.2", "TLSv1.3"),
            protocolVersionProtocolFailures = setOf("TLSv1.2"),
        )
        val repository = TlsInspectorRepositoryImpl().apply {
            handshakeEngine = engine
            wallClockMillis = { certificate.notBefore.time + 1_000 }
        }

        val result = repository.inspect(
            "www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true),
        ) as NetworkResult.Success

        assertEquals(mapOf("TLSv1.2" to false, "TLSv1.3" to true), result.data.protocolSupport)
        assertTrue(result.data.protocolProbeUnknown.isEmpty())
    }

    @Test
    fun `hostname mismatch on a trusted chain is not a trusted connection`() {
        runTest {
            val result = trustedInspection("wrong.host.badssl.com")

            assertEquals(true, result.isChainTrusted)
            assertEquals(false, result.hostnameMatches)
            assertTrue(ChainIssue.HOSTNAME_MISMATCH in result.chainIssues)
            assertFalse(result.isConnectionTrusted)
        }
    }

    @Test
    fun `matching hostname and pin on a trusted chain is a trusted connection`() {
        runTest {
            val options = TlsInspectorOptions(expectedPinSha256 = expectedPin())
            val result = trustedInspection("www.example.com", options)

            assertEquals(true, result.pinMatch)
            assertTrue(result.isConnectionTrusted)
        }
    }

    @Test
    fun `pin mismatch adds a finding and overrides a trusted chain`() {
        runTest {
            val options = TlsInspectorOptions(expectedPinSha256 = "00".repeat(32))
            val result = trustedInspection("www.example.com", options)

            assertEquals(false, result.pinMatch)
            assertTrue(ChainIssue.PIN_MISMATCH in result.chainIssues)
            assertFalse(result.isConnectionTrusted)
        }
    }

    @Test
    fun `expired leaf overrides a trusted chain`() {
        runTest {
            val result = trustedInspection("www.example.com", now = certificate.notAfter.time + 1_000)

            assertTrue(ChainIssue.EXPIRED in result.chainIssues)
            assertFalse(result.isConnectionTrusted)
        }
    }

    @Test
    fun `server refusing the hello falls back to certificate capture and is never fully trusted`() = runTest {
        val engine = FakeEngine(
            defaultProtocolFailure = SSLHandshakeException("OPENSSL_internal:SSLV3_ALERT_HANDSHAKE_FAILURE"),
        )
        val repository = repositoryWithEngine(engine, trusted = true)

        val result = repository.inspect("www.example.com", 443, 5_000, TlsInspectorOptions(probeProtocols = true))

        assertTrue(result is NetworkResult.Success)
        val data = (result as NetworkResult.Success).data
        assertFalse(data.handshakeCompleted)
        assertFalse(data.isConnectionTrusted)
        assertEquals(null, data.alpn)
        assertEquals(null, data.protocolSupport)
        assertEquals(1, data.chain.size)
        assertEquals(1, engine.compatibilityOpenCount.get())
    }

    @Test
    fun `non-alert failures never use the capture fallback`() = runTest {
        val engine = FakeEngine(handshakeFailure = SSLProtocolException("Connection reset"))
        val repository = repositoryWithEngine(engine)

        val result = repository.inspect("www.example.com", 443, 5_000)

        assertTrue(result is NetworkResult.Error)
        assertEquals(0, engine.compatibilityOpenCount.get())
    }

    @Test
    fun `failed capture reports the original rejection with a readable description`() = runTest {
        val engine = FakeEngine(
            handshakeFailure = SSLHandshakeException("Received fatal alert: handshake_failure"),
        )
        val repository = repositoryWithEngine(engine)

        val error = repository.inspect("192.0.2.10", 443, 5_000) as NetworkResult.Error

        assertEquals("tls_handshake_rejected", error.descriptionKey)
    }

    @Test
    fun `plaintext response on a TLS port is reported as a non-TLS port`() {
        runTest {
            listOf("wrong version number", "Unsupported or unrecognized SSL message").forEach { message ->
                val repository = repositoryWithEngine(FakeEngine(handshakeFailure = SSLProtocolException(message)))

                val error = repository.inspect("www.example.com", 443, 5_000) as NetworkResult.Error

                assertEquals("tls_not_tls_port", error.descriptionKey, message)
            }
        }
    }

    @Test
    fun `plaintext reply variants from OpenSSL, Conscrypt and JSSE map to the non-TLS port message`() {
        runTest {
            listOf(
                // OpenSSL-style token used by Android 8 (Conscrypt on OpenSSL): underscores, not spaces.
                "SSL handshake aborted: Failure in SSL library, usually a protocol error " +
                    "error:100000f7:SSL routines:OPENSSL_internal:WRONG_VERSION_NUMBER",
                // Conscrypt record-header parser.
                "Unable to parse TLS packet header",
                // JSSE wording for a plaintext peer.
                "Unrecognized SSL message, plaintext connection?",
            ).forEach { message ->
                val repository = repositoryWithEngine(FakeEngine(handshakeFailure = SSLProtocolException(message)))

                val error = repository.inspect("www.example.com", 443, 5_000) as NetworkResult.Error

                assertEquals("tls_not_tls_port", error.descriptionKey, message)
            }
        }
    }

    @Test
    fun `generic Handshake failed wrapper is classified by its native cause`() {
        runTest {
            // Android reports the top-level exception as "Handshake failed"; the protocol-level
            // reason is only on the cause.
            val failure =
                SSLHandshakeException("Handshake failed").apply {
                    initCause(SSLProtocolException("SSL handshake aborted: WRONG_VERSION_NUMBER"))
                }
            val repository = repositoryWithEngine(FakeEngine(handshakeFailure = failure))

            val error = repository.inspect("www.example.com", 443, 5_000) as NetworkResult.Error

            assertEquals("tls_not_tls_port", error.descriptionKey)
        }
    }

    @Test
    fun `unresolvable host uses the shared resolver sentence instead of the JVM message`() {
        runTest {
            val engine = FakeEngine(onConnect = { throw java.net.UnknownHostException("no-such-host.invalid") })
            val repository = repositoryWithEngine(engine)
            val expected = "Could not find an address for no-such-host.invalid. Check the name, then try again."

            val error = repository.inspect("no-such-host.invalid", 443, 5_000) as NetworkResult.Error

            assertEquals(expected, error.message)
        }
    }

    @Test
    fun `handshake_failure that capture cannot work around gets the readable description`() {
        runTest {
            val failure = SSLHandshakeException("Received fatal alert: handshake_failure")
            val repository = repositoryWithEngine(FakeEngine(handshakeFailure = failure))

            val error = repository.inspect("www.example.com", 443, 5_000) as NetworkResult.Error

            assertEquals("tls_handshake_rejected", error.descriptionKey)
        }
    }

    @Test
    fun `untrusted chain whose only finding is the unknown issuing root names that root`() {
        runTest {
            val result = repositoryWithEngine(FakeEngine(), trusted = false).inspect("www.example.com", 443, 5_000)

            val actual = (result as NetworkResult.Success).data
            assertEquals(listOf(ChainIssue.UNTRUSTED), actual.chainIssues)
            assertFalse(actual.isConnectionTrusted)
            assertEquals(commonNameOf(certificate.issuerX500Principal.name), actual.untrustedRootName)
        }
    }

    @Test
    fun `untrusted chain with another finding does not claim a specific missing root`() {
        runTest {
            val expiredAt = certificate.notAfter.time + 1_000
            val repository = repositoryWithEngine(FakeEngine(), now = expiredAt, trusted = false)
            val result = repository.inspect("www.example.com", 443, 5_000)

            val actual = (result as NetworkResult.Success).data
            assertTrue(ChainIssue.EXPIRED in actual.chainIssues)
            assertEquals(null, actual.untrustedRootName)
        }
    }

    private fun commonNameOf(distinguishedName: String): String {
        val match = Regex("CN=([^,]+)").find(distinguishedName)
        return match?.groupValues?.get(1) ?: distinguishedName
    }

    private fun expectedPin(): String = TlsCertificateParser.sha256Fingerprint(certificate.encoded).replace(":", "")

    private fun repositoryWithEngine(
        engine: FakeEngine,
        now: Long = certificate.notBefore.time + 1_000,
        trusted: Boolean = true,
    ): TlsInspectorRepositoryImpl {
        val repository = TlsInspectorRepositoryImpl()
        repository.handshakeEngine = engine
        repository.wallClockMillis = { now }
        repository.chainTrustOverride = { trusted }
        return repository
    }

    /** Forces a trusted chain so only hostname, pin and time decide the verdict. */
    private suspend fun trustedInspection(
        host: String,
        options: TlsInspectorOptions = TlsInspectorOptions(),
        now: Long = certificate.notBefore.time + 1_000,
    ): TlsInspectorResult {
        val repository = repositoryWithEngine(FakeEngine(), now = now, trusted = true)
        return (repository.inspect(host, 443, 5_000, options) as NetworkResult.Success).data
    }

    private inner class FakeEngine(
        private val enabled: Set<String> = emptySet(),
        private val rejected: Set<String> = emptySet(),
        private val ambiguous: Set<String> = emptySet(),
        private val configurationFailure: Set<String> = emptySet(),
        private val protocolVersionProtocolFailures: Set<String> = emptySet(),
        private val onConnect: () -> Unit = {},
        private val onHandshake: () -> Unit = {},
        private val handshakeFailure: Throwable? = null,
        private val defaultProtocolFailure: Throwable? = null,
    ) : TlsHandshakeEngine {
        val openCount = AtomicInteger()
        val closedCount = AtomicInteger()
        val requestedProtocols = mutableListOf<String?>()
        val compatibilityOpenCount = AtomicInteger()

        override fun openCompatibilityConnection(
            host: String,
            port: Int,
            timeoutMs: Int,
        ): TlsHandshakeConnection {
            compatibilityOpenCount.incrementAndGet()
            if (handshakeFailure != null) throw SSLHandshakeException("capture also refused")
            return object : TlsHandshakeConnection {
                override fun connect() = Unit
                override fun handshake() = Unit
                override fun snapshot() = TlsHandshakeSnapshot(
                    protocol = "TLSv1.2",
                    cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    peerCertificates = listOf(certificate),
                )
                override fun close() = Unit
            }
        }

        override fun enabledProtocols(): Set<String> = enabled

        override fun openConnection(
            host: String,
            port: Int,
            timeoutMs: Int,
            protocol: String?,
        ): TlsHandshakeConnection {
            openCount.incrementAndGet()
            requestedProtocols += protocol
            if (protocol in configurationFailure) throw IllegalArgumentException("unsupported locally")
            return object : TlsHandshakeConnection {
                override fun connect() {
                    if (host.isBlank() || port != 443 || timeoutMs <= 0) throw IOException("bad request")
                    onConnect()
                }

                override fun handshake() {
                    onHandshake()
                    handshakeFailure?.let { throw it }
                    if (protocol == null) defaultProtocolFailure?.let { throw it }
                    if (protocol in rejected) throw SSLHandshakeException("Received fatal alert: protocol_version")
                    if (protocol in protocolVersionProtocolFailures) {
                        throw SSLProtocolException("Received fatal alert: protocol_version")
                    }
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
