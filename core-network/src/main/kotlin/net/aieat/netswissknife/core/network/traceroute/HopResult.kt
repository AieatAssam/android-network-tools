package net.aieat.netswissknife.core.network.traceroute

/**
 * Result for a single hop in a traceroute.
 *
 * @param hopNumber    1-based index of this hop.
 * @param ip           IP address of the responding router, or null if it timed out.
 * @param hostname     Reverse-DNS hostname, or null if unavailable.
 * @param rtTimeMs     Round-trip time in milliseconds, or null on timeout/error.
 * @param status       Whether the hop responded, timed out, or produced an error.
 * @param geoLocation  Geographic location of this hop's IP, or null if unavailable.
 * @param probeRttsMs  RTT for each probe in send order; null means that probe did not receive a reply.
 * @param resolvedDestinationIp Resolver result for the requested destination, independent of this hop's responder.
 * @param destinationReached Whether the native traceroute engine identified a destination response at this hop.
 */
data class HopResult(
    val hopNumber: Int,
    val ip: String?,
    val hostname: String?,
    val rtTimeMs: Long?,
    val status: HopStatus,
    val geoLocation: HopGeoLocation? = null,
    val probeRttsMs: List<Long?> = emptyList(),
    val resolvedDestinationIp: String? = null,
    val destinationReached: Boolean = false,
) {
    /** Minimum successful probe RTT, or null when every probe timed out or failed. */
    val rttMinMs: Long? get() = probeRttsMs.filterNotNull().minOrNull()

    /** Mean successful probe RTT, or null when every probe timed out or failed. */
    val rttAvgMs: Double?
        get() =
            probeRttsMs
                .filterNotNull()
                .takeIf { it.isNotEmpty() }
                ?.average()

    /** Maximum successful probe RTT, or null when every probe timed out or failed. */
    val rttMaxMs: Long? get() = probeRttsMs.filterNotNull().maxOrNull()

    /** Probes that received a reply; a result without per-probe RTTs counts its single [rtTimeMs]. */
    val answeredProbes: Int
        get() = if (probeRttsMs.isEmpty()) listOfNotNull(rtTimeMs).size else probeRttsMs.count { it != null }

    /**
     * The hop's RTT wherever one figure is shown: the median of the answered probes, so one slow probe
     * does not set it. With an even number of replies it is the mean of the middle two, rounded. A result
     * without per-probe RTTs uses [rtTimeMs]. Null when no probe was answered.
     */
    val rttMedianMs: Long?
        get() {
            val answered = probeRttsMs.filterNotNull().sorted()
            if (answered.isEmpty()) return rtTimeMs.takeIf { probeRttsMs.isEmpty() }
            val middle = answered.size / 2
            return if (answered.size % 2 == 1) {
                answered[middle]
            } else {
                Math.round((answered[middle - 1] + answered[middle]) / 2.0)
            }
        }
}
