package net.aieat.netswissknife.core.network.traceroute

/** Why a trace stops before its maximum hop count. */
enum class TraceStopReason {
    DESTINATION_REACHED,
    UNREACHABLE_FROM_THIS_DEVICE,
}

/**
 * Decides when a trace is finished. Probes for later TTLs can already be in flight when the
 * destination answers, and a host on this subnet that cannot be reached answers every TTL from
 * this device's own address. Both cases are detected from the hops observed so far.
 */
object TracerouteStopPolicy {
    /** Consecutive replies from this device needed before the destination is declared unreachable. */
    const val LOCAL_REPLY_THRESHOLD = 2

    /**
     * The reason to stop after the last of [hops], or null to keep probing. A destination reply
     * always wins; a run of [LOCAL_REPLY_THRESHOLD] replies from [localAddresses] at the end of the
     * trace means the probes never left this device.
     */
    fun stopAfter(
        hops: List<HopResult>,
        localAddresses: Set<String>,
    ): TraceStopReason? {
        val last = hops.lastOrNull()
        val fromDevice = trailingLocalReplies(hops, localAddresses) >= LOCAL_REPLY_THRESHOLD
        return when {
            last == null -> null
            last.destinationReached -> TraceStopReason.DESTINATION_REACHED
            fromDevice -> TraceStopReason.UNREACHABLE_FROM_THIS_DEVICE
            else -> null
        }
    }

    fun isUnreachableFromThisDevice(
        hops: List<HopResult>,
        localAddresses: Set<String>,
    ): Boolean = stopAfter(hops, localAddresses) == TraceStopReason.UNREACHABLE_FROM_THIS_DEVICE

    /** Number of consecutive replies at the end of [hops] that came from this device. */
    private fun trailingLocalReplies(
        hops: List<HopResult>,
        localAddresses: Set<String>,
    ): Int {
        var count = 0
        for (hop in hops.asReversed()) {
            val fromDevice = !hop.destinationReached && hop.ip != null && hop.ip in localAddresses
            if (!fromDevice) break
            count++
        }
        return count
    }
}
