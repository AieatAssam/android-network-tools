package net.aieat.netswissknife.core.network.net

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * Socket factory for transports that own their connection lifecycle (such as OkHttp).
 * Binding is decided from the resolved connect address, immediately before connect, so public
 * requests keep the default route and local requests use the selected Android Network.
 */
internal class NetworkBindingSocketFactory(
    private val binder: NetworkBinder,
) : SocketFactory() {
    override fun createSocket(): Socket = NetworkBindingSocket(binder)

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = createConnectedSocket(InetSocketAddress(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = createConnectedSocket(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = createConnectedSocket(InetSocketAddress(host, port))

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = createConnectedSocket(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

    private fun createConnectedSocket(
        endpoint: InetSocketAddress,
        localEndpoint: InetSocketAddress? = null,
    ): Socket {
        val socket = createSocket()
        var connected = false
        try {
            if (localEndpoint != null) socket.bind(localEndpoint)
            socket.connect(endpoint)
            connected = true
            return socket
        } finally {
            if (!connected) runCatching { socket.close() }
        }
    }
}

private class NetworkBindingSocket(
    private val binder: NetworkBinder,
) : Socket() {
    override fun connect(
        endpoint: SocketAddress?,
        timeout: Int,
    ) {
        val address = endpoint as? InetSocketAddress
        val destinationIp = address?.address?.hostAddress
        if (destinationIp != null) binder.bindTcpSocket(this, destinationIp)
        super.connect(endpoint, timeout)
    }
}
