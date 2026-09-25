package net.aieat.netswissknife.core.network.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/** Records sockets and exposes deterministic network-selection behavior for repository tests. */
class FakeNetworkBinder(
    private val shouldBindResult: Boolean = false,
    private val subnet: String? = null,
    override val isAvailable: Boolean = true,
    private val throwTcpBindSecurityException: Boolean = false,
    private val localDestinationIps: Set<String>? = null,
    private val tcpBindIfLocalReturnsFalse: Boolean = false,
) : NetworkBinder {
    val boundTcpSockets = mutableListOf<Socket>()
    val boundDatagramSockets = mutableListOf<DatagramSocket>()
    val tcpSocketBoundStatesAtBind = mutableListOf<Boolean>()
    val tcpSocketConnectedStatesAtBind = mutableListOf<Boolean>()
    val shouldBindDestinations = mutableListOf<String>()
    val atomicBindDestinations = mutableListOf<String>()
    val datagramSocketBoundStatesAtBind = mutableListOf<Boolean>()

    override fun localSubnet(): String? = subnet

    override fun shouldBind(destinationIp: String): Boolean {
        shouldBindDestinations += destinationIp
        return isLocalDestination(destinationIp)
    }

    override fun bindIfLocal(socket: Socket, destinationIp: String): Boolean {
        atomicBindDestinations += destinationIp
        if (!isLocalDestination(destinationIp) || tcpBindIfLocalReturnsFalse) return false
        bind(socket)
        return true
    }

    override fun bind(socket: Socket) {
        boundTcpSockets += socket
        tcpSocketBoundStatesAtBind += socket.isBound
        tcpSocketConnectedStatesAtBind += socket.isConnected
        if (throwTcpBindSecurityException) throw SecurityException("local network permission denied")
    }

    override fun bind(socket: DatagramSocket) {
        boundDatagramSockets += socket
        datagramSocketBoundStatesAtBind += socket.isBound
    }

    override fun localInterface(): NetworkInterface? = null

    override fun localAddress(): InetAddress? = null

    private fun isLocalDestination(destinationIp: String): Boolean =
        localDestinationIps?.contains(destinationIp) ?: shouldBindResult
}
