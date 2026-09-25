package net.aieat.netswissknife.core.network.tls

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun interface TlsInspectorSocketFactory {
    fun create(context: SSLContext): SSLSocket
}

class TlsInspectorRepositoryImpl(
    private val networkBinder: NetworkBinder = NoOpNetworkBinder,
) : TlsInspectorRepository {

    internal var clock: MonotonicClock = SystemMonotonicClock
    internal var wallClockMillis: () -> Long = System::currentTimeMillis
    internal var socketFactory: TlsInspectorSocketFactory = TlsInspectorSocketFactory { context ->
        context.socketFactory.createSocket() as SSLSocket
    }
    /** Tests can inject a fully fake engine; the socket-factory seam remains for cancellation regressions. */
    internal var handshakeEngine: TlsHandshakeEngine? = null

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): NetworkResult<TlsInspectorResult> =
        inspect(host, port, timeoutMs, TlsInspectorOperation.newSession(timeoutMs, clock))

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        options: TlsInspectorOptions,
    ): NetworkResult<TlsInspectorResult> =
        inspect(host, port, timeoutMs, TlsInspectorOperation.newSession(timeoutMs, clock), options)

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): NetworkResult<TlsInspectorResult> =
        inspect(host, port, timeoutMs, operationSession, TlsInspectorOptions())

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
        options: TlsInspectorOptions,
    ): NetworkResult<TlsInspectorResult> {
        if (host.isBlank()) return NetworkResult.error(ErrorCode.HOST_BLANK, developerMessage = "Host must not be blank")
        if (port !in 1..65_535) return NetworkResult.error(
            ErrorCode.PORT_OUT_OF_RANGE,
            developerMessage = "Port must be between 1 and 65535",
            args = listOf(port, 1, 65_535),
        )
        if (timeoutMs !in TlsInspectorOperation.MIN_TIMEOUT_MILLIS..TlsInspectorOperation.MAX_TIMEOUT_MILLIS) {
            return NetworkResult.error(
                ErrorCode.TIMEOUT_OUT_OF_RANGE,
                developerMessage = "Timeout must be between 500 ms and 30 000 ms",
                args = listOf(500, 30_000),
            )
        }
        if (options.expectedPinSha256 != null && !options.expectedPinSha256.matches(PIN_PATTERN)) {
            return NetworkResult.error(
                ErrorCode.TLS_PIN_INVALID,
                developerMessage = "Invalid SHA-256 pin",
                legacyCode = "TLS_PIN_INVALID",
                descriptionKey = "tls_pin_invalid",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                OperationRunner.run(operationSession) {
                    val engine = handshakeEngine ?: SocketTlsHandshakeEngine(socketFactory, networkBinder)
                    val primary = performHandshake(engine, host, port, timeoutMs, operationSession, protocol = null)
                    val certificates = primary.snapshot.peerCertificates
                    val trusted = checkTrust(certificates)
                    val now = wallClockMillis()
                    val hostnameMatches = certificates.firstOrNull()?.let { HostnameMatcher.matches(host, it) }
                    val chain = certificates.map { TlsCertificateParser.parse(it, now) }
                    val issues = ChainAnalyzer.analyze(certificates, trusted, now, hostnameMatches)
                    val leafPin = certificates.firstOrNull()?.let {
                        TlsCertificateParser.sha256Fingerprint(it.encoded).replace(":", "").uppercase()
                    }
                    val protocolProbes = if (options.probeProtocols) {
                        probeProtocols(engine, host, port, timeoutMs, operationSession)
                    } else {
                        null
                    }

                    NetworkResult.Success(
                        TlsInspectorResult(
                            host = host,
                            port = port,
                            tlsVersion = primary.snapshot.protocol,
                            cipherSuite = primary.snapshot.cipherSuite,
                            chain = chain,
                            isChainTrusted = trusted,
                            handshakeTimeMs = primary.handshakeTimeMs,
                            hostnameMatches = hostnameMatches,
                            chainIssues = issues,
                            connectTimeMs = primary.connectTimeMs,
                            alpn = primary.snapshot.alpn,
                            protocolSupport = protocolProbes?.support,
                            protocolProbeUnknown = protocolProbes?.unknown.orEmpty(),
                            protocolProbeNotTestable = protocolProbes?.notTestable.orEmpty(),
                            pinMatch = options.expectedPinSha256?.let { expected -> leafPin == expected },
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is OperationCancellationException &&
                    cancelled.reason == CancellationReason.DEADLINE_EXCEEDED
                ) {
                    return@withContext NetworkResult.error(ErrorCode.TLS_INSPECTION_FAILED, developerMessage = "TLS inspection timed out", cancelled)
                }
                throw cancelled
            } catch (failure: Exception) {
                when (operationSession.cancellationReason) {
                    null -> Unit
                    CancellationReason.DEADLINE_EXCEEDED ->
                        return@withContext NetworkResult.error(
                            ErrorCode.TLS_INSPECTION_FAILED,
                            developerMessage = "TLS inspection timed out",
                            cause = failure,
                        )
                    else -> throw OperationCancellationException(operationSession.cancellationReason!!, failure)
                }
                NetworkResult.error(
                    ErrorCode.TLS_INSPECTION_FAILED,
                    developerMessage = failure.message ?: "TLS inspection failed",
                    cause = failure,
                )
            }
        }
    }

    private suspend fun performHandshake(
        engine: TlsHandshakeEngine,
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
        protocol: String?,
    ): TimedHandshake {
        val connection = try {
            engine.openConnection(host, port, timeoutMs, protocol)
        } catch (failure: IllegalArgumentException) {
            // A protocol-specific IllegalArgumentException here occurs while configuring the
            // local socket, before any endpoint interaction. It means this provider cannot test
            // that candidate; errors after the connection opens remain ambiguous.
            if (protocol != null) throw ProtocolNotTestableException(protocol, failure)
            throw failure
        }
        operationSession.resources.register(connection)
        var failure: Throwable? = null
        try {
            ensureCurrentOperationActive()
            val connectStart = clock.nowNanos()
            try {
                connection.connect()
            } catch (connectFailure: Exception) {
                ensureCurrentOperationActive()
                throw connectFailure
            }
            ensureCurrentOperationActive()
            val connectTimeMs = clock.elapsedMillisSince(connectStart)

            val handshakeStart = clock.nowNanos()
            try {
                connection.handshake()
            } catch (handshakeFailure: Exception) {
                ensureCurrentOperationActive()
                throw handshakeFailure
            }
            ensureCurrentOperationActive()
            val handshakeTimeMs = clock.elapsedMillisSince(handshakeStart)
            val snapshot = connection.snapshot()
            ensureCurrentOperationActive()
            return TimedHandshake(snapshot, connectTimeMs, handshakeTimeMs)
        } catch (thrown: Throwable) {
            failure = thrown
            throw thrown
        } finally {
            // A concurrent cancellation owns cleanup once ResourceScope has started closing.
            if (operationSession.resources.release(connection)) {
                try {
                    connection.close()
                } catch (closeFailure: Throwable) {
                    if (failure == null) throw closeFailure
                    if (failure !== closeFailure) failure.addSuppressed(closeFailure)
                }
            }
        }
    }

    private suspend fun probeProtocols(
        engine: TlsHandshakeEngine,
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): ProtocolProbeResults {
        val localEnabled = engine.enabledProtocols()
        val results = linkedMapOf<String, Boolean>()
        val unknown = linkedSetOf<String>()
        val notTestable = CANDIDATE_PROTOCOLS.filterNot(localEnabled::contains).toMutableSet()
        CANDIDATE_PROTOCOLS.filter(localEnabled::contains).forEach { protocol ->
            try {
                val handshake = performHandshake(engine, host, port, timeoutMs, operationSession, protocol)
                if (handshake.snapshot.protocol == protocol) results[protocol] = true
                else unknown += protocol
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProtocolNotTestableException) {
                notTestable += failure.protocol
            } catch (failure: SSLException) {
                if (isProtocolVersionRejection(failure)) results[protocol] = false
                else unknown += protocol
            } catch (_: Exception) {
                // A connection or negotiation failure does not establish protocol support.
                unknown += protocol
            }
        }
        return ProtocolProbeResults(results, unknown, notTestable)
    }

    private fun isProtocolVersionRejection(failure: SSLException): Boolean {
        var current: Throwable? = failure
        while (current != null) {
            val message = current.message.orEmpty()
            if (PROTOCOL_VERSION_ALERT.containsMatchIn(message)) return true
            current = current.cause
        }
        return false
    }

    /** Validates [certs] against the JVM/Android trust store without a network call. */
    private fun checkTrust(certs: List<X509Certificate>): Boolean = try {
        if (certs.isEmpty()) return false
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: return false
        val authType = when (certs.firstOrNull()?.publicKey?.algorithm) {
            "EC" -> "EC"
            else -> "RSA"
        }
        tm.checkServerTrusted(certs.toTypedArray(), authType)
        true
    } catch (_: CertificateException) {
        false
    } catch (_: Exception) {
        false
    }

    private data class TimedHandshake(
        val snapshot: TlsHandshakeSnapshot,
        val connectTimeMs: Long,
        val handshakeTimeMs: Long,
    )

    private class ProtocolNotTestableException(
        val protocol: String,
        cause: IllegalArgumentException,
    ) : Exception("Local TLS provider cannot configure $protocol", cause)

    private data class ProtocolProbeResults(
        val support: Map<String, Boolean>,
        val unknown: Set<String>,
        val notTestable: Set<String>,
    )

    private companion object {
        val PIN_PATTERN = Regex("[0-9A-Fa-f]{64}")
        val PROTOCOL_VERSION_ALERT = Regex("fatal alert:\\s*protocol_version\\b", RegexOption.IGNORE_CASE)
        val CANDIDATE_PROTOCOLS = listOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
    }
}
