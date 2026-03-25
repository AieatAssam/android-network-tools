package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.traceroute.TracerouteProbeType

/**
 * Parameters for a traceroute operation.
 *
 * @param host          Hostname or IP address to trace.
 * @param maxHops       Maximum TTL / hop limit (1–64). Default: 30.
 * @param timeoutMs     Per-hop response timeout in milliseconds (500–30 000). Default: 3 000.
 * @param probesPerHop  Number of probe packets per hop (1–5). More probes give a more accurate
 *                      RTT average but slow down the trace. Default: 1.
 * @param probeType     Protocol to use for probes. [TracerouteProbeType.ICMP] works on most
 *                      networks; [TracerouteProbeType.UDP] can bypass ICMP rate-limiting.
 *                      Default: ICMP.
 * @param packetSize    Probe payload size in bytes (28–1472). Use 0 for automatic MTU
 *                      discovery mode. Default: 56 (matches traditional ping packet size).
 */
data class TracerouteParams(
    val host: String,
    val maxHops: Int = 30,
    val timeoutMs: Int = 3_000,
    val probesPerHop: Int = 1,
    val probeType: TracerouteProbeType = TracerouteProbeType.ICMP,
    val packetSize: Int = 56
)
