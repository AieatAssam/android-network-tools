package net.aieat.netswissknife.core.network.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/** Records sockets and exposes deterministic network-selection behavior for repository tests. */
class FakeNetworkBinder(
    private val shouldBindResult: Boolean = false,
    private val subnet: String? = null,
    override val isAvailable: Boolean = true
) : NetworkBinder {
    val boundTcpSockets = mutableListOf<Socket>()
    val boundDatagramSockets = mutableListOf<DatagramSocket>()

    override fun localSubnet(): String? = subnet

    override fun shouldBind(destinationIp: String): Boolean = shouldBindResult

    override fun bind(socket: Socket) {
        boundTcpSockets += socket
    }

    override fun bind(socket: DatagramSocket) {
        boundDatagramSockets += socket
    }

    override fun localInterface(): NetworkInterface? = null

    override fun localAddress(): InetAddress? = null
}
