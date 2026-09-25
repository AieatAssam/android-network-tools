package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.flow.Flow

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
}
