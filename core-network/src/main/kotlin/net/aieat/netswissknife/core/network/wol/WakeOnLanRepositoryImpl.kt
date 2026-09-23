package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.newUdpSocket

class WakeOnLanRepositoryImpl(
    private val binder: NetworkBinder = NoOpNetworkBinder,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket(null as SocketAddress?) },
) : WakeOnLanRepository {

    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String,
        port: Int,
        repeatCount: Int,
    ): NetworkResult<WolSendReport> = withContext(Dispatchers.IO) {
        try {
            val payload = WolMagicPacket.build(macAddress)
            val address = InetAddress.getByName(broadcastAddress)
            binder.newUdpSocket(address.hostAddress, socketFactory).use { socket ->
                socket.bind(InetSocketAddress(0))
                socket.broadcast = true
                repeat(repeatCount) {
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                }
            }
            NetworkResult.Success(
                WolSendReport(
                    macAddress = WolMagicPacket.normalizeMac(macAddress),
                    broadcastAddress = broadcastAddress,
                    port = port,
                    packetsSent = repeatCount,
                )
            )
        } catch (e: IllegalArgumentException) {
            NetworkResult.Error(e.message ?: "Invalid MAC address", e)
        } catch (e: LocalNetworkPermissionDeniedException) {
            NetworkResult.Error("Local network permission denied", e)
        } catch (e: SecurityException) {
            NetworkResult.Error(
                "Local network permission denied",
                LocalNetworkPermissionDeniedException(e),
            )
        } catch (e: Exception) {
            NetworkResult.Error("Failed to send magic packet: ${e.message}", e)
        }
    }
}
