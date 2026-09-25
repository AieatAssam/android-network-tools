package net.aieat.netswissknife.core.network.speedtest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

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
    fun `repository shares caller concurrency across configured transfer streams and loaded RTT`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val declaredLimit = 3
            val activeCalls = AtomicInteger()
            val maxActive = AtomicInteger()
            val firstTransferBatch = CountDownLatch(declaredLimit)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val requestUrl = request.url.toString()
                    if (requestUrl.contains("bytes=25000000") || request.url.encodedPath == "/__up") {
                        firstTransferBatch.countDown()
                        if (firstTransferBatch.count > 0) {
                            firstTransferBatch.await(1, TimeUnit.SECONDS)
                        }
                    }
                    Thread.sleep(5)
                    val body = if (request.url.encodedPath == "/meta") "{\"colo\":\"LHR\"}" else "payload"
                    return MockResponse.Builder()
                        .code(200)
                        .addHeader("Server-Timing", "cfRequestDuration;dur=0")
                        .body(body)
                        .build()
                }
            }

            val observedDownloadStreams = Collections.synchronizedSet(mutableSetOf<Int>())
            val observedUploadStreams = Collections.synchronizedSet(mutableSetOf<Int>())
            val observedClient = OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .eventListenerFactory {
                    object : EventListener() {
                        override fun callStart(call: Call) {
                            val inFlight = activeCalls.incrementAndGet()
                            maxActive.updateAndGet { maxOf(it, inFlight) }
                        }

                        override fun callEnd(call: Call) {
                            activeCalls.decrementAndGet()
                        }

                        override fun callFailed(call: Call, ioe: java.io.IOException) {
                            activeCalls.decrementAndGet()
                        }
                    }
                }
                .build()
            val delegate = OkHttpTransferEngine(observedClient)
            val observingEngine = object : TransferEngine {
                override suspend fun connectRtt(host: String, port: Int) = delegate.connectRtt(host, port)
                override suspend fun connectRtt(host: String, port: Int, operationSession: OperationSession) =
                    delegate.connectRtt(host, port, operationSession)
                override suspend fun httpRtt(url: String) = delegate.httpRtt(url)
                override suspend fun serverInfo(url: String) = delegate.serverInfo(url)
                override fun download(url: String, streams: Int, durationMs: Long) = delegate.download(url, streams, durationMs)
                override fun upload(url: String, streams: Int, durationMs: Long) = delegate.upload(url, streams, durationMs)
                override fun download(url: String, streams: Int, durationMs: Long, operationSession: OperationSession) =
                    delegate.download(url, streams, durationMs, operationSession).onEach { observedDownloadStreams += it.streamIndex }
                override fun upload(url: String, streams: Int, durationMs: Long, operationSession: OperationSession) =
                    delegate.upload(url, streams, durationMs, operationSession).onEach { observedUploadStreams += it.streamIndex }
            }
            val config = SpeedTestConfig(
                latencyProbes = 1,
                downloadStreams = 4,
                uploadStreams = 2,
                phaseDurationMs = 1_000,
                sampleIntervalMs = 50,
                loadedLatencyIntervalMs = 50,
            )
            val session = OperationSession(
                OperationBudget.start(
                    requirement = OperationRequirement.ANY_NETWORK,
                    timeoutMillis = 15_000,
                    maxConcurrentProbes = declaredLimit,
                )
            )

            val events = SpeedTestRepositoryImpl(
                observingEngine,
                config,
                server.url("/").toString().trimEnd('/')
            ).runSpeedTest(session).toList()

            assertEquals(declaredLimit, maxActive.get(), "the server should observe the declared request limit in use")
            assertTrue(maxActive.get() <= session.budget.maxConcurrentProbes)
            assertEquals((0 until config.downloadStreams).toSet(), observedDownloadStreams)
            assertEquals((0 until config.uploadStreams).toSet(), observedUploadStreams)
            assertTrue(events.none { it is SpeedTestEvent.Failed }, "speed test failed: ${events.filterIsInstance<SpeedTestEvent.Failed>()}")
        } finally {
            server.close()
        }
    }

    @Test
    fun `loaded RTT overlaps transfers while sharing the caller session capacity`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val activeCalls = AtomicInteger()
            val activeTransferCalls = AtomicInteger()
            val maxActive = AtomicInteger()
            val loadedRttOverlaps = AtomicInteger()
            val firstDownloadBatch = CountDownLatch(2)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val transfer = request.url.encodedPath == "/__up" ||
                        request.url.toString().contains("bytes=25000000")
                    if (transfer && request.url.encodedPath != "/__up") {
                        firstDownloadBatch.countDown()
                        if (firstDownloadBatch.count > 0) firstDownloadBatch.await(1, TimeUnit.SECONDS)
                    }
                    val response = MockResponse.Builder()
                        .code(200)
                        .addHeader("Server-Timing", "cfRequestDuration;dur=0")
                    if (transfer) response.body("payload").throttleBody(1, 250, TimeUnit.MILLISECONDS)
                    else response.body("payload")
                    return response.build()
                }
            }

            val client = OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .eventListenerFactory {
                    object : EventListener() {
                        private fun isTransfer(call: Call): Boolean =
                            call.request().url.encodedPath == "/__up" ||
                                call.request().url.encodedQuery?.contains("bytes=25000000") == true

                        private fun isLatency(call: Call): Boolean =
                            call.request().url.encodedPath == "/__down" &&
                                call.request().url.encodedQuery == "bytes=0"

                        private fun finish(call: Call) {
                            activeCalls.decrementAndGet()
                            if (isTransfer(call)) activeTransferCalls.decrementAndGet()
                        }

                        override fun callStart(call: Call) {
                            val inFlight = activeCalls.incrementAndGet()
                            maxActive.updateAndGet { maxOf(it, inFlight) }
                            if (isTransfer(call)) activeTransferCalls.incrementAndGet()
                            else if (isLatency(call) && activeTransferCalls.get() > 0) {
                                loadedRttOverlaps.incrementAndGet()
                            }
                        }

                        override fun callEnd(call: Call) = finish(call)
                        override fun callFailed(call: Call, ioe: java.io.IOException) = finish(call)
                    }
                }
                .build()
            val delegate = OkHttpTransferEngine(client)
            val observedDownloadStreams = Collections.synchronizedSet(mutableSetOf<Int>())
            val observedUploadStreams = Collections.synchronizedSet(mutableSetOf<Int>())
            val engine = object : TransferEngine {
                override suspend fun connectRtt(host: String, port: Int) = delegate.connectRtt(host, port)
                override suspend fun connectRtt(host: String, port: Int, operationSession: OperationSession) =
                    delegate.connectRtt(host, port, operationSession)
                override suspend fun httpRtt(url: String) = delegate.httpRtt(url)
                override suspend fun serverInfo(url: String) = delegate.serverInfo(url)
                override fun download(url: String, streams: Int, durationMs: Long) = delegate.download(url, streams, durationMs)
                override fun upload(url: String, streams: Int, durationMs: Long) = delegate.upload(url, streams, durationMs)
                override fun download(url: String, streams: Int, durationMs: Long, operationSession: OperationSession) =
                    delegate.download(url, streams, durationMs, operationSession).onEach { observedDownloadStreams += it.streamIndex }
                override fun upload(url: String, streams: Int, durationMs: Long, operationSession: OperationSession) =
                    delegate.upload(url, streams, durationMs, operationSession).onEach { observedUploadStreams += it.streamIndex }
            }
            val config = SpeedTestConfig(
                latencyProbes = 1,
                downloadStreams = 2,
                uploadStreams = 2,
                phaseDurationMs = 1_000,
                sampleIntervalMs = 50,
                loadedLatencyIntervalMs = 50,
            )
            val session = SpeedTestOperation.newSession(config)

            val events = SpeedTestRepositoryImpl(
                engine,
                config,
                server.url("/").toString().trimEnd('/')
            ).runSpeedTest(session).toList()

            assertEquals(3, session.budget.maxConcurrentProbes)
            assertEquals(3, maxActive.get(), "two transfer calls and one loaded RTT should overlap")
            assertTrue(maxActive.get() <= session.budget.maxConcurrentProbes)
            assertTrue(loadedRttOverlaps.get() > 0, "loaded RTT should start while a transfer response is active")
            assertTrue(events.any { it is SpeedTestEvent.LoadedLatencySample })
            assertEquals((0 until config.downloadStreams).toSet(), observedDownloadStreams)
            assertEquals((0 until config.uploadStreams).toSet(), observedUploadStreams)
            assertTrue(events.none { it is SpeedTestEvent.Failed })
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
