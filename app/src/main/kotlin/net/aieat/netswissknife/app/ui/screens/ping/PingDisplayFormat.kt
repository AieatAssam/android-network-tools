package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingEngineKind
import kotlin.math.roundToInt

/** Packets kept for the live list and the RTT chart of a continuous session. */
internal const val PING_ROLLING_WINDOW = 100

internal val PING_TIMEOUT_PRESETS_MS = listOf(1_000, 2_000, 3_000, 5_000)

/** Packet loss as a percentage; a non-zero loss never reads as 0%. */
internal fun pingLossLabel(lossPercent: Float): String =
    when {
        lossPercent <= 0f -> "0%"
        lossPercent < 0.1f -> "<0.1%"
        lossPercent % 1f == 0f -> "${lossPercent.roundToInt()}%"
        else -> "${"%.1f".format(lossPercent)}%"
    }

/** Y-axis tick label for the RTT chart. Sub-millisecond values are not shown as 0 ms. */
internal fun pingAxisLabel(
    rttMs: Float,
    unit: String,
): String =
    when {
        rttMs <= 0f -> "0 $unit"
        rttMs < 1f -> "<1 $unit"
        else -> "${rttMs.roundToInt()} $unit"
    }

internal fun pingTimeoutLabel(timeoutMs: Int): String = if (timeoutMs % 1_000 == 0) "${timeoutMs / 1_000} s" else "$timeoutMs ms"

/** Preset timeouts plus the current value, so the selected chip always matches the setting. */
internal fun pingTimeoutOptions(currentMs: Int): List<Int> = (PING_TIMEOUT_PRESETS_MS + currentMs).distinct().sorted()

/**
 * Header line of the raw output. The reachability fallback does not send the requested payload
 * or TTL, so those values are left out rather than reported as applied.
 */
internal fun pingRawHeader(
    host: String,
    resolvedIp: String?,
    payloadBytes: Int,
    ttl: Int,
    engine: PingEngineKind,
): String {
    val target = "PING $host${resolvedIp?.let { " ($it)" } ?: ""}"
    return when (engine) {
        PingEngineKind.ICMP -> {
            "$target: $payloadBytes data bytes, ttl $ttl, engine ICMP"
        }

        PingEngineKind.REACHABILITY -> {
            "$target: engine Reachability (ICMP/TCP fallback), payload size and TTL not applied"
        }
    }
}

/** Fraction of the bar filled by one RTT, scaled to the slowest packet in the list. */
internal fun pingBarFraction(
    rttMs: Long,
    maxRttMs: Long,
): Float = if (maxRttMs <= 0L) 0f else (rttMs.toFloat() / maxRttMs).coerceIn(0f, 1f)
