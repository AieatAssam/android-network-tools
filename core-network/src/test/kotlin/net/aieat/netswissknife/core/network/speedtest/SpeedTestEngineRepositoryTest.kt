package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestEngineRepositoryTest {
    private class FakeEngine : TransferEngine {
        var info: ServerInfo? = ServerInfo(colo = "LHR", asn = "AS13335", country = "GB")
        var infoFails = false
        var connect: Long? = 7
        var connectSession: OperationSession? = null
        var streamsDown = 0
        var streamsUp = 0
        var cancelledDownload = false
        var suspendDownload = false
        var serverDuration = 12.5

        override suspend fun connectRtt(host: String, port: Int) = connect

        override suspend fun connectRtt(
            host: String,
            port: Int,
            operationSession: OperationSession,
        ): Long? {
            connectSession = operationSession
            return connectRtt(host, port)
        }

        override suspend fun httpRtt(url: String): HttpRtt {
            return HttpRtt(totalMs = 15, serverMs = serverDuration)
        }

        override suspend fun serverInfo(url: String): ServerInfo? {
            if (infoFails) error("metadata unavailable")
            return info
        }

        override fun download(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> = flow {
            streamsDown = streams
            try {
                if (suspendDownload) awaitCancellation()
                delay(800)
                emit(ChunkEvent(0, 1_000, 200))
                emit(ChunkEvent(1, 2_000, 450))
                emit(ChunkEvent(2, 3_000, 700))
            } catch (cancelled: CancellationException) {
                cancelledDownload = true
                throw cancelled
            }
        }

        override fun upload(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> = flow {
            streamsUp = streams
            delay(800)
            emit(ChunkEvent(0, 50, 500))
        }
    }

    @Test
    fun `server info leads events and idle samples subtract server duration with floor and connect RTT`() = runTest {
        val engine = FakeEngine()
        val events = SpeedTestRepositoryImpl(
            engine,
            SpeedTestConfig(latencyProbes = 2, downloadStreams = 4, uploadStreams = 2, phaseDurationMs = 1_000)
        ).runSpeedTest().toList()

        assertTrue(events.first() is SpeedTestEvent.ServerInfoReceived)
        assertEquals(ServerInfo(colo = "LHR", asn = "AS13335", country = "GB"), (events.first() as SpeedTestEvent.ServerInfoReceived).info)
        val samples = events.filterIsInstance<SpeedTestEvent.LatencyProgress>()
        assertEquals(listOf(2L, 2L), samples.map { it.sample.rtTimeMs }) // 15 - 12.5, narrowed to whole milliseconds
        val latency = events.filterIsInstance<SpeedTestEvent.LatencyFinished>().single().stats
        assertEquals(7L, latency.connectRttMs)
        assertEquals(4, engine.streamsDown)
        assertEquals(2, engine.streamsUp)
    }

    @Test
    fun `repository passes caller session to connect RTT adapter`() =
        runTest {
            val engine = FakeEngine()
            val session = OperationSession(OperationBudget.start(timeoutMillis = 10_000))

            val events =
                SpeedTestRepositoryImpl(
                    engine,
                    SpeedTestConfig(latencyProbes = 1, phaseDurationMs = 1_000),
                ).runSpeedTest(session).toList()

            assertTrue(events.any { it is SpeedTestEvent.LatencyFinished })
            assertEquals(session, engine.connectSession)
        }

    @Test
    fun `server time larger than request time floors corrected sample at zero`() = runTest {
        val engine = FakeEngine().apply { serverDuration = 30.0 }
        val events = SpeedTestRepositoryImpl(engine, SpeedTestConfig(latencyProbes = 1)).runSpeedTest().toList()
        assertEquals(0L, events.filterIsInstance<SpeedTestEvent.LatencyProgress>().single().sample.rtTimeMs)
    }

    @Test
    fun `aggregates bytes across configured streams and reports interval samples from the aggregate`() = runTest {
        val events = SpeedTestRepositoryImpl(
            FakeEngine(), SpeedTestConfig(latencyProbes = 1, phaseDurationMs = 1_000, sampleIntervalMs = 200)
        ).runSpeedTest().toList()
        val download = events.filterIsInstance<SpeedTestEvent.DownloadFinished>().single().result
        assertEquals(6_000L, download.bytesTransferred)
        assertEquals(3, download.samples.size)
        assertEquals(listOf(1_000L, 3_000L, 6_000L), download.samples.map { it.bytesTransferred })
        assertEquals(6_000L, download.samples.maxOf { it.bytesTransferred })
        assertEquals(0.068571, download.avgMbps, 0.00001)
        assertEquals(0.096, download.peakMbps, 0.00001)
    }

    @Test
    fun `loaded latency samples run during both transfer phases and finish with per-phase stats`() = runTest {
        val events = SpeedTestRepositoryImpl(
            FakeEngine(), SpeedTestConfig(latencyProbes = 1, phaseDurationMs = 1_000, loadedLatencyIntervalMs = 250)
        ).runSpeedTest().toList()
        val down = events.filterIsInstance<SpeedTestEvent.LoadedLatencySample>().filter { it.phase == SpeedTestPhase.DOWNLOAD }
        val up = events.filterIsInstance<SpeedTestEvent.LoadedLatencySample>().filter { it.phase == SpeedTestPhase.UPLOAD }
        assertEquals(3, down.size)
        assertEquals(3, up.size)
        val finished = events.filterIsInstance<SpeedTestEvent.LoadedLatencyFinished>()
        assertEquals(listOf(SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD), finished.map { it.phase })
        assertEquals(2.0, finished.first().stats.avgMs)
        assertEquals(
            events.filterIsInstance<SpeedTestEvent.LoadedLatencySample>()
                .filter { it.phase == SpeedTestPhase.DOWNLOAD }.map { it.rttMs },
            finished.first().stats.samples.map { it.rtTimeMs }
        )
    }

    @Test
    fun `loaded sample finishing on the transfer boundary is included in final phase stats`() = runTest {
        val loadedProbeStarted = CompletableDeferred<Unit>()
        val allowTransferToFinish = CompletableDeferred<Unit>()
        var httpCalls = 0
        val engine = object : TransferEngine {
            override suspend fun connectRtt(host: String, port: Int): Long? = null
            override suspend fun httpRtt(url: String): HttpRtt {
                httpCalls++
                if (httpCalls == 4) loadedProbeStarted.complete(Unit)
                return HttpRtt(totalMs = if (httpCalls == 4) 23 else 20, serverMs = null)
            }
            override suspend fun serverInfo(url: String): ServerInfo? = null
            override fun download(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> = flow {
                delay(250)
                loadedProbeStarted.await()
                allowTransferToFinish.await()
                emit(ChunkEvent(0, 128, 250))
            }
            override fun upload(url: String, streams: Int, durationMs: Long): Flow<ChunkEvent> = emptyFlow()
        }
        val events = mutableListOf<SpeedTestEvent>()
        SpeedTestRepositoryImpl(
            engine,
            SpeedTestConfig(latencyProbes = 1, loadedLatencyIntervalMs = 250)
        ).runSpeedTest().collect { event ->
            events += event
            if (event is SpeedTestEvent.LoadedLatencySample && event.phase == SpeedTestPhase.DOWNLOAD) {
                allowTransferToFinish.complete(Unit)
            }
        }

        val emittedRtts = events.filterIsInstance<SpeedTestEvent.LoadedLatencySample>()
            .filter { it.phase == SpeedTestPhase.DOWNLOAD }.map { it.rttMs }
        val finishedStats = events.filterIsInstance<SpeedTestEvent.LoadedLatencyFinished>()
            .single { it.phase == SpeedTestPhase.DOWNLOAD }.stats
        assertEquals(listOf(23L), emittedRtts)
        assertEquals(emittedRtts, finishedStats.samples.map { it.rtTimeMs })
    }

    @Test
    fun `metadata failure is best effort and does not fail the test`() = runTest {
        val events = SpeedTestRepositoryImpl(
            FakeEngine().apply { infoFails = true }, SpeedTestConfig(latencyProbes = 1)
        ).runSpeedTest().toList()
        assertFalse(events.any { it is SpeedTestEvent.ServerInfoReceived })
        assertFalse(events.any { it is SpeedTestEvent.Failed })
        assertTrue(events.any { it is SpeedTestEvent.UploadFinished })
    }

    @Test
    fun `cancellation during download cancels transfer collector and loaded probes`() = runTest {
        val engine = FakeEngine().apply { suspendDownload = true }
        val job = async { SpeedTestRepositoryImpl(engine, SpeedTestConfig(latencyProbes = 1)).runSpeedTest().toList() }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        job.cancelAndJoin()
        assertTrue(engine.cancelledDownload)
    }
}
