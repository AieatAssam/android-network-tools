package net.aieat.netswissknife.core.network.tls

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal fun interface TlsInspectorSocketFactory {
    fun create(context: SSLContext): SSLSocket
}

class TlsInspectorRepositoryImpl : TlsInspectorRepository {

    internal var clock: MonotonicClock = SystemMonotonicClock
    internal var socketFactory: TlsInspectorSocketFactory = TlsInspectorSocketFactory { context ->
        context.socketFactory.createSocket() as SSLSocket
    }

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int
    ): NetworkResult<TlsInspectorResult> =
        inspect(host, port, timeoutMs, TlsInspectorOperation.newSession(timeoutMs, clock))

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): NetworkResult<TlsInspectorResult> {
        if (host.isBlank()) return NetworkResult.Error("Host must not be blank")
        if (port !in 1..65_535) return NetworkResult.Error("Port must be between 1 and 65535")
        if (timeoutMs !in TlsInspectorOperation.MIN_TIMEOUT_MILLIS..TlsInspectorOperation.MAX_TIMEOUT_MILLIS) {
            return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        }

        return withContext(Dispatchers.IO) {
            try {
                OperationRunner.run(operationSession) {
                    val startTimeNanos = clock.nowNanos()

                    // Trust-all context for full certificate-chain inspection.
                    val trustAllCtx = SSLContext.getInstance("TLS")
                    trustAllCtx.init(null, arrayOf(TrustAllManager), null)

                    val sslSocket = socketFactory.create(trustAllCtx)
                    resources.register(sslSocket)
                    sslSocket.soTimeout = timeoutMs
                    // Set SNI for hostname-based hosts (not bare IPs).
                    if (!host.contains(':') && !HostValidator.isValidIpv4(host)) {
                        try {
                            val params = sslSocket.sslParameters
                            params.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                            sslSocket.sslParameters = params
                        } catch (_: Exception) { /* SNI is best-effort, as before. */ }
                    }

                    ensureCurrentOperationActive()
                    try {
                        sslSocket.connect(InetSocketAddress(host, port), timeoutMs)
                    } catch (failure: Exception) {
                        ensureCurrentOperationActive()
                        throw failure
                    }
                    ensureCurrentOperationActive()
                    try {
                        sslSocket.startHandshake()
                    } catch (failure: Exception) {
                        ensureCurrentOperationActive()
                        throw failure
                    }
                    ensureCurrentOperationActive()

                    val sslSession = sslSocket.session
                    val tlsVersion = sslSession.protocol
                    val cipherSuite = sslSession.cipherSuite
                    val handshakeMs = clock.elapsedMillisSince(startTimeNanos)
                    val x509Certs = sslSession.peerCertificates.map { it as X509Certificate }
                    val chain = x509Certs.map { TlsCertificateParser.parse(it) }
                    val isChainTrusted = checkTrust(x509Certs)

                    NetworkResult.Success(
                        TlsInspectorResult(
                            host = host,
                            port = port,
                            tlsVersion = tlsVersion,
                            cipherSuite = cipherSuite,
                            chain = chain,
                            isChainTrusted = isChainTrusted,
                            handshakeTimeMs = handshakeMs,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is OperationCancellationException &&
                    cancelled.reason == CancellationReason.DEADLINE_EXCEEDED
                ) {
                    return@withContext NetworkResult.Error("TLS inspection timed out", cancelled)
                }
                throw cancelled
            } catch (failure: Exception) {
                when (val reason = operationSession.cancellationReason) {
                    null -> Unit
                    CancellationReason.DEADLINE_EXCEEDED ->
                        return@withContext NetworkResult.Error("TLS inspection timed out", failure)
                    else -> throw OperationCancellationException(reason, failure)
                }
                NetworkResult.Error(failure.message ?: "TLS inspection failed", failure)
            }
        }
    }

    /** Validates [certs] against the JVM/Android trust store without a network call. */
    private fun checkTrust(certs: List<X509Certificate>): Boolean = try {
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

    /** A TrustManager that accepts every certificate chain without verification. */
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
