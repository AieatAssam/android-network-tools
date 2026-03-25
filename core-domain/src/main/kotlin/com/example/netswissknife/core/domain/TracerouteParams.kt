package com.example.netswissknife.core.domain

data class TracerouteParams(
    val host: String,
    val maxHops: Int = 30,
    val timeoutMs: Int = 3_000,
    val queriesPerHop: Int = 1
)
