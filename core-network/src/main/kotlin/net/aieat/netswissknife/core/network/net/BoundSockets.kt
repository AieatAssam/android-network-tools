package net.aieat.netswissknife.core.network.net

import java.io.Closeable
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress

/** Creates a TCP socket and applies local-network binding before it can connect. */
internal fun NetworkBinder.newTcpSocket(
    destinationIp: String,
    socketFactory: () -> Socket,
): Socket = createBoundSocket(socketFactory) { socket ->
    bindTcpSocket(socket, destinationIp)
}

/** Binds an already-created, still-unconnected TCP socket only for a selected local destination. */
internal fun NetworkBinder.bindTcpSocket(socket: Socket, destinationIp: String) {
    if (!shouldBind(destinationIp)) return
    if (!bindTcpSocketIfLocal(socket, destinationIp)) {
        throw LocalNetworkBindingUnavailableException(destinationIp)
    }
}

/** Performs the atomic platform bind and maps Android permission failures to a typed I/O error. */
internal fun NetworkBinder.bindTcpSocketIfLocal(socket: Socket, destinationIp: String): Boolean = try {
    bindIfLocal(socket, destinationIp)
} catch (permissionDenied: LocalNetworkPermissionDeniedException) {
    throw permissionDenied
} catch (error: SecurityException) {
    throw LocalNetworkPermissionDeniedException(error)
}

/**
 * Creates an unbound UDP socket and applies network binding before the caller binds a local port.
 * LAN multicast queries use QU responses, so they need the selected network even though the
 * multicast destination itself is outside the selected unicast subnet.
 */
internal fun NetworkBinder.newUdpSocket(
    destinationIp: String,
    socketFactory: () -> DatagramSocket = { DatagramSocket(null as SocketAddress?) },
    bindMulticastDestinations: Boolean = false,
    forceBind: Boolean = false,
): DatagramSocket = createBoundSocket(socketFactory) { socket ->
    val isMulticast = bindMulticastDestinations && runCatching {
        InetAddress.getByName(destinationIp).isMulticastAddress
    }.getOrDefault(false)
    if (forceBind || shouldBind(destinationIp) || isMulticast) bind(socket)
}

private fun <T : Closeable> NetworkBinder.createBoundSocket(
    socketFactory: () -> T,
    applyNetwork: (T) -> Unit,
): T {
    var socket: T? = null
    try {
        val created = socketFactory()
        socket = created
        applyNetwork(created)
        return created
    } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
        runCatching { socket?.close() }
        throw permissionDenied
    } catch (error: SecurityException) {
        runCatching { socket?.close() }
        throw LocalNetworkPermissionDeniedException(error)
    } catch (error: Exception) {
        runCatching { socket?.close() }
        throw error
    }
}
