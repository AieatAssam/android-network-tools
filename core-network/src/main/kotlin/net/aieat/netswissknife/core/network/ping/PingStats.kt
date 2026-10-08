package net.aieat.netswissknife.core.network.ping

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
            val accumulator = PingStatsAccumulator()
            packets.forEach(accumulator::add)
            return accumulator.snapshot()
        }
    }
}

/**
 * Constant-memory accumulator for the statistics represented by [PingStats].
 * Successful packets without an RTT are counted as sent but not received, matching
 * [PingStats.compute]. Jitter is accumulated across successive successful RTTs,
 * skipping failed probes just as the list-based computation does.
 */
class PingStatsAccumulator {
    private var sent = 0
    private var received = 0
    private var minMs = Long.MAX_VALUE
    private var maxMs = Long.MIN_VALUE
    private var rttSum = 0.0
    private var previousSuccessfulRtt: Long? = null
    private var jitterDifferenceSum = 0.0
    private var jitterDifferenceCount = 0

    fun add(packet: PingPacketResult) {
        sent++
        val rtt = packet.rtTimeMs?.takeIf { packet.status == PingStatus.SUCCESS } ?: return
        received++
        if (rtt < minMs) minMs = rtt
        if (rtt > maxMs) maxMs = rtt
        rttSum += rtt.toDouble()
        previousSuccessfulRtt?.let { previous ->
            jitterDifferenceSum += kotlin.math.abs(rtt - previous).toDouble()
            jitterDifferenceCount++
        }
        previousSuccessfulRtt = rtt
    }

    fun snapshot(): PingStats {
        val lossPercent = if (sent == 0) 0f else ((sent - received).toFloat() / sent) * 100f
        return PingStats(
            sent = sent,
            received = received,
            lossPercent = lossPercent,
            minMs = if (received == 0) 0L else minMs,
            maxMs = if (received == 0) 0L else maxMs,
            avgMs = if (received == 0) 0.0 else rttSum / received,
            jitterMs = if (jitterDifferenceCount == 0) 0.0 else jitterDifferenceSum / jitterDifferenceCount,
        )
    }
}
