package net.aieat.netswissknife.core.network.topology

data class SnmpWalkResult(
    val entries: Map<String, String>,
    val truncationReasons: Set<TopologyTruncationReason> = emptySet(),
    val hadError: Boolean = false
)

/** Shared by all concurrent walks for one device so parallel tables share one ceiling. */
class SnmpWalkBudget internal constructor(
    private val limits: TopologyResourceLimits
) {
    internal val maxRepetitions: Int get() = limits.maxRepetitions
    internal val maxValueChars: Int get() = limits.maxValueChars
    private var deviceEntries = 0
    private var deviceBytes = 0

    @Synchronized
    internal fun tryReserve(
        oid: String,
        value: String,
        walkEntries: Int,
        walkBytes: Int
    ): Reservation {
        if (value.length > limits.maxValueChars) {
            return Reservation.Rejected(TopologyTruncationReason.WALK_VALUE_LIMIT)
        }
        // Three bytes per UTF-16 code unit is a conservative UTF-8 upper bound.
        val bytes = (oid.length.toLong() + value.length.toLong()) * 3L
        if (walkEntries >= limits.maxEntriesPerWalk) {
            return Reservation.Rejected(TopologyTruncationReason.WALK_ENTRY_LIMIT)
        }
        if (bytes + walkBytes > limits.maxBytesPerWalk) {
            return Reservation.Rejected(TopologyTruncationReason.WALK_BYTE_LIMIT)
        }
        if (deviceEntries >= limits.maxEntriesPerDevice) {
            return Reservation.Rejected(TopologyTruncationReason.DEVICE_ENTRY_LIMIT)
        }
        if (bytes + deviceBytes > limits.maxBytesPerDevice) {
            return Reservation.Rejected(TopologyTruncationReason.DEVICE_BYTE_LIMIT)
        }
        deviceEntries++
        deviceBytes += bytes.toInt()
        return Reservation.Accepted(bytes.toInt())
    }

    @Synchronized
    internal fun tryReserveScalar(oid: String, value: String): Reservation {
        val bytes = (oid.length.toLong() + value.length.toLong()) * 3L
        if (bytes + deviceBytes > limits.maxBytesPerDevice) {
            return Reservation.Rejected(TopologyTruncationReason.DEVICE_BYTE_LIMIT)
        }
        deviceBytes += bytes.toInt()
        return Reservation.Accepted(bytes.toInt())
    }

    internal sealed interface Reservation {
        data class Accepted(val bytes: Int) : Reservation
        data class Rejected(val reason: TopologyTruncationReason) : Reservation
    }
}
