package net.aieat.netswissknife.core.network.net

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/** Platform seam for selecting and binding sockets to a chosen local network. */
interface NetworkBinder {
    val isAvailable: Boolean

    fun localSubnet(): String?

    /** Returns true only when the destination belongs to the selected local network. */
    fun shouldBind(destinationIp: String): Boolean

    fun bind(socket: Socket)

    fun bind(socket: DatagramSocket)

    fun localInterface(): NetworkInterface?

    fun localAddress(): InetAddress?
}

/** JVM-safe default: preserve the process default route and perform no binding. */
object NoOpNetworkBinder : NetworkBinder {
    override val isAvailable: Boolean = false

    override fun localSubnet(): String? = null

    override fun shouldBind(destinationIp: String): Boolean = false

    override fun bind(socket: Socket) = Unit

    override fun bind(socket: DatagramSocket) = Unit

    override fun localInterface(): NetworkInterface? = null

    override fun localAddress(): InetAddress? = null
}
