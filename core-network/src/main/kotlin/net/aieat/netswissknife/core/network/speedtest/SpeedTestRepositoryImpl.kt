package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationContext
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive

/** Streams bytes for a fixed wall-clock [duration][Long], invoking [onChunk] for every
 * read/write with the number of bytes moved and the elapsed time since the stream started. */
internal typealias ByteStreamFn = suspend (durationMs: Long, onChunk: suspend (bytesTransferred: Int, elapsedMs: Long) -> Unit) -> Unit

/** Shared limits and per-run session factory for a full speed test. */
object SpeedTestOperation {
    const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    private const val LOADED_LATENCY_REQUESTS = 1

    fun newSession(
        timeoutMs: Long = DEFAULT_TIMEOUT_MILLIS,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession = newSession(SpeedTestConfig(), timeoutMs, clock)

    /** Creates a session whose default capacity covers the largest transfer plus loaded RTT. */
    fun newSession(
        config: SpeedTestConfig,
        timeoutMs: Long = DEFAULT_TIMEOUT_MILLIS,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession {
        val normalized = config.normalized()
        val transferStreams = maxOf(normalized.downloadStreams, normalized.uploadStreams)
        return OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = timeoutMs.coerceIn(1L, OperationBudget.DEFAULT_INTERACTIVE_TIMEOUT_MILLIS),
                maxConcurrentProbes = transferStreams + LOADED_LATENCY_REQUESTS,
                maxResponseBytes = OperationBudget.DEFAULT_MAX_RESPONSE_BYTES,
                clock = clock,
            )
        )
    }
}

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
    private val uploadStream: ByteStreamFn = DEFAULT_UPLOAD,
    private val monotonicTimeNs: () -> Long = { System.nanoTime() },
    private val connectionFactory: (url: String, method: String) -> HttpURLConnection =
        { url, method -> openConnection(url, method) },
    private val transferEngine: TransferEngine? = null,
    private val speedTestConfig: SpeedTestConfig = SpeedTestConfig(),
    private val testBaseUrl: String = BASE_URL,
) : SpeedTestRepository {

    /** Keeps the existing full-argument JVM constructor available to compiled callers. */
    constructor(
        latencyProbeCount: Int,
        latencyTimeoutMs: Int,
        downloadDurationMs: Long,
        uploadDurationMs: Long,
        sampleIntervalMs: Long,
        latencyProbe: suspend (timeoutMs: Int) -> Long,
        downloadStream: ByteStreamFn,
        uploadStream: ByteStreamFn,
        monotonicTimeNs: () -> Long,
    ) : this(
        latencyProbeCount,
        latencyTimeoutMs,
        downloadDurationMs,
        uploadDurationMs,
        sampleIntervalMs,
        latencyProbe,
        downloadStream,
        uploadStream,
        monotonicTimeNs,
        { url, method -> openConnection(url, method) },
    )

    /** Preserves the former explicit connection-factory constructor for compiled integrations. */
    constructor(
        latencyProbeCount: Int,
        latencyTimeoutMs: Int,
        downloadDurationMs: Long,
        uploadDurationMs: Long,
        sampleIntervalMs: Long,
        latencyProbe: suspend (timeoutMs: Int) -> Long,
        downloadStream: ByteStreamFn,
        uploadStream: ByteStreamFn,
        monotonicTimeNs: () -> Long,
        connectionFactory: (url: String, method: String) -> HttpURLConnection,
    ) : this(
        latencyProbeCount,
        latencyTimeoutMs,
        downloadDurationMs,
        uploadDurationMs,
        sampleIntervalMs,
        latencyProbe,
        downloadStream,
        uploadStream,
        monotonicTimeNs,
        connectionFactory,
        null,
        SpeedTestConfig(),
    )

    /** Engine-backed implementation used by production and deterministic fake-engine tests. */
    constructor(
        transferEngine: TransferEngine,
        config: SpeedTestConfig = SpeedTestConfig(),
        baseUrl: String = BASE_URL
    ) : this(
        transferEngine = transferEngine,
        speedTestConfig = config.normalized(),
        testBaseUrl = baseUrl.trimEnd('/')
    )

    companion object {
        const val BASE_URL = "https://speed.cloudflare.com"
        const val DEFAULT_LATENCY_PROBE_COUNT = 10
        const val DEFAULT_LATENCY_TIMEOUT_MS = 5_000
        const val DEFAULT_PHASE_DURATION_MS = 10_000L
        const val DEFAULT_SAMPLE_INTERVAL_MS = 200L

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val WARMUP_REQUESTS = 2
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
            val startNs = System.nanoTime()
            fun elapsedMs(): Long = (System.nanoTime() - startNs) / 1_000_000L
            val buffer = ByteArray(CHUNK_SIZE)
            while (elapsedMs() < durationMs) {
                currentCoroutineContext().ensureActive()
                val conn = openConnection("$BASE_URL/__down?bytes=$DOWNLOAD_PAYLOAD_BYTES", "GET")
                try {
                    conn.connect()
                    conn.inputStream.use { stream ->
                        while (elapsedMs() < durationMs) {
                            currentCoroutineContext().ensureActive()
                            val read = stream.read(buffer)
                            if (read == -1) break
                            onChunk(read, elapsedMs())
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

        val DEFAULT_UPLOAD: ByteStreamFn = { durationMs, onChunk ->
            val startNs = System.nanoTime()
            fun elapsedMs(): Long = (System.nanoTime() - startNs) / 1_000_000L
            val payload = ByteArray(CHUNK_SIZE).also { SecureRandom().nextBytes(it) }
            while (elapsedMs() < durationMs) {
                currentCoroutineContext().ensureActive()
                val conn = openConnection("$BASE_URL/__up", "POST").apply {
                    doOutput = true
                    setChunkedStreamingMode(CHUNK_SIZE)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                try {
                    conn.connect()
                    conn.outputStream.use { out ->
                        var written = 0L
                        while (written < UPLOAD_PAYLOAD_BYTES && elapsedMs() < durationMs) {
                            currentCoroutineContext().ensureActive()
                            val toWrite = minOf(CHUNK_SIZE.toLong(), UPLOAD_PAYLOAD_BYTES - written).toInt()
                            out.write(payload, 0, toWrite)
                            written += toWrite
                            onChunk(toWrite, elapsedMs())
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
        // The default budget belongs to each collection, not to the cold Flow value.
        emitAll(runSpeedTest(SpeedTestOperation.newSession(speedTestConfig)))
    }

    override fun runSpeedTest(operationSession: OperationSession): Flow<SpeedTestEvent> =
        if (transferEngine != null) runWithTransferEngine(operationSession, transferEngine, speedTestConfig.normalized(), testBaseUrl)
        else runLegacy(operationSession)

    override fun runSpeedTest(operationSession: OperationSession, config: SpeedTestConfig): Flow<SpeedTestEvent> =
        if (transferEngine != null) runWithTransferEngine(operationSession, transferEngine, config.normalized(), testBaseUrl)
        else runLegacy(operationSession)

    private fun runLegacy(operationSession: OperationSession): Flow<SpeedTestEvent> = channelFlow {
        var currentPhase = SpeedTestPhase.LATENCY
        try {
            OperationRunner.run(operationSession) {
                // ── Phase 1: latency ─────────────────────────────────────────
                val latencySamples = mutableListOf<LatencySample>()
                for (seq in 1..latencyProbeCount) {
                    val rtt = try {
                        ensureCurrentOperationActive()
                        if (latencyProbe === DEFAULT_LATENCY_PROBE) {
                            runManagedLatencyProbe(this, latencyTimeoutMs)
                        } else latencyProbe(latencyTimeoutMs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ensureCurrentOperationActive()
                        send(SpeedTestEvent.Failed(SpeedTestPhase.LATENCY, ErrorInfo(ErrorCode.NETWORK_REQUEST_FAILED, developerMessage = e.message ?: "Latency probe failed")))
                        return@run
                    }
                    ensureCurrentOperationActive()
                    val sample = LatencySample(seq, rtt)
                    latencySamples.add(sample)
                    ensureCurrentOperationActive()
                    send(SpeedTestEvent.LatencyProgress(sample, latencyProbeCount))
                }
                ensureCurrentOperationActive()
                send(SpeedTestEvent.LatencyFinished(LatencyStats.compute(latencySamples)))

                // ── Phase 2: download ────────────────────────────────────────
                currentPhase = SpeedTestPhase.DOWNLOAD
                val downloadResult = try {
                    measureThroughput(downloadDurationMs, downloadStream, this) {
                        send(SpeedTestEvent.DownloadProgress(it))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureCurrentOperationActive()
                    send(SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, ErrorInfo(ErrorCode.NETWORK_REQUEST_FAILED, developerMessage = e.message ?: "Download test failed")))
                    return@run
                }
                ensureCurrentOperationActive()
                send(SpeedTestEvent.DownloadFinished(downloadResult))

                // ── Phase 3: upload ─────────────────────────────────────────
                currentPhase = SpeedTestPhase.UPLOAD
                val uploadResult = try {
                    measureThroughput(uploadDurationMs, uploadStream, this) {
                        send(SpeedTestEvent.UploadProgress(it))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureCurrentOperationActive()
                    send(SpeedTestEvent.Failed(SpeedTestPhase.UPLOAD, ErrorInfo(ErrorCode.NETWORK_REQUEST_FAILED, developerMessage = e.message ?: "Upload test failed")))
                    return@run
                }
                ensureCurrentOperationActive()
                send(SpeedTestEvent.UploadFinished(uploadResult))
            }
        } catch (cancelled: CancellationException) {
            if (operationSession.cancellationReason == CancellationReason.DEADLINE_EXCEEDED) {
                send(SpeedTestEvent.Failed(currentPhase, ErrorInfo(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Speed test timed out")))
            } else {
                throw cancelled
            }
        } catch (deadline: OperationDeadlineExceededException) {
            if (operationSession.cancellationReason == CancellationReason.DEADLINE_EXCEEDED) {
                send(SpeedTestEvent.Failed(currentPhase, ErrorInfo(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Speed test timed out")))
            } else {
                throw deadline
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun runWithTransferEngine(
        operationSession: OperationSession,
        engine: TransferEngine,
        config: SpeedTestConfig,
        baseUrl: String
    ): Flow<SpeedTestEvent> = channelFlow {
        var currentPhase = SpeedTestPhase.LATENCY
        try {
            OperationRunner.run(operationSession) {
                // Optional metadata is best-effort and deliberately precedes measurements.
                runCatching { engine.serverInfo("$baseUrl/meta") }
                    .getOrNull()?.let { info ->
                        ensureCurrentOperationActive()
                        send(SpeedTestEvent.ServerInfoReceived(info))
                    }

                val connectRtt =
                    try {
                        val endpoint = URI(baseUrl)
                        val port =
                            when {
                                endpoint.port > 0 -> endpoint.port
                                endpoint.scheme == "https" -> 443
                                else -> 80
                            }
                        engine.connectRtt(
                            endpoint.host,
                            port,
                            operationSession,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                ensureCurrentOperationActive()
                repeat(WARMUP_REQUESTS) {
                    ensureCurrentOperationActive()
                    runCatching { engine.httpRtt("$baseUrl/__down?bytes=0") }
                }

                val latencySamples = mutableListOf<LatencySample>()
                repeat(config.latencyProbes) { index ->
                    ensureCurrentOperationActive()
                    val rtt = engine.httpRtt("$baseUrl/__down?bytes=0").correctedMs
                    ensureCurrentOperationActive()
                    val sample = LatencySample(index + 1, rtt)
                    latencySamples += sample
                    send(SpeedTestEvent.LatencyProgress(sample, config.latencyProbes))
                }
                val idleStats = LatencyStats.compute(latencySamples).copy(connectRttMs = connectRtt)
                send(SpeedTestEvent.LatencyFinished(idleStats))

                currentPhase = SpeedTestPhase.DOWNLOAD
                val (download, loadedDown) = measureEnginePhase(
                    engine = engine,
                    operationSession = operationSession,
                    config = config,
                    baseUrl = baseUrl,
                    transfer = engine.download("$baseUrl/__down?bytes=$DOWNLOAD_PAYLOAD_BYTES", config.downloadStreams, config.phaseDurationMs, operationSession),
                    report = { send(SpeedTestEvent.DownloadProgress(it)) },
                    loadedReport = { send(SpeedTestEvent.LoadedLatencySample(currentPhase, it)) }
                )
                send(SpeedTestEvent.LoadedLatencyFinished(currentPhase, loadedDown))
                send(SpeedTestEvent.DownloadFinished(download))

                currentPhase = SpeedTestPhase.UPLOAD
                val (upload, loadedUp) = measureEnginePhase(
                    engine = engine,
                    operationSession = operationSession,
                    config = config,
                    baseUrl = baseUrl,
                    transfer = engine.upload("$baseUrl/__up", config.uploadStreams, config.phaseDurationMs, operationSession),
                    report = { send(SpeedTestEvent.UploadProgress(it)) },
                    loadedReport = { send(SpeedTestEvent.LoadedLatencySample(currentPhase, it)) }
                )
                send(SpeedTestEvent.LoadedLatencyFinished(currentPhase, loadedUp))
                send(SpeedTestEvent.UploadFinished(upload))
            }
        } catch (cancelled: CancellationException) {
            if (operationSession.cancellationReason == CancellationReason.DEADLINE_EXCEEDED) {
                send(SpeedTestEvent.Failed(currentPhase, ErrorInfo(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Speed test timed out")))
            } else throw cancelled
        } catch (failure: Exception) {
            if (operationSession.cancellationReason == CancellationReason.DEADLINE_EXCEEDED) {
                send(SpeedTestEvent.Failed(currentPhase, ErrorInfo(ErrorCode.NETWORK_TIMEOUT, developerMessage = "Speed test timed out")))
            } else {
                ensureCurrentOperationActive()
                send(SpeedTestEvent.Failed(currentPhase, ErrorInfo(ErrorCode.NETWORK_REQUEST_FAILED, developerMessage = failure.message ?: "Speed test failed")))
            }
        }
    }

    private suspend fun measureEnginePhase(
        engine: TransferEngine,
        operationSession: OperationSession,
        config: SpeedTestConfig,
        baseUrl: String,
        transfer: Flow<ChunkEvent>,
        report: suspend (ThroughputSample) -> Unit,
        loadedReport: suspend (Long) -> Unit
    ): Pair<ThroughputResult, LatencyStats> = coroutineScope {
        val loadedSamples = mutableListOf<LatencySample>()
        var loadedSequence = 0
        val loadedJob = launch {
            while (true) {
                delay(config.loadedLatencyIntervalMs)
                ensureCurrentOperationActive()
                val sample = operationSession.concurrencyLimiter.withPermit {
                    engine.httpRtt("$baseUrl/__down?bytes=0").correctedMs
                }
                ensureCurrentOperationActive()
                loadedSequence++
                loadedReport(sample)
                loadedSamples += LatencySample(loadedSequence, sample)
            }
        }

        try {
            var totalBytes = 0L
            var intervalBytes = 0L
            var previousElapsed = 0L
            var lastSampleElapsed = 0L
            val samples = mutableListOf<ThroughputSample>()
            val startNs = monotonicTimeNs()
            transfer.collect { chunk ->
                ensureCurrentOperationActive()
                totalBytes += chunk.bytes
                intervalBytes += chunk.bytes
                val elapsed = maxOf(previousElapsed, chunk.elapsedMs.coerceAtLeast(0L))
                previousElapsed = elapsed
                if (elapsed - lastSampleElapsed >= config.sampleIntervalMs) {
                    val duration = elapsed - lastSampleElapsed
                    val sample = ThroughputSample(
                        elapsedMs = elapsed,
                        bytesTransferred = totalBytes,
                        instantMbps = (intervalBytes * 8.0 / 1_000_000.0) / (duration / 1000.0)
                    )
                    samples += sample
                    report(sample)
                    intervalBytes = 0L
                    lastSampleElapsed = elapsed
                }
            }
            loadedJob.cancelAndJoin()
            val durationMs = maxOf(previousElapsed, ((monotonicTimeNs() - startNs) / 1_000_000L).coerceAtLeast(0L))
            if (intervalBytes > 0L) {
                val intervalMs = (durationMs - lastSampleElapsed).coerceAtLeast(1L)
                val finalSample = ThroughputSample(
                    elapsedMs = durationMs,
                    bytesTransferred = totalBytes,
                    instantMbps = (intervalBytes * 8.0 / 1_000_000.0) / (intervalMs / 1000.0)
                )
                samples += finalSample
                report(finalSample)
            }
            val stats = LatencyStats.compute(loadedSamples)
            ThroughputResult.from(totalBytes, durationMs, samples) to stats
        } finally {
            loadedJob.cancelAndJoin()
        }
    }

    private suspend fun runManagedLatencyProbe(context: OperationContext, timeoutMs: Int): Long {
        val lease = openOwnedConnection(context, "$BASE_URL/__down?bytes=0", "GET").apply {
            connectionTimeout(timeoutMs)
        }
        return try {
            val start = monotonicTimeNs()
            lease.connection.connect()
            ensureCurrentOperationActive()
            lease.connection.responseCode
            ensureCurrentOperationActive()
            (monotonicTimeNs() - start) / 1_000_000L
        } finally {
            releaseConnection(context, lease)
        }
    }

    private suspend fun runManagedDownload(
        durationMs: Long,
        context: OperationContext,
        onChunk: suspend (Int, Long) -> Unit,
    ) {
        val startNs = monotonicTimeNs()
        fun elapsedMs() = (monotonicTimeNs() - startNs) / 1_000_000L
        val buffer = ByteArray(CHUNK_SIZE)
        while (elapsedMs() < durationMs) {
            ensureCurrentOperationActive()
            val lease = openOwnedConnection(context, "$BASE_URL/__down?bytes=$DOWNLOAD_PAYLOAD_BYTES", "GET")
            try {
                lease.connection.connect()
                ensureCurrentOperationActive()
                val stream = lease.trackStream(lease.connection.inputStream) as java.io.InputStream
                try {
                    ensureCurrentOperationActive()
                    while (elapsedMs() < durationMs) {
                        ensureCurrentOperationActive()
                        val read = stream.read(buffer)
                        ensureCurrentOperationActive()
                        if (read == -1) break
                        onChunk(read, elapsedMs())
                    }
                } finally {
                    lease.releaseStream(stream)
                }
            } finally {
                releaseConnection(context, lease)
            }
        }
    }

    private suspend fun runManagedUpload(
        durationMs: Long,
        context: OperationContext,
        onChunk: suspend (Int, Long) -> Unit,
    ) {
        val startNs = monotonicTimeNs()
        fun elapsedMs() = (monotonicTimeNs() - startNs) / 1_000_000L
        val payload = ByteArray(CHUNK_SIZE).also { SecureRandom().nextBytes(it) }
        while (elapsedMs() < durationMs) {
            ensureCurrentOperationActive()
            val lease = openOwnedConnection(context, "$BASE_URL/__up", "POST").apply {
                connection.doOutput = true
                connection.setChunkedStreamingMode(CHUNK_SIZE)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
            }
            try {
                lease.connection.connect()
                ensureCurrentOperationActive()
                val out = lease.trackStream(lease.connection.outputStream) as java.io.OutputStream
                try {
                    ensureCurrentOperationActive()
                    var written = 0L
                    while (written < UPLOAD_PAYLOAD_BYTES && elapsedMs() < durationMs) {
                        ensureCurrentOperationActive()
                        val toWrite = minOf(CHUNK_SIZE.toLong(), UPLOAD_PAYLOAD_BYTES - written).toInt()
                        out.write(payload, 0, toWrite)
                        ensureCurrentOperationActive()
                        written += toWrite
                        onChunk(toWrite, elapsedMs())
                    }
                } finally {
                    lease.releaseStream(out)
                }
                lease.connection.responseCode
                ensureCurrentOperationActive()
            } finally {
                releaseConnection(context, lease)
            }
        }
    }

    private fun openOwnedConnection(context: OperationContext, url: String, method: String): ConnectionLease {
        val connection = connectionFactory(url, method).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CONNECT_TIMEOUT_MS
        }
        val lease = ConnectionLease(connection)
        return context.resources.register(lease)
    }

    private fun releaseConnection(context: OperationContext, lease: ConnectionLease) {
        if (context.resources.release(lease)) lease.close()
    }

    private fun ConnectionLease.connectionTimeout(timeoutMs: Int) {
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
    }

    private class ConnectionLease(val connection: HttpURLConnection) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val activeStream = AtomicReference<Closeable?>(null)

        fun trackStream(stream: Closeable): Closeable {
            if (closed.get()) {
                stream.close()
                return stream
            }
            activeStream.set(stream)
            if (closed.get() && activeStream.compareAndSet(stream, null)) stream.close()
            return stream
        }

        fun releaseStream(stream: Closeable) {
            if (activeStream.compareAndSet(stream, null)) stream.close()
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            var failure: Throwable? = null
            try {
                // Disconnect first so the owning socket releases an in-flight stream operation.
                connection.disconnect()
            } catch (closeFailure: Throwable) {
                failure = closeFailure
            }
            try {
                activeStream.getAndSet(null)?.close()
            } catch (closeFailure: Throwable) {
                val primary = failure
                if (primary == null) failure = closeFailure
                else if (primary !== closeFailure) primary.addSuppressed(closeFailure)
            }
            failure?.let { throw it }
        }
    }

    /** Drives [streamFn] for [durationMs], turning the raw byte/elapsed callbacks into
     * periodic [ThroughputSample]s (emitted via [onSample]) and a final [ThroughputResult]. */
    private suspend fun measureThroughput(
        durationMs: Long,
        streamFn: ByteStreamFn,
        operationContext: OperationContext,
        onSample: suspend (ThroughputSample) -> Unit
    ): ThroughputResult {
        val samples = mutableListOf<ThroughputSample>()
        var totalBytes = 0L
        var intervalBytes = 0L
        var lastSampleElapsed = 0L
        var lastCallbackElapsed = 0L
        val measurementStartNs = monotonicTimeNs()
        val effectiveSampleIntervalMs = sampleIntervalMs.coerceAtLeast(1L)

        val transfer: suspend (suspend (Int, Long) -> Unit) -> Unit = when {
            streamFn === DEFAULT_DOWNLOAD -> { callback -> runManagedDownload(durationMs, operationContext, callback) }
            streamFn === DEFAULT_UPLOAD -> { callback -> runManagedUpload(durationMs, operationContext, callback) }
            else -> { callback -> streamFn(durationMs, callback) }
        }
        transfer { bytesTransferred, elapsedMs ->
            ensureCurrentOperationActive()
            val safeElapsedMs = maxOf(lastCallbackElapsed, elapsedMs.coerceAtLeast(0L))
            totalBytes += bytesTransferred
            intervalBytes += bytesTransferred
            lastCallbackElapsed = safeElapsedMs
            val sinceLastSample = safeElapsedMs - lastSampleElapsed
            if (sinceLastSample >= effectiveSampleIntervalMs) {
                val intervalSec = sinceLastSample / 1000.0
                val instantMbps = if (intervalSec > 0) (intervalBytes * 8.0 / 1_000_000.0) / intervalSec else 0.0
                val sample = ThroughputSample(safeElapsedMs, totalBytes, instantMbps)
                samples.add(sample)
                onSample(sample)
                intervalBytes = 0L
                lastSampleElapsed = safeElapsedMs
            }
        }
        ensureCurrentOperationActive()

        val clockDurationMs = ((monotonicTimeNs() - measurementStartNs) / 1_000_000L).coerceAtLeast(0L)
        val actualDurationMs = maxOf(lastCallbackElapsed, clockDurationMs)

        // The final callback often arrives before the configured sample interval
        // (or the stream ends between samples). Account for those bytes instead
        // of silently excluding them from the reported peak and sample history.
        val finalIntervalMs = actualDurationMs - lastSampleElapsed
        if (intervalBytes > 0L) {
            val intervalSec = finalIntervalMs / 1000.0
            val instantMbps = if (intervalSec > 0.0) {
                (intervalBytes * 8.0 / 1_000_000.0) / intervalSec
            } else {
                0.0
            }
            val finalSample = ThroughputSample(actualDurationMs, totalBytes, instantMbps)
            samples.add(finalSample)
            onSample(finalSample)
        }

        return ThroughputResult.from(totalBytes, actualDurationMs, samples)
    }
}
