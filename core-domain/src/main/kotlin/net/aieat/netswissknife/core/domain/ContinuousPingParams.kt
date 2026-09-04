package net.aieat.netswissknife.core.domain

data class ContinuousPingParams(
    val host: String,
    val timeoutMs: Int = 3_000
)
