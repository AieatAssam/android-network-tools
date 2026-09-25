package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Pooled Cloudflare transport. A shared client lets the latency requests reuse TLS/HTTP connections. */
class OkHttpTransferEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val monotonicTimeNs: () -> Long = System::nanoTime
) : TransferEngine {

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun connectRtt(host: String, port: Int): Long? = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(host)
        currentCoroutineContext().ensureActive()
        val start = monotonicTimeNs()
        Socket().use { socket ->
            // Register cancellation against the active socket so a blocked connect is closed promptly.
            val socketHandler = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
                runCatching { socket.close() }
            }
            try {
                socket.connect(InetSocketAddress(address, port), 5_000)
            } finally {
                socketHandler.dispose()
            }
        }
        ((monotonicTimeNs() - start) / 1_000_000L).coerceAtLeast(0L)
    }

    override suspend fun httpRtt(url: String): HttpRtt = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val request = Request.Builder().url(url).get().build()
        val start = monotonicTimeNs()
        withResponse(request) { response ->
            response.body?.close()
            currentCoroutineContext().ensureActive()
            HttpRtt(
                totalMs = ((monotonicTimeNs() - start) / 1_000_000L).coerceAtLeast(0L),
                serverMs = response.headers.values("Server-Timing")
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                    ?.let(::parseServerTimingMs)
            )
        }
    }

    override suspend fun serverInfo(url: String): ServerInfo? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val request = Request.Builder().url(url).get().build()
        withResponse(request) { response ->
            if (!response.isSuccessful) return@withResponse null
            val body = response.body ?: return@withResponse null
            if (body.contentLength() > MAX_SERVER_INFO_BYTES) return@withResponse null
            val json = body.byteStream().use { readBoundedServerInfo(it) }
                ?: return@withResponse null
            val fields = Json.parseToJsonElement(json).jsonObject
            fun value(name: String): String? = fields[name]?.jsonPrimitive?.content
                ?.takeIf(String::isNotBlank)
            ServerInfo(
                colo = value("colo"),
                clientIp = value("clientIp"),
                asn = value("asn"),
                country = value("country")
            )
        }
    }

    override fun download(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> =
        transfer(url, streams, durationMs, upload = false, operationSession = null)

    override fun download(
        url: String,
        streams: Int,
        durationMs: Long,
        operationSession: OperationSession,
    ): Flow<ChunkEvent> = transfer(url, streams, durationMs, upload = false, operationSession = operationSession)

    override fun upload(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> =
        transfer(url, streams, durationMs, upload = true, operationSession = null)

    override fun upload(
        url: String,
        streams: Int,
        durationMs: Long,
        operationSession: OperationSession,
    ): Flow<ChunkEvent> = transfer(url, streams, durationMs, upload = true, operationSession = operationSession)

    private fun transfer(
        url: String,
        streams: Int,
        durationMs: Long,
        upload: Boolean,
        operationSession: OperationSession?,
    ): Flow<ChunkEvent> = channelFlow {
        val startedAt = monotonicTimeNs()
        val safeStreams = streams.coerceAtLeast(1)
        repeat(safeStreams) { streamIndex ->
            launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_BYTES)
                while ((monotonicTimeNs() - startedAt) / 1_000_000L < durationMs) {
                    currentCoroutineContext().ensureActive()
                    val requestBuilder = Request.Builder().url(url)
                    val request = if (upload) {
                        requestBuilder.post(ByteArray(CHUNK_BYTES).toRequestBody(BINARY)).build()
                    } else {
                        requestBuilder.get().build()
                    }
                    suspend fun executeRequest() {
                        withResponse(request) { response ->
                            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                            val body = response.body ?: return@withResponse
                            if (upload) {
                                body.byteStream().use { drainUploadResponse(it) }
                                send(ChunkEvent(streamIndex, CHUNK_BYTES.toLong(), elapsedSince(startedAt)))
                            } else {
                                body.byteStream().use { input ->
                                    while ((monotonicTimeNs() - startedAt) / 1_000_000L < durationMs) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        send(ChunkEvent(streamIndex, count.toLong(), elapsedSince(startedAt)))
                                    }
                                }
                            }
                        }
                    }
                    if (operationSession == null) executeRequest()
                    else operationSession.concurrencyLimiter.withPermit { executeRequest() }
                }
            }
        }
    }

    private fun elapsedSince(startedAt: Long) = ((monotonicTimeNs() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun <T> withResponse(
        request: Request,
        consume: suspend (okhttp3.Response) -> T
    ): T = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        val cancellation = coroutineContext[Job]?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true
        ) { cause -> if (cause != null) call.cancel() }
        try {
            call.execute().use { response -> consume(response) }
        } finally {
            cancellation?.dispose()
        }
    }

    companion object {
        private const val CHUNK_BYTES = 64 * 1024
        private const val UPLOAD_RESPONSE_DRAIN_CAP_BYTES = 16 * 1024
        internal const val MAX_SERVER_INFO_BYTES = 16 * 1024
        private val BINARY = "application/octet-stream".toMediaType()

        /** Drain a small response prefix to permit reuse while bounding work and allocation. */
        internal fun drainUploadResponse(input: java.io.InputStream): Int {
            val buffer = ByteArray(4 * 1024)
            var drained = 0
            while (drained < UPLOAD_RESPONSE_DRAIN_CAP_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, UPLOAD_RESPONSE_DRAIN_CAP_BYTES - drained))
                if (read < 0) break
                drained += read
            }
            return drained
        }

        /** Reads at most one byte over the limit so chunked/unknown-length bodies are rejected. */
        internal fun readBoundedServerInfo(input: java.io.InputStream): String? {
            val bytes = ByteArray(MAX_SERVER_INFO_BYTES + 1)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            if (count > MAX_SERVER_INFO_BYTES) return null
            return bytes.decodeToString(endIndex = count)
        }

        private fun splitServerTimingMetrics(header: String): List<String> {
            val metrics = mutableListOf<String>()
            var start = 0
            var quoted = false
            var escaped = false
            header.forEachIndexed { index, char ->
                when {
                    quoted && escaped -> escaped = false
                    quoted && char == '\\' -> escaped = true
                    char == '"' -> quoted = !quoted
                    char == ',' && !quoted -> {
                        metrics += header.substring(start, index)
                        start = index + 1
                    }
                }
            }
            metrics += header.substring(start)
            return metrics
        }

        /** Parses the cfRequestDuration metric from a possibly combined Server-Timing header. */
        fun parseServerTimingMs(header: String): Double? = splitServerTimingMetrics(header)
            .asSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { metric ->
                val parts = metric.split(';').map { it.trim() }
                if (!parts.firstOrNull().equals("cfRequestDuration", ignoreCase = true)) return@firstNotNullOfOrNull null
                parts.drop(1).firstNotNullOfOrNull { part ->
                    if (!part.startsWith("dur=", ignoreCase = true)) null
                    else part.substringAfter('=').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                }
            }
    }
}
