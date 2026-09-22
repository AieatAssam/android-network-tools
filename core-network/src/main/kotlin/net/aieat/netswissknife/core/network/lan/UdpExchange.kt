package net.aieat.netswissknife.core.network.lan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Small injectable UDP seam shared by LAN discovery protocols. */
fun interface UdpExchange {
    fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray?
}
data class CorrelatedUdpReply(
    val sourceIp: String,
    val sourcePort: Int,
    val payload: ByteArray,
)

fun interface CorrelatedUdpExchange {
    suspend fun exchangeCorrelated(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
        accepts: (CorrelatedUdpReply) -> Boolean,
    ): CorrelatedUdpReply?
}

object DefaultUdpExchange : UdpExchange, CorrelatedUdpExchange {
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

    override suspend fun exchangeCorrelated(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
        accepts: (CorrelatedUdpReply) -> Boolean,
    ): CorrelatedUdpReply? {
        val deadlineNanos = System.nanoTime() + timeoutMs.coerceAtLeast(1) * 1_000_000L
        return try {
            DatagramSocket().use { socket ->
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(ip, port)))
                val responseBuffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) return null
                    // Poll at most every 100 ms so cancellation closes the socket promptly.
                    socket.soTimeout = (((remainingNanos + 999_999L) / 1_000_000L)
                        .coerceAtMost(100L)).toInt().coerceAtLeast(1)
                    val response = DatagramPacket(responseBuffer, responseBuffer.size)
                    try {
                        socket.receive(response)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val candidate = CorrelatedUdpReply(
                        sourceIp = response.address.hostAddress,
                        sourcePort = response.port,
                        payload = response.data.copyOf(response.length),
                    )
                    currentCoroutineContext().ensureActive()
                    if (accepts(candidate)) return candidate
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
