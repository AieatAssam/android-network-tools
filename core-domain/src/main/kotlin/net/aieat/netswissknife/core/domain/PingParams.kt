package net.aieat.netswissknife.core.domain

/**
 * Parameters for a ping session.
 *
 * @param host       Target hostname or IP address
 * @param count      Number of probes to send (1–100)
 * @param timeoutMs  Per-probe timeout in milliseconds (100–30 000)
 * @param intervalMs Delay between probes in milliseconds (100–10 000)
 * @param payloadBytes ICMP payload size (0–1472)
 * @param ttl          IP time-to-live (1–255)
 */
data class PingParams(
    val host: String,
    val count: Int = 4,
    val timeoutMs: Int = 3_000,
    val intervalMs: Int = 1_000,
    val payloadBytes: Int = 56,
    val ttl: Int = 64
)
