package net.aieat.netswissknife.core.network.ping

/** Fully specified ping session request. [count] == 0 means continuous. */
data class PingRequest(
    val host: String,
    val resolvedIp: String? = null,
    val count: Int,
    val timeoutMs: Int,
    val intervalMs: Int = 1_000,
    val payloadBytes: Int = 56,
    val ttl: Int = 64
)
