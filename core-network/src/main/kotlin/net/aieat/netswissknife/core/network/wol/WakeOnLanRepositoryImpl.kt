package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.net.LocalNetworkBindingUnavailableException
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class WakeOnLanRepositoryImpl(
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket(null as SocketAddress?) },
) : WakeOnLanRepository {
    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String,
        port: Int,
        repeatCount: Int,
    ): NetworkResult<WolSendReport> =
        sendMagicPacket(
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
    ): NetworkResult<WolSendReport> =
        try {
            OperationRunner
                .run(operationSession) {
                    withContext(Dispatchers.IO) {
                        ensureOperationActive()
                        val payload = WolMagicPacket.build(macAddress)
                        // WOL destinations are IPv4 literals. Parsing octets directly avoids
                        // invoking the system resolver, which can block past operation cancellation.
                        val octets =
                            WolMagicPacket.parseIpv4(broadcastAddress) ?: throw InvalidBroadcastAddressException()
                        val address = InetAddress.getByAddress(octets)
                        ensureOperationActive()
                        val socket = socketFactory()
                        // Own the socket before network binding, local bind, or any send can block.
                        resources.register(SocketLease(socket))
                        val destinationIp = address.hostAddress
                        if (binder.shouldBind(destinationIp) && !binder.bindIfLocal(socket, destinationIp)) {
                            throw LocalNetworkBindingUnavailableException(destinationIp)
                        }
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
                timeoutResult(e)
            } else {
                throw e
            }
        } catch (e: OperationDeadlineExceededException) {
            timeoutResult(e)
        } catch (e: Exception) {
            failureResult(e)
        }

    private fun failureResult(e: Exception): NetworkResult<WolSendReport> =
        when {
            e is InvalidBroadcastAddressException -> {
                NetworkResult.error(ErrorCode.BROADCAST_INVALID, developerMessage = BROADCAST_MESSAGE, cause = e)
            }

            e is IllegalArgumentException -> {
                NetworkResult.error(
                    ErrorCode.MAC_INVALID,
                    developerMessage = e.message ?: "Invalid MAC address",
                    cause = e,
                )
            }

            e is LocalNetworkPermissionDeniedException -> {
                permissionDenied(e)
            }

            e is SecurityException -> {
                permissionDenied(LocalNetworkPermissionDeniedException(e))
            }

            e.isLocalNetworkUnavailable() -> {
                NetworkResult.error(
                    ErrorCode.WOL_NO_NETWORK,
                    developerMessage = "No local network is available for the magic packet",
                    cause = e,
                )
            }

            else -> {
                NetworkResult.error(
                    ErrorCode.WOL_SEND_FAILED,
                    developerMessage = "Failed to send magic packet: ${e.message}",
                    cause = e,
                )
            }
        }

    private fun permissionDenied(cause: Throwable): NetworkResult<WolSendReport> =
        NetworkResult.error(
            ErrorCode.LOCAL_NETWORK_PERMISSION_DENIED,
            developerMessage = "Local network permission denied",
            cause = cause,
        )

    private fun timeoutResult(cause: Throwable): NetworkResult<WolSendReport> =
        NetworkResult.error(
            ErrorCode.NETWORK_TIMEOUT,
            developerMessage = "Wake-on-LAN send timed out",
            cause = cause,
        )

    private class SocketLease(
        private val socket: DatagramSocket,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) socket.close()
        }
    }

    /** The chosen local network disappeared, or the OS has no route to send from (Wi-Fi/Ethernet off). */
    private fun Exception.isLocalNetworkUnavailable(): Boolean =
        this is LocalNetworkBindingUnavailableException ||
            this is NoRouteToHostException ||
            (this is SocketException && message?.contains("unreachable", ignoreCase = true) == true)

    private class InvalidBroadcastAddressException : IllegalArgumentException(BROADCAST_MESSAGE)

    private companion object {
        const val BROADCAST_MESSAGE = "Broadcast address must be an IPv4 address"
    }
}
