package com.example.netswissknife.core.network.ping

/**
 * The complete result of a ping session after all probes have been sent.
 *
 * @param host      Target host that was pinged
 * @param packets   Ordered list of individual probe results
 * @param stats     Aggregated statistics computed from [packets]
 * @param rawOutput Human-readable "raw" view of the ping session (like the CLI output)
 */
data class PingResult(
    val host: String,
    val packets: List<PingPacketResult>,
    val stats: PingStats,
    val rawOutput: String
)
