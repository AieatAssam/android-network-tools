package net.aieat.netswissknife.core.network.traceroute

/**
 * Turns the hop reports of a trace into its rows. A TTL is reported again each time one of its probes
 * completes, replies arrive out of TTL order, and probes for TTLs past the destination can be answered
 * by the destination too.
 */
object TracerouteHops {
    /**
     * [hops] with [update] in place of any earlier report for the same TTL, in TTL order. Reverse DNS
     * and location found for the same address are kept, since a later probe report does not carry them.
     */
    fun merge(
        hops: List<HopResult>,
        update: HopResult,
    ): List<HopResult> {
        val previous = hops.firstOrNull { it.hopNumber == update.hopNumber }
        val merged =
            if (previous != null && previous.ip == update.ip) {
                update.copy(
                    hostname = update.hostname ?: previous.hostname,
                    geoLocation = update.geoLocation ?: previous.geoLocation,
                )
            } else {
                update
            }
        return (hops.filter { it.hopNumber != update.hopNumber } + merged).sortedBy { it.hopNumber }
    }

    /** [hops] in TTL order, ending at the first TTL the destination answered. */
    fun ordered(hops: List<HopResult>): List<HopResult> {
        val end = hops.filter { it.destinationReached }.minOfOrNull { it.hopNumber }
        return hops
            .filter { end == null || it.hopNumber <= end }
            .distinctBy { it.hopNumber }
            .sortedBy { it.hopNumber }
    }

    /**
     * The rows of a finished trace: [ordered], plus a no-reply row for every TTL from 1 to the last one
     * that produced no report at all.
     */
    fun complete(hops: List<HopResult>): List<HopResult> {
        val rows = ordered(hops)
        val byNumber = rows.associateBy { it.hopNumber }
        val last = rows.lastOrNull()?.hopNumber ?: return rows
        return (1..last).map { number ->
            byNumber[number] ?: HopResult(number, null, null, null, HopStatus.TIMEOUT)
        }
    }

    /** The highest TTL probed on the route: the destination's TTL when it answered. */
    fun probedHopCount(hops: List<HopResult>): Int = ordered(hops).lastOrNull()?.hopNumber ?: 0
}

/**
 * Decides when a trace is finished as its hop reports arrive. A destination reply does not end the trace
 * until every probe of each earlier TTL has reported, because those can time out after the destination
 * has answered. Reports for TTLs past the destination are not part of the route.
 */
class TraceCompletion(
    private val probesPerHop: Int,
    private val localAddresses: Set<String>,
) {
    private val observed = mutableListOf<HopResult>()
    private val probesReported = mutableMapOf<Int, Int>()
    private var destinationHop: Int? = null
    private var unreachableFromThisDevice = false

    /** True once the route to the destination is complete, or the probes never left this device. */
    val isFinished: Boolean
        get() =
            unreachableFromThisDevice ||
                destinationHop?.let { end ->
                    (1 until end).all { (probesReported[it] ?: 0) >= probesPerHop }
                } ?: false

    /** Records [hop]; false when it lies past the destination and should be dropped. */
    fun accept(hop: HopResult): Boolean {
        val end = destinationHop
        if (end != null && hop.hopNumber > end) return false
        observed += hop
        val reported = hop.probeRttsMs.size.coerceAtLeast(1)
        probesReported[hop.hopNumber] = maxOf(probesReported[hop.hopNumber] ?: 0, reported)
        if (hop.destinationReached) destinationHop = minOf(end ?: hop.hopNumber, hop.hopNumber)
        if (TracerouteStopPolicy.isUnreachableFromThisDevice(observed, localAddresses)) {
            unreachableFromThisDevice = true
        }
        return true
    }
}
