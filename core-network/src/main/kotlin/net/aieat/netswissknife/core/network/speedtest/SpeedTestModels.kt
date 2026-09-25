package net.aieat.netswissknife.core.network.speedtest

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

/** Phases of a full speed test run, in execution order. */
enum class SpeedTestPhase {
    LATENCY, DOWNLOAD, UPLOAD
}

/** A single round-trip latency probe result. */
data class LatencySample(
    val sequence: Int,
    val rtTimeMs: Long
)

/** Aggregated statistics over a set of [LatencySample]s. */
data class LatencyStats(
    val minMs: Long,
    val avgMs: Double,
    val maxMs: Long,
    val jitterMs: Double,
    val samples: List<LatencySample>,
    /** TCP handshake RTT measured before the reusable HTTP latency probes. */
    val connectRttMs: Long? = null
) {
    companion object {
        val EMPTY = LatencyStats(0L, 0.0, 0L, 0.0, emptyList())

        /** Computes min/avg/max/jitter from a list of samples. Jitter is the mean
         * absolute difference between consecutive RTTs (RFC 3550 style approximation). */
        fun compute(samples: List<LatencySample>): LatencyStats {
            if (samples.isEmpty()) return EMPTY
            val rtts = samples.map { it.rtTimeMs }
            val jitter = if (rtts.size > 1) {
                rtts.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()
            } else 0.0
            return LatencyStats(
                minMs = rtts.min(),
                avgMs = rtts.average(),
                maxMs = rtts.max(),
                jitterMs = jitter,
                samples = samples
            )
        }
    }
}

/** A throughput measurement taken at [elapsedMs] since the phase started. */
data class ThroughputSample(
    val elapsedMs: Long,
    val bytesTransferred: Long,
    val instantMbps: Double
)

/** Aggregated result of a download or upload throughput phase. */
data class ThroughputResult(
    val avgMbps: Double,
    val peakMbps: Double,
    val bytesTransferred: Long,
    val durationMs: Long,
    val samples: List<ThroughputSample>
) {
    companion object {
        val EMPTY = ThroughputResult(0.0, 0.0, 0L, 0L, emptyList())

        /** Builds a result from accumulated bytes/duration and the per-interval samples. */
        fun from(totalBytes: Long, durationMs: Long, samples: List<ThroughputSample>): ThroughputResult {
            val avgMbps = if (durationMs > 0) {
                (totalBytes * 8.0 / 1_000_000.0) / (durationMs / 1000.0)
            } else 0.0
            val peakMbps = samples.maxOfOrNull { it.instantMbps } ?: avgMbps
            return ThroughputResult(
                avgMbps = avgMbps,
                peakMbps = peakMbps,
                bytesTransferred = totalBytes,
                durationMs = durationMs,
                samples = samples
            )
        }
    }
}

/** Final, combined result of a completed speed test run. */
data class ServerInfo(
    val colo: String? = null,
    val clientIp: String? = null,
    val asn: String? = null,
    val country: String? = null
)

/** Configuration used for one run. Values are clamped by [SpeedTestConfig.normalized]. */
data class SpeedTestConfig(
    val latencyProbes: Int = 10,
    val downloadStreams: Int = 4,
    val uploadStreams: Int = 2,
    val phaseDurationMs: Long = 10_000,
    val sampleIntervalMs: Long = 200,
    val loadedLatencyIntervalMs: Long = 250
) {
    fun normalized() = copy(
        latencyProbes = latencyProbes.coerceIn(1, 30),
        downloadStreams = downloadStreams.coerceIn(1, 8),
        uploadStreams = uploadStreams.coerceIn(1, 4),
        phaseDurationMs = phaseDurationMs.coerceIn(1_000, 60_000),
        sampleIntervalMs = sampleIntervalMs.coerceIn(50, 2_000),
        loadedLatencyIntervalMs = loadedLatencyIntervalMs.coerceIn(50, 5_000)
    )
}

data class SpeedTestResult(
    val latency: LatencyStats,
    val download: ThroughputResult,
    val upload: ThroughputResult,
    val serverInfo: ServerInfo? = null,
    val loadedLatencyDown: LatencyStats = LatencyStats.EMPTY,
    val loadedLatencyUp: LatencyStats = LatencyStats.EMPTY,
    val config: SpeedTestConfig = SpeedTestConfig()
)

/** Streaming events emitted while a speed test is in progress. */
sealed interface SpeedTestEvent {
    data class LatencyProgress(val sample: LatencySample, val total: Int) : SpeedTestEvent
    data class LatencyFinished(val stats: LatencyStats) : SpeedTestEvent
    data class ServerInfoReceived(val info: ServerInfo) : SpeedTestEvent
    data class LoadedLatencySample(val phase: SpeedTestPhase, val rttMs: Long) : SpeedTestEvent
    data class LoadedLatencyFinished(val phase: SpeedTestPhase, val stats: LatencyStats) : SpeedTestEvent
    data class DownloadProgress(val sample: ThroughputSample) : SpeedTestEvent
    data class DownloadFinished(val result: ThroughputResult) : SpeedTestEvent
    data class UploadProgress(val sample: ThroughputSample) : SpeedTestEvent
    data class UploadFinished(val result: ThroughputResult) : SpeedTestEvent
    data class Failed(val phase: SpeedTestPhase, val info: ErrorInfo) : SpeedTestEvent {
        constructor(phase: SpeedTestPhase, message: String) : this(
            phase,
            ErrorInfo(ErrorCode.UNKNOWN, developerMessage = message),
        )

        /** Source-compatible convenience for callers that construct or read the former string form. */
        val message: String get() = info.developerMessage ?: info.code.name.lowercase().replace('_', ' ')
    }
}
