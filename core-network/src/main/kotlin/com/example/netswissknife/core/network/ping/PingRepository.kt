package com.example.netswissknife.core.network.ping

import kotlinx.coroutines.flow.Flow

/**
 * Repository that sends ping probes to a remote host and streams each result
 * as it arrives via a [Flow].
 */
interface PingRepository {

    /**
     * Sends [count] ICMP-style probes to [host] and emits a [PingPacketResult]
     * for each one as it completes (success, timeout, or error).
     *
     * @param host       Hostname or IP address to ping
     * @param count      Number of probes to send (≥ 1)
     * @param timeoutMs  Per-probe timeout in milliseconds
     * @param packetSize Payload size in bytes (informational, used in raw output)
     */
    fun ping(
        host: String,
        count: Int,
        timeoutMs: Int,
        packetSize: Int
    ): Flow<PingPacketResult>
}
