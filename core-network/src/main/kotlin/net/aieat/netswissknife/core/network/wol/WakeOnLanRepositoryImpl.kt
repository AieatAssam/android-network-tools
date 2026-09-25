package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder

class WakeOnLanRepositoryImpl(
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket(null as SocketAddress?) },
) : WakeOnLanRepository {

    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String,
        port: Int,
        repeatCount: Int,
    ): NetworkResult<WolSendReport> = sendMagicPacket(
        macAddress,
        broadcastAddress,
        port,
        repeatCount,
        WakeOnLanOperation.newSession(),
    )

    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String,
        port: Int,
        repeatCount: Int,
        operationSession: OperationSession,
    ): NetworkResult<WolSendReport> = try {
        OperationRunner.run(operationSession) {
            withContext(Dispatchers.IO) {
                ensureOperationActive()
                val payload = WolMagicPacket.build(macAddress)
                // WOL destinations are IPv4 broadcast literals. Parsing octets directly avoids
                // invoking the system resolver, which can block past operation cancellation.
                val address = parseIpv4Address(broadcastAddress)
                ensureOperationActive()
                val socket = socketFactory()
                // Own the socket before network binding, local bind, or any send can block.
                resources.register(SocketLease(socket))
                if (binder.shouldBind(address.hostAddress)) binder.bind(socket)
                ensureOperationActive()
                socket.bind(InetSocketAddress(0))
                socket.broadcast = true
                repeat(repeatCount) {
                    ensureOperationActive()
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                }
                ensureOperationActive()
                WolSendReport(
                    macAddress = WolMagicPacket.normalizeMac(macAddress),
                    broadcastAddress = broadcastAddress,
                    port = port,
                    packetsSent = repeatCount,
                )
            }
        }.let { NetworkResult.Success(it) }
    } catch (e: OperationCancellationException) {
        if (e.reason == CancellationReason.DEADLINE_EXCEEDED) {
            NetworkResult.error(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Wake-on-LAN send timed out", cause = e)
        } else {
            throw e
        }
    } catch (e: OperationDeadlineExceededException) {
        NetworkResult.error(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Wake-on-LAN send timed out", cause = e)
    } catch (e: IllegalArgumentException) {
        NetworkResult.error(ErrorCode.MAC_INVALID, developerMessage = e.message ?: "Invalid MAC address", cause = e)
    } catch (e: LocalNetworkPermissionDeniedException) {
        NetworkResult.error(
            ErrorCode.LOCAL_NETWORK_PERMISSION_DENIED,
            developerMessage = "Local network permission denied",
            cause = e,
        )
    } catch (e: SecurityException) {
        NetworkResult.error(
            ErrorCode.LOCAL_NETWORK_PERMISSION_DENIED,
            developerMessage = "Local network permission denied",
            cause = LocalNetworkPermissionDeniedException(e),
        )
    } catch (e: Exception) {
        NetworkResult.error(
            ErrorCode.WOL_SEND_FAILED,
            developerMessage = "Failed to send magic packet: ${e.message}",
            cause = e,
        )
    }

    private class SocketLease(private val socket: DatagramSocket) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) socket.close()
        }
    }

    private fun parseIpv4Address(value: String): InetAddress {
        val octets = value.split('.', limit = 5)
        require(octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.all { it in '0'..'9' } &&
                octet.toIntOrNull()?.let { it in 0..255 } == true
        }) { "Broadcast address must be an IPv4 address" }
        return InetAddress.getByAddress(ByteArray(4) { index -> octets[index].toInt().toByte() })
    }
}
