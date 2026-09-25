package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OkHttpTransferEngineTest {
    @Test
    fun `HTTP latency records the cf request duration correction header`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Server-Timing", "cache;desc=HIT")
                    .addHeader("Server-Timing", "cfRequestDuration;dur=12.5")
                    .body("")
                    .build()
            )
            val rtt = OkHttpTransferEngine().httpRtt(server.url("/__down?bytes=0").toString())
            assertEquals(12.5, rtt.serverMs)
            assertTrue(rtt.totalMs >= rtt.correctedMs)
        } finally {
            server.close()
        }
    }

    @Test
    fun `parallel download starts one request for each configured stream`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val streams = 4
            val arrivals = CountDownLatch(streams)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    arrivals.countDown()
                    val allStreamsStarted = arrivals.await(2, TimeUnit.SECONDS)
                    return MockResponse.Builder()
                        .code(if (allStreamsStarted) 200 else 503)
                        .body("payload")
                        .build()
                }
            }
            val engine = OkHttpTransferEngine(
                OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
            )
            val chunks = engine.download(server.url("/download").toString(), streams, durationMs = 100).toList()
            assertEquals((0 until streams).toSet(), chunks.map { it.streamIndex }.toSet())
            assertTrue(chunks.sumOf { it.bytes } >= streams * 7L)
            assertTrue(server.requestCount >= streams)
        } finally {
            server.close()
        }
    }

    @Test
    fun `server timing parser reads decimal cfRequestDuration from combined values`() {
        assertEquals(12.5, OkHttpTransferEngine.parseServerTimingMs("cache;desc=HIT, cfRequestDuration;dur=12.5"))
        assertEquals(12.5, OkHttpTransferEngine.parseServerTimingMs("cfRequestDuration;desc=\"cache, origin\";dur=12.5"))
        assertEquals(12.5, OkHttpTransferEngine.parseServerTimingMs("cfRequestDuration;desc=\"cache, \\\"warm\\\" origin\";dur=12.5"))
        assertEquals(null, OkHttpTransferEngine.parseServerTimingMs("origin;dur=12.5"))
    }

    @Test
    fun `upload response draining is capped instead of materializing the full body`() {
        val input = ByteArrayInputStream(ByteArray(2_000_000))
        assertEquals(16 * 1024, OkHttpTransferEngine.drainUploadResponse(input))
        assertEquals(2_000_000 - 16 * 1024, input.available())
    }

    @Test
    fun `chunked oversized server metadata is capped and repository continues measuring`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .chunkedBody("x".repeat(OkHttpTransferEngine.MAX_SERVER_INFO_BYTES * 4), 1024)
                    .build()
            )
            val httpEngine = OkHttpTransferEngine()
            val engine = object : TransferEngine {
                override suspend fun connectRtt(host: String, port: Int): Long? = null
                override suspend fun httpRtt(url: String) = HttpRtt(totalMs = 11, serverMs = null)
                override suspend fun serverInfo(url: String) = httpEngine.serverInfo(url)
                override fun download(url: String, streams: Int, durationMs: Long) =
                    flowOf(ChunkEvent(0, 1_000, 200))
                override fun upload(url: String, streams: Int, durationMs: Long) = emptyFlow<ChunkEvent>()
            }
            val repo = SpeedTestRepositoryImpl(
                engine,
                SpeedTestConfig(latencyProbes = 1),
                server.url("/").toString().trimEnd('/')
            )

            val events = repo.runSpeedTest().toList()

            assertEquals(1, server.requestCount)
            assertTrue(events.none { it is SpeedTestEvent.ServerInfoReceived })
            assertTrue(events.any { it is SpeedTestEvent.LatencyFinished })
            assertTrue(events.any { it is SpeedTestEvent.DownloadFinished })
            assertTrue(events.any { it is SpeedTestEvent.UploadFinished })
            assertTrue(events.none { it is SpeedTestEvent.Failed })
        } finally {
            server.close()
        }
    }

    @Test
    fun `bounded server info reader consumes only limit plus one byte for oversized unknown length body`() {
        var consumed = 0
        val input = object : FilterInputStream(ByteArrayInputStream(ByteArray(1_000_000))) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) consumed += it }
        }

        assertEquals(null, OkHttpTransferEngine.readBoundedServerInfo(input))
        assertEquals(OkHttpTransferEngine.MAX_SERVER_INFO_BYTES + 1, consumed)
    }
}
