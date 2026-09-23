package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingResult
import java.util.Locale

/** Builds the stable, locale-neutral CSV used by Ping's Copy and Share actions. */
internal object PingCsvSerializer {

    fun serialize(result: PingResult): String = buildString {
        appendCsvRow(listOf("sequence", "host", "status", "rtt_ms", "error", "ttl", "bytes"))
        result.packets.forEach { packet ->
            appendCsvRow(
                listOf(
                    packet.sequence.toString(),
                    packet.host,
                    packet.status.toString(),
                    packet.rtTimeMs?.toString().orEmpty(),
                    packet.errorMessage.orEmpty(),
                    packet.replyTtl?.toString().orEmpty(),
                    packet.bytes?.toString().orEmpty()
                )
            )
        }
        append('\n')
        append("# Stats\n")
        appendCsvRow(listOf("sent", "received", "loss_percent", "min_ms", "avg_ms", "max_ms", "jitter_ms"))
        appendCsvRow(
            listOf(
                result.stats.sent.toString(),
                result.stats.received.toString(),
                formatDecimal(result.stats.lossPercent, 1),
                result.stats.minMs.toString(),
                formatDecimal(result.stats.avgMs, 3),
                result.stats.maxMs.toString(),
                formatDecimal(result.stats.jitterMs, 3)
            )
        )
    }

    private fun StringBuilder.appendCsvRow(fields: List<String>) {
        append(fields.joinToString(",", transform = ::csvField))
        append('\n')
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    /** Non-finite stats are emitted as canonical text tokens; finite values use decimal-dot precision. */
    private fun formatDecimal(value: Float, fractionDigits: Int): String = when {
        value.isNaN() -> "NaN"
        value == Float.POSITIVE_INFINITY -> "Infinity"
        value == Float.NEGATIVE_INFINITY -> "-Infinity"
        else -> String.format(Locale.ROOT, "%.${fractionDigits}f", value)
    }

    /** Non-finite stats are emitted as canonical text tokens; finite values use decimal-dot precision. */
    private fun formatDecimal(value: Double, fractionDigits: Int): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        else -> String.format(Locale.ROOT, "%.${fractionDigits}f", value)
    }
}
