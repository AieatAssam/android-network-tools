package com.example.netswissknife.core.network.traceroute

import kotlinx.coroutines.flow.Flow

interface TracerouteRepository {
    /**
     * Executes a traceroute to [host] and emits [HopResult] for each hop discovered.
     *
     * @param host          Hostname or IP address to trace.
     * @param maxHops       Maximum number of hops to probe (TTL limit).
     * @param timeoutMs     Per-hop timeout in milliseconds.
     * @param queriesPerHop Number of probe packets sent per hop.
     */
    fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        queriesPerHop: Int
    ): Flow<HopResult>
}
