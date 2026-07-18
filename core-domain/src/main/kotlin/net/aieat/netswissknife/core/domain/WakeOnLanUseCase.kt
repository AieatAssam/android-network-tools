package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepository
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport

data class WakeOnLanParams(
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9,
)

class WakeOnLanUseCase(private val repository: WakeOnLanRepository) {

    suspend operator fun invoke(params: WakeOnLanParams): NetworkResult<WolSendReport> {
        val mac = params.macAddress.trim()
        if (mac.isBlank()) return NetworkResult.Error("MAC address must not be blank")
        if (!WolMagicPacket.isValidMac(mac)) return NetworkResult.Error("Invalid MAC address format")
        val broadcast = params.broadcastAddress.trim()
        if (broadcast.isBlank()) return NetworkResult.Error("Broadcast address must not be blank")
        if (params.port !in 0..65_535) return NetworkResult.Error("Port must be between 0 and 65535")
        return repository.sendMagicPacket(mac, broadcast, params.port)
    }
}
