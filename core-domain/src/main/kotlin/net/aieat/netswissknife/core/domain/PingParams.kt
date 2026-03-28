package net.aieat.netswissknife.core.domain

/**
 * Parameters for a ping session.
 *
 * @param host       Target hostname or IP address
 * @param count      Number of probes to send (1–100)
 * @param timeoutMs  Per-probe timeout in milliseconds (100–30 000)
 * @param packetSize ICMP payload size in bytes (1–65 507)
 */
data class PingParams(
    val host: String,
    val count: Int = 4,
    val timeoutMs: Int = 3_000,
    val packetSize: Int = 56
)
