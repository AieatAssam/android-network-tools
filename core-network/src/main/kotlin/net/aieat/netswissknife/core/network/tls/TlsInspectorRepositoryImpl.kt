package net.aieat.netswissknife.core.network.tls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TlsInspectorRepositoryImpl : TlsInspectorRepository {

    override suspend fun inspect(
        host: String,
        port: Int,
        timeoutMs: Int
    ): NetworkResult<TlsInspectorResult> {
        if (host.isBlank()) return NetworkResult.Error("Host must not be blank")
        if (port !in 1..65_535) return NetworkResult.Error("Port must be between 1 and 65535")
        if (timeoutMs < 500) return NetworkResult.Error("Timeout must be at least 500 ms")

        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // ── Trust-all context for full cert chain inspection ─────────
                val trustAllCtx = SSLContext.getInstance("TLS")
                trustAllCtx.init(null, arrayOf(TrustAllManager), null)

                val sslSocket = (trustAllCtx.socketFactory.createSocket() as SSLSocket).apply {
                    soTimeout = timeoutMs
                    // Set SNI for hostname-based hosts (not bare IPs)
                    if (!host.contains(':') && !HostValidator.isValidIpv4(host)) {
                        try {
                            val params = sslParameters
                            params.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                            sslParameters = params
                        } catch (_: Exception) { /* SNI not critical */ }
                    }
                }

                try {
                    sslSocket.connect(InetSocketAddress(host, port), timeoutMs)
                    sslSocket.startHandshake()

                    val session      = sslSocket.session
                    val tlsVersion   = session.protocol
                    val cipherSuite  = session.cipherSuite
                    val handshakeMs  = System.currentTimeMillis() - startTime

                    val x509Certs = session.peerCertificates.map { it as X509Certificate }
                    val chain     = x509Certs.map { TlsCertificateParser.parse(it) }

                    // ── Check chain trust against the JVM/Android trust store ─
                    val isChainTrusted = checkTrust(x509Certs)

                    NetworkResult.Success(
                        TlsInspectorResult(
                            host             = host,
                            port             = port,
                            tlsVersion       = tlsVersion,
                            cipherSuite      = cipherSuite,
                            chain            = chain,
                            isChainTrusted   = isChainTrusted,
                            handshakeTimeMs  = handshakeMs
                        )
                    )
                } finally {
                    runCatching { sslSocket.close() }
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "TLS inspection failed", e)
            }
        }
    }

    /** Validates [certs] against the platform trust store without a network call. */
    private fun checkTrust(certs: List<X509Certificate>): Boolean = try {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: return false
        val authType = when (certs.firstOrNull()?.publicKey?.algorithm) {
            "EC"  -> "EC"
            else  -> "RSA"
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
