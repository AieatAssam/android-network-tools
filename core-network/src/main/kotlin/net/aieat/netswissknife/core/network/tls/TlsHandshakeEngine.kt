package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.bindTcpSocketIfLocal
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Socket seam used by the TLS inspector; fake implementations never need to open a socket. */
internal interface TlsHandshakeEngine {
    /** Protocols enabled by the local TLS provider and suitable for a separate protocol probe. */
    fun enabledProtocols(): Set<String>

    fun openConnection(
        host: String,
        port: Int,
        timeoutMs: Int,
        protocol: String? = null,
    ): TlsHandshakeConnection

    /** Wraps an already-connected, network-bound transport without resolving [host] again. */
    fun openConnectionOverSocket(
        host: String,
        port: Int,
        timeoutMs: Int,
        transportSocket: Socket,
    ): TlsHandshakeConnection = throw UnsupportedOperationException("Bound transport wrapping is unavailable")
}

internal interface TlsHandshakeConnection : AutoCloseable {
    fun connect()
    fun handshake()
    fun snapshot(): TlsHandshakeSnapshot
}

internal data class TlsHandshakeSnapshot(
    val protocol: String,
    val cipherSuite: String,
    val peerCertificates: List<X509Certificate>,
    val alpn: String? = null,
)

/** Production SSLSocket adapter. It accepts chains during handshake so diagnostics can inspect them. */
internal class SocketTlsHandshakeEngine(
    private val socketFactory: TlsInspectorSocketFactory,
    private val networkBinder: NetworkBinder = NoOpNetworkBinder,
) : TlsHandshakeEngine {
    private val context: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(TrustAllManager), null)
    }

    override fun enabledProtocols(): Set<String> {
        val socket = socketFactory.create(context)
        return try {
            socket.enabledProtocols.toSet()
        } finally {
            socket.close()
        }
    }

    override fun openConnection(
        host: String,
        port: Int,
        timeoutMs: Int,
        protocol: String?,
    ): TlsHandshakeConnection {
        val socket = socketFactory.create(context)
        try {
            socket.soTimeout = timeoutMs
            if (protocol != null) socket.enabledProtocols = arrayOf(protocol)
            configureTlsParameters(socket, host)
            return SocketConnection(
                socket,
                host,
                port,
                timeoutMs,
                transportConnected = false,
                networkBinder = networkBinder,
            )
        } catch (failure: Throwable) {
            try {
                socket.close()
            } catch (closeFailure: Throwable) {
                if (failure !== closeFailure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    override fun openConnectionOverSocket(
        host: String,
        port: Int,
        timeoutMs: Int,
        transportSocket: Socket,
    ): TlsHandshakeConnection {
        val socket = context.socketFactory.createSocket(transportSocket, host, port, true) as SSLSocket
        try {
            socket.soTimeout = timeoutMs
            configureTlsParameters(socket, host)
            return SocketConnection(
                socket,
                host,
                port,
                timeoutMs,
                transportConnected = true,
                networkBinder = networkBinder,
            )
        } catch (failure: Throwable) {
            try {
                socket.close()
            } catch (closeFailure: Throwable) {
                if (failure !== closeFailure) failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private fun configureTlsParameters(socket: SSLSocket, host: String) {
        try {
            val params = socket.sslParameters
            if (!host.contains(':') && !HostValidator.isValidIpv4(host)) {
                params.serverNames = listOf(javax.net.ssl.SNIHostName(host))
            }
            try {
                params.javaClass
                    .getMethod("setApplicationProtocols", Array<String>::class.java)
                    .invoke(params, arrayOf<Any>(arrayOf("h2", "http/1.1")))
            } catch (_: Exception) {
                // ALPN is unavailable on older Android providers; TLS inspection still works.
            }
            socket.sslParameters = params
        } catch (_: Exception) { /* SNI/ALPN are best-effort, as before. */ }
    }

    private class SocketConnection(
        private val socket: SSLSocket,
        private val host: String,
        private val port: Int,
        private val timeoutMs: Int,
        private val transportConnected: Boolean,
        private val networkBinder: NetworkBinder,
    ) : TlsHandshakeConnection {
        override fun connect() {
            if (!transportConnected) {
                if (!networkBinder.isAvailable) {
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    return
                }
                val addresses = java.net.InetAddress.getAllByName(host)
                val localDestination = addresses.firstOrNull { address ->
                    networkBinder.shouldBind(address.hostAddress ?: return@firstOrNull false)
                }
                val destination = localDestination ?: addresses.firstOrNull()
                    ?: throw java.net.UnknownHostException(host)
                if (localDestination != null &&
                    !networkBinder.bindTcpSocketIfLocal(socket, destination.hostAddress)
                ) {
                    throw LocalNetworkBindingUnavailableException(destination.hostAddress)
                }
                socket.connect(InetSocketAddress(destination, port), timeoutMs)
            }
        }

        override fun handshake() {
            socket.startHandshake()
        }

        override fun snapshot(): TlsHandshakeSnapshot {
            val session = socket.session
            return TlsHandshakeSnapshot(
                protocol = session.protocol,
                cipherSuite = session.cipherSuite,
                peerCertificates = session.peerCertificates.map { it as X509Certificate },
                alpn = applicationProtocol(socket),
            )
        }

        override fun close() = socket.close()

        private fun applicationProtocol(socket: SSLSocket): String? = try {
            // Reflection keeps this adapter usable on Android versions before SSLSocket exposed ALPN.
            (socket.javaClass.getMethod("getApplicationProtocol").invoke(socket) as? String)
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
