package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepository
import net.aieat.netswissknife.core.network.wol.WolBroadcastProblem
import net.aieat.netswissknife.core.network.wol.WolMacProblem
import net.aieat.netswissknife.core.network.wol.WolMagicPacket
import net.aieat.netswissknife.core.network.wol.WolSendReport

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

class WakeOnLanUseCase(
    private val repository: WakeOnLanRepository,
) {
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
        val macProblem = WolMagicPacket.macProblem(mac)
        if (macProblem == WolMacProblem.BLANK) {
            return NetworkResult.error(ErrorCode.MAC_BLANK, "MAC address must not be blank")
        }
        if (macProblem != null) {
            return NetworkResult.error(ErrorCode.MAC_INVALID, "Invalid MAC address format: $macProblem")
        }
        val broadcast = params.broadcastAddress.trim()
        val broadcastProblem = WolMagicPacket.broadcastProblem(broadcast)
        if (broadcastProblem == WolBroadcastProblem.BLANK) {
            return NetworkResult.error(ErrorCode.BROADCAST_BLANK, "Broadcast address must not be blank")
        }
        if (broadcastProblem != null) {
            return NetworkResult.error(ErrorCode.BROADCAST_INVALID, "Broadcast address must be an IPv4 address")
        }
        if (params.port !in WakeOnLanParams.MIN_PORT..WakeOnLanParams.MAX_PORT) {
            return NetworkResult.error(
                ErrorCode.PORT_OUT_OF_RANGE,
                "Port must be between ${WakeOnLanParams.MIN_PORT} and ${WakeOnLanParams.MAX_PORT}",
                args = listOf(params.port, WakeOnLanParams.MIN_PORT, WakeOnLanParams.MAX_PORT),
            )
        }
        return if (operationSession == null) {
            repository.sendMagicPacket(mac, broadcast, params.port)
        } else {
            repository.sendMagicPacket(mac, broadcast, params.port, operationSession = operationSession)
        }
    }
}
