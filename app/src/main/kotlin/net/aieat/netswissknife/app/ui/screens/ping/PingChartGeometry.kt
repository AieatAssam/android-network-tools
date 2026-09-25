package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus

internal data class PingChartSample(
    val packetIndex: Int,
    val rttMs: Long,
)

/** Group adjacent successful measurements so chart lines never bridge a failed packet. */
internal fun pingChartSuccessSegments(packets: List<PingPacketResult>): List<List<PingChartSample>> {
    val segments = mutableListOf<List<PingChartSample>>()
    var current = mutableListOf<PingChartSample>()

    packets.forEachIndexed { index, packet ->
        val rttMs = packet.rtTimeMs
        if (packet.status == PingStatus.SUCCESS && rttMs != null) {
            current += PingChartSample(index, rttMs)
        } else if (current.isNotEmpty()) {
            segments += current.toList()
            current = mutableListOf()
        }
    }

    if (current.isNotEmpty()) segments += current.toList()
    return segments
}
