package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Collections
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.testkit.FakeClock

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

        @Test
        fun `late custom probe return after deadline emits no latency success`() = runTest {
            val clock = FakeClock()
            val probeEntered = CompletableDeferred<Unit>()
            val releaseProbe = CompletableDeferred<Unit>()
            val session = OperationSession(
                OperationBudget.start(
                    requirement = OperationRequirement.ANY_NETWORK,
                    timeoutMillis = 100,
                    maxConcurrentProbes = 1,
                    clock = clock,
                )
            )
            val repository = SpeedTestRepositoryImpl(
                latencyProbeCount = 1,
                latencyProbe = {
                    probeEntered.complete(Unit)
                    releaseProbe.await()
                    12L
                },
                downloadStream = fakeStream(emptyList()),
                uploadStream = fakeStream(emptyList()),
            )
            val collection = async(Dispatchers.IO) {
                repository.runSpeedTest(session).toList()
            }

            withContext(Dispatchers.IO) { withTimeout(3_000) { probeEntered.await() } }
            clock.advanceBy(100_000_000L)
            releaseProbe.complete(Unit)
            val events = withContext(Dispatchers.IO) { withTimeout(3_000) { collection.await() } }

            assertTrue(events.none { it is SpeedTestEvent.LatencyProgress || it is SpeedTestEvent.LatencyFinished })
            val failure = events.single() as SpeedTestEvent.Failed
            assertEquals(SpeedTestPhase.LATENCY, failure.phase)
            assertEquals("Speed test timed out", failure.message)
        }
    }

    @Nested
    @DisplayName("download / upload phases")
    inner class ThroughputPhases {

        @Test
        fun `cancelling an active download disconnects its connection and does not emit a failed result`() = runTest {
            val readEntered = CountDownLatch(1)
            val disconnects = AtomicInteger()
            val streamCloses = AtomicInteger()
            val released = CountDownLatch(1)
            val disconnected = CountDownLatch(1)
            val streamClosedAfterDisconnect = AtomicBoolean(false)
            val connection = object : HttpURLConnection(URL("https://speed.cloudflare.com/__down")) {
                override fun connect() = Unit
                override fun usingProxy() = false
                override fun disconnect() {
                    disconnects.incrementAndGet()
                    released.countDown()
                    disconnected.countDown()
                }
                override fun getInputStream(): InputStream = object : InputStream() {
                    override fun read(): Int {
                        readEntered.countDown()
                        released.await(5, TimeUnit.SECONDS)
                        throw IOException("closed by disconnect")
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readEntered.countDown()
                        released.await(5, TimeUnit.SECONDS)
                        throw IOException("closed by disconnect")
                    }
                    override fun close() {
                        streamCloses.incrementAndGet()
                        streamClosedAfterDisconnect.set(disconnected.await(3, TimeUnit.SECONDS))
                    }
                }
            }
            val repository = SpeedTestRepositoryImpl(
                latencyProbeCount = 1,
                latencyProbe = { 8L },
                connectionFactory = { _, _ -> connection },
            )
            val session = SpeedTestOperation.newSession()
            val events = Collections.synchronizedList(mutableListOf<SpeedTestEvent>())
            val collector = async(Dispatchers.IO) { repository.runSpeedTest(session).collect { events.add(it) } }

            assertTrue(withContext(Dispatchers.IO) { readEntered.await(3, TimeUnit.SECONDS) })
            session.cancel(CancellationReason.USER_STOP)
            withContext(Dispatchers.IO) { assertTrue(released.await(3, TimeUnit.SECONDS)) }
            var wasCancelled = false
            try {
                collector.await()
            } catch (_: CancellationException) {
                wasCancelled = true
            }
            assertTrue(wasCancelled)
            assertEquals(1, disconnects.get())
            assertTrue(events.none { it is SpeedTestEvent.Failed })
            assertEquals(1, streamCloses.get())
            assertTrue(streamClosedAfterDisconnect.get(), "disconnect must unblock the stream before close")
        }

        @Test
        fun `deadline disconnects an active download connection`() = runTest {
            val readEntered = CountDownLatch(1)
            val disconnects = AtomicInteger()
            val released = CountDownLatch(1)
            val connection = object : HttpURLConnection(URL("https://speed.cloudflare.com/__down")) {
                override fun connect() = Unit
                override fun usingProxy() = false
                override fun disconnect() {
                    disconnects.incrementAndGet()
                    released.countDown()
                }
                override fun getInputStream(): InputStream = object : InputStream() {
                    override fun read(): Int {
                        readEntered.countDown()
                        released.await(5, TimeUnit.SECONDS)
                        throw IOException("closed by disconnect")
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readEntered.countDown()
                        released.await(5, TimeUnit.SECONDS)
                        throw IOException("closed by disconnect")
                    }
                }
            }
            val repository = SpeedTestRepositoryImpl(
                latencyProbeCount = 1,
                latencyProbe = { 8L },
                connectionFactory = { _, _ -> connection },
            )
            val collector = async(Dispatchers.IO) {
                repository.runSpeedTest(SpeedTestOperation.newSession(timeoutMs = 500L)).toList()
            }

            assertTrue(withContext(Dispatchers.IO) { readEntered.await(3, TimeUnit.SECONDS) })
            withContext(Dispatchers.IO) { assertTrue(released.await(3, TimeUnit.SECONDS)) }
            val events = collector.await()
            assertEquals(1, disconnects.get())
            assertEquals(
                SpeedTestEvent.Failed(SpeedTestPhase.DOWNLOAD, "Speed test timed out"),
                events.last(),
            )
            assertTrue(events.none { it is SpeedTestEvent.DownloadFinished || it is SpeedTestEvent.UploadFinished })
        }

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

        @Test
        fun `accounts for final partial sample after the last interval`() = runTest {
            val repo = repoWith(
                latencyProbeCount = 1,
                latencyRtts = listOf(10L),
                downloadChunks = listOf(500_000 to 1_000L, 250_000 to 1_200L),
                uploadChunks = emptyList()
            )

            val events = repo.runSpeedTest().toList()
            val finished = events.filterIsInstance<SpeedTestEvent.DownloadFinished>().single()

            assertEquals(750_000L, finished.result.bytesTransferred)
            assertEquals(1_200L, finished.result.durationMs)
            assertEquals(2, finished.result.samples.size)
            assertEquals(750_000L, finished.result.samples.last().bytesTransferred)
            assertTrue(finished.result.samples.last().instantMbps > 0.0)
        }

        @Test
        fun `propagates cancellation instead of reporting a failed phase`() = runTest {
            val cancellation = CancellationException("cancelled")
            val repo = SpeedTestRepositoryImpl(
                latencyProbeCount = 1,
                latencyProbe = { throw cancellation },
                downloadStream = fakeStream(emptyList()),
                uploadStream = fakeStream(emptyList())
            )

            var thrown: CancellationException? = null
            try {
                repo.runSpeedTest().toList()
            } catch (e: CancellationException) {
                thrown = e
            }
            assertTrue(thrown != null)
        }
    }
}
