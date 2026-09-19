package net.aieat.netswissknife.core.network.lan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Small injectable UDP seam shared by LAN discovery protocols. */
fun interface UdpExchange {
    fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray?
}
object DefaultUdpExchange : UdpExchange {
    override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs.coerceAtLeast(1)
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(ip, port)))
                val responseBuffer = ByteArray(4096)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                response.data.copyOf(response.length)
            }
        }.getOrNull()
    }
}
