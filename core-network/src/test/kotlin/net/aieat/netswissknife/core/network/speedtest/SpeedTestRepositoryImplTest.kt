package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SpeedTestRepositoryImpl")
class SpeedTestRepositoryImplTest {

    /** Fake stream that replays a fixed sequence of (bytes, elapsedMs) chunks, ignoring duration. */
    private fun fakeStream(chunks: List<Pair<Int, Long>>): ByteStreamFn = { _, onChunk ->
        chunks.forEach { (bytes, elapsedMs) -> onChunk(bytes, elapsedMs) }
    }

    private fun repoWith(
        latencyProbeCount: Int = 3,
        latencyRtts: List<Long> = listOf(10L, 12L, 11L),
        downloadChunks: List<Pair<Int, Long>> = listOf(1_250_000 to 1_000L, 1_250_000 to 2_000L),
        uploadChunks: List<Pair<Int, Long>> = listOf(625_000 to 1_000L, 625_000 to 2_000L)
    ): SpeedTestRepositoryImpl {
        var latencyCall = 0
        return SpeedTestRepositoryImpl(
            latencyProbeCount = latencyProbeCount,
            sampleIntervalMs = 500L,
            latencyProbe = { latencyRtts[latencyCall++] },
            downloadStream = fakeStream(downloadChunks),
            uploadStream = fakeStream(uploadChunks)
        )
    }

    @Nested
    @DisplayName("latency phase")
    inner class LatencyPhase {

        @Test
        fun `emits a progress event per probe followed by a finished event`() = runTest {
            val events = repoWith(latencyProbeCount = 3).runSpeedTest().toList()
            val progress = events.filterIsInstance<SpeedTestEvent.LatencyProgress>()
            assertEquals(3, progress.size)
            assertEquals(listOf(1, 2, 3), progress.map { it.sample.sequence })
            assertEquals(listOf(10L, 12L, 11L), progress.map { it.sample.rtTimeMs })

            val finished = events.filterIsInstance<SpeedTestEvent.LatencyFinished>().single()
            assertEquals(10L, finished.stats.minMs)
            assertEquals(12L, finished.stats.maxMs)
        }

        @Test
        fun `emits Failed and stops when a probe throws`() = runTest {
            val repo = SpeedTestRepositoryImpl(
                latencyProbeCount = 3,
                latencyProbe = { throw java.io.IOException("timed out") },
                downloadStream = fakeStream(emptyList()),
                uploadStream = fakeStream(emptyList())
            )
            val events = repo.runSpeedTest().toList()
            assertEquals(1, events.size)
            val failure = events.single() as SpeedTestEvent.Failed
            assertEquals(SpeedTestPhase.LATENCY, failure.phase)
            assertEquals("timed out", failure.message)
        }
    }

    @Nested
    @DisplayName("download / upload phases")
    inner class ThroughputPhases {

        @Test
        fun `streams progress samples and a final result for download`() = runTest {
            val events = repoWith().runSpeedTest().toList()
            val progress = events.filterIsInstance<SpeedTestEvent.DownloadProgress>()
            assertEquals(2, progress.size)
            assertEquals(1_250_000L, progress[0].sample.bytesTransferred)
            assertEquals(2_500_000L, progress[1].sample.bytesTransferred)

            val finished = events.filterIsInstance<SpeedTestEvent.DownloadFinished>().single()
            assertEquals(2_500_000L, finished.result.bytesTransferred)
            assertEquals(2_000L, finished.result.durationMs)
            // 2,500,000 bytes over 2s = 20,000,000 bits / 2s = 10 Mbps
            assertEquals(10.0, finished.result.avgMbps, 0.0001)
        }

        @Test
        fun `streams progress samples and a final result for upload`() = runTest {
            val events = repoWith().runSpeedTest().toList()
            val progress = events.filterIsInstance<SpeedTestEvent.UploadProgress>()
            assertEquals(2, progress.size)

            val finished = events.filterIsInstance<SpeedTestEvent.UploadFinished>().single()
            assertEquals(1_250_000L, finished.result.bytesTransferred)
            assertEquals(2_000L, finished.result.durationMs)
            // 1,250,000 bytes over 2s = 10,000,000 bits / 2s = 5 Mbps
            assertEquals(5.0, finished.result.avgMbps, 0.0001)
        }

        @Test
        fun `emits Failed and stops when the download stream throws`() = runTest {
            val repo = SpeedTestRepositoryImpl(
                latencyProbeCount = 1,
                latencyProbe = { 10L },
                downloadStream = { _, _ -> throw java.io.IOException("connection reset") },
                uploadStream = fakeStream(emptyList())
            )
            val events = repo.runSpeedTest().toList()
            assertTrue(events.last() is SpeedTestEvent.Failed)
            val failure = events.last() as SpeedTestEvent.Failed
            assertEquals(SpeedTestPhase.DOWNLOAD, failure.phase)
            // No upload events should be emitted after a download failure
            assertTrue(events.none { it is SpeedTestEvent.UploadProgress || it is SpeedTestEvent.UploadFinished })
        }

        @Test
        fun `runs phases in order latency, download, upload`() = runTest {
            val events = repoWith(latencyProbeCount = 1, latencyRtts = listOf(10L)).runSpeedTest().toList()
            val phaseOrder = events.mapNotNull {
                when (it) {
                    is SpeedTestEvent.LatencyFinished -> SpeedTestPhase.LATENCY
                    is SpeedTestEvent.DownloadFinished -> SpeedTestPhase.DOWNLOAD
                    is SpeedTestEvent.UploadFinished -> SpeedTestPhase.UPLOAD
                    else -> null
                }
            }
            assertEquals(listOf(SpeedTestPhase.LATENCY, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD), phaseOrder)
        }
    }
}
