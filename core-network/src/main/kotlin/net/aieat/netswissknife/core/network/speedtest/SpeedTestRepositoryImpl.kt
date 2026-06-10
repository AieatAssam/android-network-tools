package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom

/** Streams bytes for a fixed wall-clock [duration][Long], invoking [onChunk] for every
 * read/write with the number of bytes moved and the elapsed time since the stream started. */
internal typealias ByteStreamFn = suspend (durationMs: Long, onChunk: suspend (bytesTransferred: Int, elapsedMs: Long) -> Unit) -> Unit

/**
 * Production [SpeedTestRepository] that measures latency, download, and upload
 * throughput against Cloudflare's public speed test endpoints
 * (`speed.cloudflare.com/__down` and `/__up` — the same backend that powers
 * https://speed.cloudflare.com).
 *
 * The [latencyProbe], [downloadStream], and [uploadStream] hooks are injected so
 * tests can supply deterministic timing/byte sequences without real network I/O;
 * the defaults perform the real HTTP calls.
 */
class SpeedTestRepositoryImpl(
    private val latencyProbeCount: Int = DEFAULT_LATENCY_PROBE_COUNT,
    private val latencyTimeoutMs: Int = DEFAULT_LATENCY_TIMEOUT_MS,
    private val downloadDurationMs: Long = DEFAULT_PHASE_DURATION_MS,
    private val uploadDurationMs: Long = DEFAULT_PHASE_DURATION_MS,
    private val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS,
    private val latencyProbe: suspend (timeoutMs: Int) -> Long = DEFAULT_LATENCY_PROBE,
    private val downloadStream: ByteStreamFn = DEFAULT_DOWNLOAD,
    private val uploadStream: ByteStreamFn = DEFAULT_UPLOAD
) : SpeedTestRepository {

    companion object {
        const val BASE_URL = "https://speed.cloudflare.com"
        const val DEFAULT_LATENCY_PROBE_COUNT = 10
        const val DEFAULT_LATENCY_TIMEOUT_MS = 5_000
        const val DEFAULT_PHASE_DURATION_MS = 10_000L
        const val DEFAULT_SAMPLE_INTERVAL_MS = 200L

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHUNK_SIZE = 64 * 1024
        private const val DOWNLOAD_PAYLOAD_BYTES = 25_000_000L
        private const val UPLOAD_PAYLOAD_BYTES = 10_000_000L

        private fun openConnection(url: String, method: String): HttpURLConnection =
            (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
            }

        val DEFAULT_LATENCY_PROBE: suspend (Int) -> Long = { timeoutMs ->
            val conn = openConnection("$BASE_URL/__down?bytes=0", "GET").apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            try {
                val start = System.nanoTime()
                conn.connect()
                conn.responseCode
                (System.nanoTime() - start) / 1_000_000L
            } finally {
                conn.disconnect()
            }
        }

        val DEFAULT_DOWNLOAD: ByteStreamFn = { durationMs, onChunk ->
            val start = System.currentTimeMillis()
            val buffer = ByteArray(CHUNK_SIZE)
            while (System.currentTimeMillis() - start < durationMs) {
                val conn = openConnection("$BASE_URL/__down?bytes=$DOWNLOAD_PAYLOAD_BYTES", "GET")
                try {
                    conn.connect()
                    conn.inputStream.use { stream ->
                        while (System.currentTimeMillis() - start < durationMs) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            onChunk(read, System.currentTimeMillis() - start)
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

        val DEFAULT_UPLOAD: ByteStreamFn = { durationMs, onChunk ->
            val start = System.currentTimeMillis()
            val payload = ByteArray(CHUNK_SIZE).also { SecureRandom().nextBytes(it) }
            while (System.currentTimeMillis() - start < durationMs) {
                val conn = openConnection("$BASE_URL/__up", "POST").apply {
                    doOutput = true
                    setChunkedStreamingMode(CHUNK_SIZE)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                try {
                    conn.connect()
                    conn.outputStream.use { out ->
                        var written = 0L
                        while (written < UPLOAD_PAYLOAD_BYTES && System.currentTimeMillis() - start < durationMs) {
                            val toWrite = minOf(CHUNK_SIZE.toLong(), UPLOAD_PAYLOAD_BYTES - written).toInt()
                            out.write(payload, 0, toWrite)
                            written += toWrite
                            onChunk(toWrite, System.currentTimeMillis() - start)
                        }
                    }
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    override fun runSpeedTest(): Flow<SpeedTestEvent> = flow {
        // ── Phase 1: latency ─────────────────────────────────────────────────
        val latencySamples = mutableListOf<LatencySample>()
        for (seq in 1..latencyProbeCount) {
            val rtt = try {
                latencyProbe(latencyTimeoutMs)
            } catch (e: Exception) {
                emit(SpeedTestEvent.Failed(SpeedTestPhase.LATENCY, e.message ?: "Latency probe failed"))
                return@flow
            }
            val sample = LatencySample(seq, rtt)
            latencySamples.add(sample)
            emit(SpeedTestEvent.LatencyProgress(sample, latencyProbeCount))
        }
        emit(SpeedTestEvent.LatencyFinished(LatencyStats.compute(latencySamples)))

        // ── Phase 2: download ────────────────────────────────────────────────
        val downloadResult = try {
            measureThroughput(downloadDurationMs, downloadStream) { emit(SpeedTestEvent.DownloadProgress(it)) }
        } catch (e: Exception) {
            emit(SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, e.message ?: "Download test failed"))
            return@flow
        }
        emit(SpeedTestEvent.DownloadFinished(downloadResult))

        // ── Phase 3: upload ──────────────────────────────────────────────────
        val uploadResult = try {
            measureThroughput(uploadDurationMs, uploadStream) { emit(SpeedTestEvent.UploadProgress(it)) }
        } catch (e: Exception) {
            emit(SpeedTestEvent.Failed(SpeedTestPhase.UPLOAD, e.message ?: "Upload test failed"))
            return@flow
        }
        emit(SpeedTestEvent.UploadFinished(uploadResult))
    }.flowOn(Dispatchers.IO)

    /** Drives [streamFn] for [durationMs], turning the raw byte/elapsed callbacks into
     * periodic [ThroughputSample]s (emitted via [onSample]) and a final [ThroughputResult]. */
    private suspend fun measureThroughput(
        durationMs: Long,
        streamFn: ByteStreamFn,
        onSample: suspend (ThroughputSample) -> Unit
    ): ThroughputResult {
        val samples = mutableListOf<ThroughputSample>()
        var totalBytes = 0L
        var intervalBytes = 0L
        var lastSampleElapsed = 0L

        streamFn(durationMs) { bytesTransferred, elapsedMs ->
            totalBytes += bytesTransferred
            intervalBytes += bytesTransferred
            val sinceLastSample = elapsedMs - lastSampleElapsed
            if (sinceLastSample >= sampleIntervalMs) {
                val intervalSec = sinceLastSample / 1000.0
                val instantMbps = if (intervalSec > 0) (intervalBytes * 8.0 / 1_000_000.0) / intervalSec else 0.0
                val sample = ThroughputSample(elapsedMs, totalBytes, instantMbps)
                samples.add(sample)
                onSample(sample)
                intervalBytes = 0L
                lastSampleElapsed = elapsedMs
            }
        }

        val actualDurationMs = samples.lastOrNull()?.elapsedMs ?: 0L
        return ThroughputResult.from(totalBytes, actualDurationMs, samples)
    }
}
