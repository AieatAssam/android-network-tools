package net.aieat.netswissknife.core.network.traceroute

import kotlinx.coroutines.flow.Flow
import net.aieat.netswissknife.core.network.operation.OperationSession

interface TracerouteRepository {
    /**
     * Executes a traceroute to [host] and emits [HopResult] for each hop discovered.
     *
     * @param host          Hostname or IP address to trace.
     * @param maxHops       Maximum number of hops to probe (TTL limit).
     * @param timeoutMs     Per-hop timeout in milliseconds.
     * @param probesPerHop  Number of probe packets sent per hop (affects RTT accuracy).
     * @param probeType     Protocol to use for probes: ICMP (default) or UDP.
     * @param packetSize    Probe packet payload size in bytes. Use 0 for automatic MTU discovery.
     */
    fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int,
        probeType: TracerouteProbeType = TracerouteProbeType.ICMP,
        packetSize: Int = 56
    ): Flow<HopResult>

    /** Caller-owned operation scope for a complete trace and its optional enrichment. */
    fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int,
        probeType: TracerouteProbeType = TracerouteProbeType.ICMP,
        packetSize: Int = 56,
        operationSession: OperationSession,
    ): Flow<HopResult> = trace(host, maxHops, timeoutMs, probesPerHop, probeType, packetSize)
}
