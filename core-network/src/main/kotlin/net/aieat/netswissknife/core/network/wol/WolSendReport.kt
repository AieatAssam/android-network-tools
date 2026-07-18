package net.aieat.netswissknife.core.network.wol

/**
 * Outcome of a successful Wake-on-LAN send.
 *
 * @property macAddress canonical colon-separated MAC the packet targets
 * @property broadcastAddress destination address the UDP datagrams were sent to
 * @property port destination UDP port (conventionally 9, sometimes 7)
 * @property packetsSent number of duplicate magic packets sent for reliability
 */
data class WolSendReport(
    val macAddress: String,
    val broadcastAddress: String,
    val port: Int,
    val packetsSent: Int,
)
