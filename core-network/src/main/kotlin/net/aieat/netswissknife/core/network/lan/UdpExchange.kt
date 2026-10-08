package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newUdpSocket
import net.aieat.netswissknife.core.network.operation.OperationResourcesContext
import net.aieat.netswissknife.core.network.operation.ResourceScope
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Small injectable UDP seam shared by LAN discovery protocols. */
fun interface UdpExchange {
    fun exchange(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
    ): ByteArray?
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
    private val delegate = NetworkBoundUdpExchange(NoOpNetworkBinder)

    override fun exchange(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
    ): ByteArray? = delegate.exchange(ip, port, payload, timeoutMs)

    internal fun withBinder(binder: NetworkBinder): UdpExchange =
        if (binder === NoOpNetworkBinder) {
            this
        } else {
            NetworkBoundUdpExchange(
                binder = binder,
                bindMulticastDestinations = true,
            )
        }

    override suspend fun exchangeCorrelated(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
        accepts: (CorrelatedUdpReply) -> Boolean,
    ): CorrelatedUdpReply? = delegate.exchangeCorrelated(ip, port, payload, timeoutMs, accepts)
}

internal class NetworkBoundUdpExchange(
    private val binder: NetworkBinder,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket(null) },
    private val bindMulticastDestinations: Boolean = false,
) : UdpExchange,
    CorrelatedUdpExchange {
    override fun exchange(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
    ): ByteArray? =
        try {
            openSocket(ip, null).use { socket ->
                socket.soTimeout = timeoutMs.coerceAtLeast(1)
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(ip, port)))
                val responseBuffer = ByteArray(4096)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                response.data.copyOf(response.length)
            }
        } catch (permissionDenied: LocalNetworkPermissionDeniedException) {
            throw permissionDenied
        } catch (bindingUnavailable: LocalNetworkBindingUnavailableException) {
            throw bindingUnavailable
        } catch (_: Exception) {
            null
        }

    override suspend fun exchangeCorrelated(
        ip: String,
        port: Int,
        payload: ByteArray,
        timeoutMs: Int,
        accepts: (CorrelatedUdpReply) -> Boolean,
    ): CorrelatedUdpReply? {
        ensureCurrentOperationActive()
        val deadlineNanos = System.nanoTime() + timeoutMs.coerceAtLeast(1) * 1_000_000L
        val resources = currentCoroutineContext()[OperationResourcesContext]?.resources
        return try {
            val socket = openSocket(ip, resources)
            try {
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(ip, port)))
                ensureCurrentOperationActive()
                val responseBuffer = ByteArray(4096)
                while (true) {
                    ensureCurrentOperationActive()
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) return null
                    // Poll at most every 100 ms so cancellation closes the socket promptly.
                    socket.soTimeout =
                        (
                            ((remainingNanos + 999_999L) / 1_000_000L)
                                .coerceAtMost(100L)
                        ).toInt().coerceAtLeast(1)
                    val response = DatagramPacket(responseBuffer, responseBuffer.size)
                    try {
                        socket.receive(response)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val candidate =
                        CorrelatedUdpReply(
                            sourceIp = response.address.hostAddress,
                            sourcePort = response.port,
                            payload = response.data.copyOf(response.length),
                        )
                    ensureCurrentOperationActive()
                    if (accepts(candidate)) return candidate
                }
                @Suppress("UNREACHABLE_CODE")
                null
            } finally {
                closeOrRelease(socket, resources)
            }
        } catch (error: Exception) {
            val shouldPropagateTypedFailure =
                error is CancellationException ||
                    error is LocalNetworkPermissionDeniedException ||
                    error is LocalNetworkBindingUnavailableException
            if (shouldPropagateTypedFailure || (error !is IOException && error !is IllegalArgumentException)) {
                throw error
            }
            null
        }
    }

    private fun openSocket(
        ip: String,
        resources: ResourceScope?,
    ): DatagramSocket {
        var socket: DatagramSocket? = null
        val boundSocket =
            try {
                binder.newUdpSocket(ip, {
                    socketFactory().also { created ->
                        socket = created
                        resources?.register(created)
                    }
                }, bindMulticastDestinations)
            } catch (failure: Throwable) {
                val created = socket
                if (created != null && (resources == null || resources.release(created))) {
                    // newUdpSocket closes a socket when applying the network binding fails. Release
                    // its operation registration here without issuing a second close. A fatal Error
                    // can bypass that helper's Exception cleanup, so preserve the outer cleanup for it.
                    if (failure !is Exception) runCatching { created.close() }
                }
                throw failure
            }
        try {
            boundSocket.bind(InetSocketAddress(0))
        } catch (error: SecurityException) {
            closeOrRelease(boundSocket, resources)
            throw LocalNetworkPermissionDeniedException(error)
        } catch (error: Exception) {
            closeOrRelease(boundSocket, resources)
            throw error
        }
        return boundSocket
    }

    private fun closeOrRelease(
        socket: DatagramSocket,
        resources: ResourceScope?,
    ) {
        if (resources == null || resources.release(socket)) socket.close()
    }
}
