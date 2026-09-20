package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import java.io.File

class PingSessionLogger(internal val file: File) {

    fun init() {
        file.writeText("seq,timestamp_ms,latency_ms,status,ttl,bytes\n")
    }

    fun append(seq: Int, packet: PingPacketResult) {
        val status = when (packet.status) {
            PingStatus.SUCCESS -> "ok"
            PingStatus.TIMEOUT -> "timeout"
            PingStatus.UNREACHABLE -> "unreachable"
            PingStatus.ERROR -> "error"
        }
        file.appendText(
            "$seq,${System.currentTimeMillis()},${packet.rtTimeMs ?: ""},$status," +
                "${packet.replyTtl ?: ""},${packet.bytes ?: ""}\n"
        )
    }
}
