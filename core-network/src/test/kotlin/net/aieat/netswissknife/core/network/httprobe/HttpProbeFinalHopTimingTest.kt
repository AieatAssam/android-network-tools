package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngine
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineCall
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineRequest
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineResponse
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HttpProbeRepositoryImpl reports phases of the final hop only")
class HttpProbeFinalHopTimingTest {
    /** Serves the scripted responses in order, one per call, so each hop carries its own timings. */
    private class ScriptedEngine(
        private val responses: List<HttpEngineResponse>,
    ) : HttpEngine {
        private var next = 0

        override fun newCall(request: HttpEngineRequest): HttpEngineCall {
            val response = responses[next++]
            return object : HttpEngineCall {
                override suspend fun execute(): HttpEngineResponse = response

                override fun close() = Unit
            }
        }
    }

    private fun redirect(
        location: String,
        timings: HttpTimings,
    ) = HttpEngineResponse(
        statusCode = 302,
        statusMessage = "Found",
        headers = mapOf("Location" to listOf(location)),
        body = null,
        protocol = "http/1.1",
        timings = timings,
    )

    private fun ok(timings: HttpTimings) =
        HttpEngineResponse(
            statusCode = 200,
            statusMessage = "OK",
            headers = mapOf("Content-Type" to listOf("text/plain")),
            body = "ok".byteInputStream(),
            protocol = "http/1.1",
            timings = timings,
        )

    @Test
    fun `phases come from the final hop and total time covers the whole chain`() =
        runTest {
            val firstHop =
                HttpTimings(
                    dnsMs = 40,
                    connectMs = 30,
                    tlsMs = 20,
                    ttfbMs = 90,
                    transferMs = null,
                    totalMs = 100,
                )
            val finalHop = HttpTimings(dnsMs = 3, connectMs = 4, tlsMs = 5, ttfbMs = 12, transferMs = 2, totalMs = 25)
            val engine =
                ScriptedEngine(
                    listOf(
                        redirect("https://example.test/final", firstHop),
                        ok(finalHop),
                    ),
                )

            val result =
                HttpProbeRepositoryImpl(engine).probe(
                    HttpProbeRequest(url = "https://example.test/start", followRedirects = true),
                )

            val probe = (result as NetworkResult.Success).data
            assertEquals(3L, probe.timings.dnsMs)
            assertEquals(4L, probe.timings.connectMs)
            assertEquals(5L, probe.timings.tlsMs)
            assertEquals(12L, probe.timings.ttfbMs)
            assertEquals(2L, probe.timings.transferMs)
            assertEquals(probe.responseTimeMs, probe.timings.totalMs)
        }
}
