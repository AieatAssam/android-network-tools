package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingEngineKind
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingResult
import net.aieat.netswissknife.core.network.ping.PingStats
import net.aieat.netswissknife.core.network.ping.PingStatus

/**
 * Builds the finished [PingResult] for a run, including its ping(8)-style raw output.
 *
 * [payloadBytes] and [ttl] are read when a result is built, so the raw header shows the values in
 * effect at that moment. [observedEngine] is only consulted when no packet names the engine.
 */
internal class PingResultBuilder(
    private val payloadBytes: () -> Int,
    private val ttl: () -> Int,
    private val observedEngine: () -> PingEngineKind?,
) {
    fun build(
        host: String,
        packets: List<PingPacketResult>,
        statsOverride: PingStats? = null,
    ): PingResult {
        val stats = statsOverride ?: PingStats.compute(packets)
        val raw = rawOutput(host, packets, stats)
        return PingResult(
            host = host,
            packets = packets,
            stats = stats,
            rawOutput = raw,
            engine =
                packets.firstNotNullOfOrNull { it.engine }
                    ?: observedEngine()
                    ?: PingEngineKind.REACHABILITY,
            resolvedIp = packets.firstNotNullOfOrNull { it.fromIp },
        )
    }

    private fun rawOutput(
        host: String,
        packets: List<PingPacketResult>,
        stats: PingStats,
    ): String =
        buildString {
            val firstPacket = packets.firstOrNull()
            val resolvedIp = firstPacket?.fromIp
            val engine = firstPacket?.engine ?: observedEngine() ?: PingEngineKind.REACHABILITY
            appendLine(pingRawHeader(host, resolvedIp, payloadBytes(), ttl(), engine))
            appendLine()
            packets.forEach { p -> appendLine(rawPacketLine(p)) }
            appendLine()
            appendLine("--- $host ping statistics ---")
            appendLine(
                "${stats.sent} packets transmitted, ${stats.received} packets received, " +
                    "${"%.1f".format(stats.lossPercent)}% packet loss",
            )
            if (stats.received > 0) {
                appendLine(
                    "round-trip min/avg/max/jitter = ${stats.minMs}/${"%.3f".format(stats.avgMs)}/" +
                        "${stats.maxMs}/${"%.3f".format(stats.jitterMs)} ms",
                )
            }
        }

    private fun rawPacketLine(p: PingPacketResult): String =
        when (p.status) {
            PingStatus.SUCCESS -> {
                "${p.host}: probe_seq=${p.sequence} time=${p.rtTimeMs} ms"
            }

            PingStatus.TIMEOUT -> {
                "Request timeout for probe_seq ${p.sequence}"
            }

            PingStatus.UNREACHABLE -> {
                "Destination unreachable for probe_seq ${p.sequence}: " +
                    (p.errorMessage ?: "unknown reason")
            }

            PingStatus.ERROR -> {
                "Error for probe_seq ${p.sequence}: ${p.errorMessage}"
            }
        }
}
