package com.example.netswissknife.core.network.ping

/**
 * Aggregated statistics computed from a completed ping session.
 *
 * @param sent        Total number of probes sent
 * @param received    Number of probes that received a response
 * @param lossPercent Packet loss as a percentage (0–100)
 * @param minMs       Minimum RTT in milliseconds; 0 when [received] == 0
 * @param maxMs       Maximum RTT in milliseconds; 0 when [received] == 0
 * @param avgMs       Average RTT in milliseconds; 0.0 when [received] == 0
 * @param jitterMs    Mean absolute deviation of successive RTT differences; 0.0 when fewer than 2 probes succeeded
 */
data class PingStats(
    val sent: Int,
    val received: Int,
    val lossPercent: Float,
    val minMs: Long,
    val maxMs: Long,
    val avgMs: Double,
    val jitterMs: Double
) {
    companion object {
        /**
         * Computes [PingStats] from a list of completed [PingPacketResult]s.
         * The list must contain exactly [sent] elements.
         */
        fun compute(packets: List<PingPacketResult>): PingStats {
            val sent = packets.size
            val successRtts = packets.mapNotNull { it.rtTimeMs }
            val received = successRtts.size

            val lossPercent = if (sent == 0) 0f else ((sent - received).toFloat() / sent) * 100f
            val minMs = successRtts.minOrNull() ?: 0L
            val maxMs = successRtts.maxOrNull() ?: 0L
            val avgMs = if (successRtts.isEmpty()) 0.0 else successRtts.average()

            val jitterMs = if (successRtts.size < 2) 0.0 else {
                val diffs = successRtts.zipWithNext { a, b -> Math.abs(b - a).toDouble() }
                diffs.average()
            }

            return PingStats(
                sent = sent,
                received = received,
                lossPercent = lossPercent,
                minMs = minMs,
                maxMs = maxMs,
                avgMs = avgMs,
                jitterMs = jitterMs
            )
        }
    }
}
