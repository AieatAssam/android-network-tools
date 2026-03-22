package com.example.netswissknife.core.network.ping

/**
 * Low-level result from a single [InetAddress.isReachable] probe.
 *
 * @param reachable    Whether the host responded
 * @param rtTimeMs     Elapsed time in milliseconds (always measured, even on timeout/error)
 * @param errorMessage Non-null when an exception prevented the probe from completing
 */
data class ReachabilityResult(
    val reachable: Boolean,
    val rtTimeMs: Long,
    val errorMessage: String? = null
)
