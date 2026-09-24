package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepository
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport
import net.aieat.netswissknife.core.network.operation.OperationSession

data class WakeOnLanParams(
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9,
) {
    companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
    }
}

class WakeOnLanUseCase(private val repository: WakeOnLanRepository) {

    suspend operator fun invoke(params: WakeOnLanParams): NetworkResult<WolSendReport> {
        return invokeValidated(params, operationSession = null)
    }

    suspend operator fun invoke(
        params: WakeOnLanParams,
        operationSession: OperationSession,
    ): NetworkResult<WolSendReport> = invokeValidated(params, operationSession)

    private suspend fun invokeValidated(
        params: WakeOnLanParams,
        operationSession: OperationSession?,
    ): NetworkResult<WolSendReport> {
        val mac = params.macAddress.trim()
        if (mac.isBlank()) return NetworkResult.Error("MAC address must not be blank")
        if (!WolMagicPacket.isValidMac(mac)) return NetworkResult.Error("Invalid MAC address format")
        val broadcast = params.broadcastAddress.trim()
        if (broadcast.isBlank()) return NetworkResult.Error("Broadcast address must not be blank")
        if (params.port !in WakeOnLanParams.MIN_PORT..WakeOnLanParams.MAX_PORT) {
            return NetworkResult.Error(
                "Port must be between ${WakeOnLanParams.MIN_PORT} and ${WakeOnLanParams.MAX_PORT}"
            )
        }
        return if (operationSession == null) {
            repository.sendMagicPacket(mac, broadcast, params.port)
        } else {
            repository.sendMagicPacket(mac, broadcast, params.port, operationSession = operationSession)
        }
    }
}
