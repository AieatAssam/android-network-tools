package net.aieat.netswissknife.core.network.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WakeOnLanRepositoryImpl : WakeOnLanRepository {

    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String,
        port: Int,
        repeatCount: Int,
    ): NetworkResult<WolSendReport> = withContext(Dispatchers.IO) {
        try {
            val payload = WolMagicPacket.build(macAddress)
            val address = InetAddress.getByName(broadcastAddress)
            DatagramSocket().use { socket ->
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
        } catch (e: Exception) {
            NetworkResult.Error("Failed to send magic packet: ${e.message}", e)
        }
    }
}
