package net.aieat.netswissknife.core.network.wol

import net.aieat.netswissknife.core.network.NetworkResult

interface WakeOnLanRepository {

    /**
     * Sends a Wake-on-LAN magic packet for [macAddress] as a UDP broadcast.
     *
     * @param macAddress target MAC in any common notation
     * @param broadcastAddress destination address (default global broadcast)
     * @param port destination UDP port (9 by convention)
     * @param repeatCount duplicate packets to send; UDP is lossy so >1 improves reliability
     */
    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int = 9,
        repeatCount: Int = 3,
    ): NetworkResult<WolSendReport>
}
