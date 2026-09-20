package net.aieat.netswissknife.core.network.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository that sends ping probes to a remote host and streams each result
 * as it arrives via a [Flow].
 */
interface PingRepository {

    val lastEngineUsed: StateFlow<PingEngineKind?>?
        get() = null

    fun ping(request: PingRequest): Flow<PingPacketResult>

    fun continuousPing(request: PingRequest): Flow<PingPacketResult>

    /**
     * Sends [count] reachability probes to [host] and emits a [PingPacketResult]
     * for each one as it completes (success, timeout, or error).
     *
     * @param host       Hostname or IP address to ping
     * @param count      Number of probes to send (≥ 1)
     * @param timeoutMs  Per-probe timeout in milliseconds
     */
    @Deprecated("Use ping(PingRequest)")
    fun ping(
        host: String,
        count: Int,
        timeoutMs: Int
    ): Flow<PingPacketResult> = ping(PingRequest(host = host, count = count, timeoutMs = timeoutMs))

    /**
     * Sends probes to [host] indefinitely, emitting a [PingPacketResult] for each one.
     * The flow runs until the collecting coroutine is cancelled.
     *
     * @param host       Hostname or IP address to ping
     * @param timeoutMs  Per-probe timeout in milliseconds
     */
    @Deprecated("Use continuousPing(PingRequest)")
    fun continuousPing(
        host: String,
        timeoutMs: Int
    ): Flow<PingPacketResult> = continuousPing(PingRequest(host = host, count = 0, timeoutMs = timeoutMs))
}
