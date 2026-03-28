package net.aieat.netswissknife.core.network.ping

/**
 * The result of a single ping probe.
 *
 * @param sequence     1-based sequence number of this probe
 * @param host         Target host that was pinged
 * @param rtTimeMs     Round-trip time in ms; null when [status] is not [PingStatus.SUCCESS]
 * @param status       Outcome of this probe
 * @param errorMessage Human-readable error detail; non-null only when [status] is [PingStatus.ERROR]
 */
data class PingPacketResult(
    val sequence: Int,
    val host: String,
    val rtTimeMs: Long?,
    val status: PingStatus,
    val errorMessage: String? = null
)
