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
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateFactory
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

    /**
     * Opens a raw connection that sends a hand-built TLS 1.2 hello and only reads the server's
     * certificates. Used when the platform provider's hello is refused outright.
     */
    fun openCompatibilityConnection(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): TlsHandshakeConnection = throw UnsupportedOperationException("Compatibility capture is unavailable")

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
    private val context: SSLContext =
        newInspectionContext().apply {
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

    override fun openCompatibilityConnection(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): TlsHandshakeConnection = CompatibilityConnection(Socket(), host, port, timeoutMs, networkBinder)

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

    private fun configureTlsParameters(
        socket: SSLSocket,
        host: String,
    ) {
        try {
            val params = socket.sslParameters
            val normalizedHost = HostValidator.normalize(host)
            if (normalizedHost != null && !normalizedHost.contains(':') && !HostValidator.isValidIpv4(normalizedHost)) {
                // SNIHostName rejects a trailing DNS root dot. Normalize valid hostnames
                // first so absolute DNS names retain SNI instead of silently skipping it.
                params.serverNames = listOf(javax.net.ssl.SNIHostName(normalizedHost))
            }
            try {
                params.javaClass
                    .getMethod("setApplicationProtocols", Array<String>::class.java)
                    .invoke(params, arrayOf<Any>(arrayOf("h2", "http/1.1")))
            } catch (_: Exception) {
                // ALPN is unavailable on older Android providers; TLS inspection still works.
            }
            socket.sslParameters = params
        } catch (_: Exception) {
            // SNI/ALPN are best-effort, as before.
        }
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
            if (!transportConnected) connectBound(socket, host, port, timeoutMs, networkBinder)
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

        private fun applicationProtocol(socket: SSLSocket): String? =
            try {
                // Reflection keeps this adapter usable on Android versions before SSLSocket exposed ALPN.
                (socket.javaClass.getMethod("getApplicationProtocol").invoke(socket) as? String)
                    ?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
    }

    /** Raw-socket adapter for [TlsCompatibilityHello]; it never completes a handshake. */
    private class CompatibilityConnection(
        private val socket: Socket,
        private val host: String,
        private val port: Int,
        private val timeoutMs: Int,
        private val networkBinder: NetworkBinder,
    ) : TlsHandshakeConnection {
        private var flight: TlsCapturedFlight? = null

        override fun connect() {
            socket.soTimeout = timeoutMs
            connectBound(socket, host, port, timeoutMs, networkBinder)
        }

        override fun handshake() {
            socket.getOutputStream().apply {
                write(TlsCompatibilityHello.build(host))
                flush()
            }
            flight = TlsServerFlightReader.read(socket.getInputStream())
        }

        override fun snapshot(): TlsHandshakeSnapshot {
            val captured = checkNotNull(flight) { "Certificates were not captured" }
            val factory = CertificateFactory.getInstance("X.509")
            val certificates =
                captured.certificates.map { der ->
                    factory.generateCertificate(der.inputStream()) as X509Certificate
                }
            return TlsHandshakeSnapshot(
                protocol = captured.protocol,
                cipherSuite = captured.cipherSuite,
                peerCertificates = certificates,
            )
        }

        override fun close() = socket.close()
    }

    /**
     * Accepts every chain so the inspector can report expired, self-signed or mismatched
     * certificates instead of failing the handshake. This context never carries application
     * data: TlsInspectorRepositoryImpl validates each captured chain against the platform
     * trust store and reports the verdict to the user.
     */
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted( // NOSONAR: diagnostic-only context, see class KDoc.
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted( // NOSONAR: chains are validated after capture, see class KDoc.
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        /**
         * A TLSv1.3 context still negotiates TLS 1.2 and lets the protocol probe enable older
         * versions per socket. Providers before Android 10 lack TLS 1.3, so fall back to TLSv1.2.
         */
        fun newInspectionContext(): SSLContext =
            try {
                SSLContext.getInstance("TLSv1.3")
            } catch (_: NoSuchAlgorithmException) {
                SSLContext.getInstance("TLSv1.2")
            }
    }
}

/** Connects [socket], binding to the selected local network when the destination is on it. */
private fun connectBound(
    socket: Socket,
    host: String,
    port: Int,
    timeoutMs: Int,
    networkBinder: NetworkBinder,
) {
    if (!networkBinder.isAvailable) {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        return
    }
    val addresses = java.net.InetAddress.getAllByName(host)
    val localDestination =
        addresses.firstOrNull { address ->
            networkBinder.shouldBind(address.hostAddress ?: return@firstOrNull false)
        }
    val destination = localDestination ?: addresses.firstOrNull() ?: throw java.net.UnknownHostException(host)
    if (localDestination != null && !networkBinder.bindTcpSocketIfLocal(socket, destination.hostAddress)) {
        throw LocalNetworkBindingUnavailableException(destination.hostAddress)
    }
    socket.connect(InetSocketAddress(destination, port), timeoutMs)
}
