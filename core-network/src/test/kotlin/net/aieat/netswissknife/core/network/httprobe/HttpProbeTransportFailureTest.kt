package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngine
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineCall
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineRequest
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException

@DisplayName("HTTP Probe transport failures")
class HttpProbeTransportFailureTest {
    private class ThrowingEngine(
        private val failure: IOException,
    ) : HttpEngine {
        override fun newCall(request: HttpEngineRequest): HttpEngineCall =
            object : HttpEngineCall {
                override suspend fun execute(): HttpEngineResponse = throw failure

                override fun close() = Unit
            }
    }

    private suspend fun codeFor(failure: IOException): ErrorCode? {
        val result =
            HttpProbeRepositoryImpl(ThrowingEngine(failure))
                .probe(HttpProbeRequest(url = "https://example.test/"))
        return (result as? NetworkResult.Error)?.info?.code
    }

    @Test
    fun `unresolvable host is reported as a DNS failure`() =
        runTest {
            assertEquals(ErrorCode.DNS_LOOKUP_FAILED, codeFor(UnknownHostException("nonexistent-host.invalid")))
        }

    @Test
    fun `request timeout is reported as a timeout not a generic failure`() =
        runTest {
            assertEquals(ErrorCode.NETWORK_TIMEOUT, codeFor(InterruptedIOException("timeout")))
        }

    @Test
    fun `refused connection is reported as connection refused`() =
        runTest {
            assertEquals(ErrorCode.NETWORK_CONNECTION_REFUSED, codeFor(ConnectException("Connection refused")))
        }

    @Test
    fun `refused connection wrapped by the transport is still classified`() =
        runTest {
            val wrapped =
                IOException("Failed to connect to example.test/203.0.113.7:443").apply {
                    initCause(ConnectException("Connection refused"))
                }
            assertEquals(ErrorCode.NETWORK_CONNECTION_REFUSED, codeFor(wrapped))
        }

    @Test
    fun `other transport failures keep the generic code`() =
        runTest {
            assertEquals(ErrorCode.NETWORK_REQUEST_FAILED, codeFor(IOException("unexpected EOF")))
        }
}
