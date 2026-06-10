package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.flow.Flow

/**
 * Repository that runs a full internet speed test (latency, download, upload)
 * against Cloudflare's public speed test endpoints and streams progress as a [Flow].
 */
interface SpeedTestRepository {

    /**
     * Runs latency, download, and upload phases in order, emitting a [SpeedTestEvent]
     * for each measurement as it becomes available. The flow completes after the
     * upload phase finishes, or emits [SpeedTestEvent.Failed] and completes if a
     * phase cannot be measured.
     */
    fun runSpeedTest(): Flow<SpeedTestEvent>
}
