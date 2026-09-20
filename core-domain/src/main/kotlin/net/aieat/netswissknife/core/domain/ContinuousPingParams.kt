package net.aieat.netswissknife.core.domain

data class ContinuousPingParams(
    val host: String,
    val timeoutMs: Int = 3_000,
    val intervalMs: Int = 1_000,
    val payloadBytes: Int = 56,
    val ttl: Int = 64
)
