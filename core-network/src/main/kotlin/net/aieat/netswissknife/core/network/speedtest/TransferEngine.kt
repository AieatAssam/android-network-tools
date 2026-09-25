package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import net.aieat.netswissknife.core.network.operation.OperationSession

data class HttpRtt(val totalMs: Long, val serverMs: Double?) {
    /** Removes Cloudflare's server processing time without allowing a negative RTT. */
    val correctedMs: Long get() = (totalMs - (serverMs ?: 0.0)).coerceAtLeast(0.0).toLong()
}

data class ChunkEvent(val streamIndex: Int, val bytes: Long, val elapsedMs: Long)

/** Network boundary for a speed test run; kept injectable for deterministic repository tests. */
interface TransferEngine {
    suspend fun connectRtt(host: String, port: Int): Long?
    suspend fun httpRtt(url: String): HttpRtt
    suspend fun serverInfo(url: String): ServerInfo?
    fun download(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent>
    fun upload(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent>

    /**
     * Session-aware transfer seam. Implementations with per-stream workers should acquire a
     * session permit around each network request; the default adapter reserves permits for the
     * effective stream count while a legacy engine runs and leaves capacity for loaded RTT.
     */
    fun download(
        url: String,
        streams: Int,
        durationMs: Long,
        operationSession: OperationSession,
    ): Flow<ChunkEvent> = boundedLegacyTransfer(url, streams, durationMs, operationSession) {
        download(url, it, durationMs)
    }

    /** See [download]'s session-aware compatibility adapter. */
    fun upload(
        url: String,
        streams: Int,
        durationMs: Long,
        operationSession: OperationSession,
    ): Flow<ChunkEvent> = boundedLegacyTransfer(url, streams, durationMs, operationSession) {
        upload(url, it, durationMs)
    }
}

private fun boundedLegacyTransfer(
    url: String,
    streams: Int,
    durationMs: Long,
    operationSession: OperationSession,
    transfer: (effectiveStreams: Int) -> Flow<ChunkEvent>,
): Flow<ChunkEvent> = flow {
    val budget = operationSession.budget.maxConcurrentProbes
    // Legacy engines cannot share permits between their internal streams. Reserve at least one
    // slot for transfer and, when possible, one for loaded latency, then make the chosen cap
    // explicit in the stream count passed to the adapter.
    val effectiveStreams = streams.coerceAtLeast(1).coerceAtMost((budget - 1).coerceAtLeast(1))
    operationSession.concurrencyLimiter.withPermits(effectiveStreams) {
        transfer(effectiveStreams).collect { emit(it) }
    }
}
