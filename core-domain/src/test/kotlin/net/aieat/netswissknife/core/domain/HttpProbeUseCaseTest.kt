package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepository
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HttpProbeUseCase – validation")
class HttpProbeUseCaseTest {

    private lateinit var repository: HttpProbeRepository
    private lateinit var useCase: HttpProbeUseCase

    private val fakeResult = HttpProbeResult(
        request = HttpProbeRequest(url = "https://example.com"),
        statusCode = 200,
        statusMessage = "OK",
        responseTimeMs = 123L,
        responseHeaders = emptyMap(),
        responseBody = "Hello",
        responseBodyBytes = 5L,
        finalUrl = "https://example.com",
        redirectChain = emptyList(),
        securityChecks = emptyList()
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = HttpProbeUseCase(repository)
    }

    @Test
    @DisplayName("invoke returns Error for blank URL")
    fun `invoke returns Error for blank URL`() = runTest {
        val result = useCase(HttpProbeParams(url = "  "))
        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("blank", ignoreCase = true))
    }

    @Test
    @DisplayName("invoke returns Error for non-HTTP URL")
    fun `invoke returns Error for non-HTTP URL`() = runTest {
        val result = useCase(HttpProbeParams(url = "ftp://example.com"))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("invoke returns Error for timeout below 500ms")
    fun `invoke returns Error for timeout below 500ms`() = runTest {
        val result = useCase(HttpProbeParams(url = "https://example.com", timeoutMs = 499))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("invoke returns Error for timeout above 60000ms")
    fun `invoke returns Error for timeout above 60000ms`() = runTest {
        val result = useCase(HttpProbeParams(url = "https://example.com", timeoutMs = 60_001))
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("invoke delegates to repository for valid params")
    fun `invoke delegates to repository for valid params`() = runTest {
        coEvery { repository.probe(any()) } returns NetworkResult.Success(fakeResult)

        val result = useCase(HttpProbeParams(url = "https://example.com"))

        assertTrue(result is NetworkResult.Success)
        coVerify(exactly = 1) { repository.probe(any()) }
    }

    @Test
    @DisplayName("invoke strips body for GET requests")
    fun `invoke strips body for GET requests`() = runTest {
        coEvery { repository.probe(any()) } returns NetworkResult.Success(fakeResult)

        useCase(HttpProbeParams(url = "https://example.com", method = HttpMethod.GET, body = "should be stripped"))

        coVerify { repository.probe(match { it.body == null }) }
    }

    @Test
    @DisplayName("invoke preserves body for POST requests")
    fun `invoke preserves body for POST requests`() = runTest {
        coEvery { repository.probe(any()) } returns NetworkResult.Success(fakeResult)

        useCase(HttpProbeParams(url = "https://example.com", method = HttpMethod.POST, body = "payload"))

        coVerify { repository.probe(match { it.body == "payload" }) }
    }

    @Test
    @DisplayName("invoke passes http URL to repository")
    fun `invoke passes http URL to repository`() = runTest {
        coEvery { repository.probe(any()) } returns NetworkResult.Success(fakeResult)

        useCase(HttpProbeParams(url = "http://example.com"))

        coVerify { repository.probe(match { it.url == "http://example.com" }) }
    }

    @Test
    @DisplayName("invoke returns repository error unchanged")
    fun `invoke returns repository error unchanged`() = runTest {
        coEvery { repository.probe(any()) } returns NetworkResult.Error("Connection refused")

        val result = useCase(HttpProbeParams(url = "https://example.com"))

        assertTrue(result is NetworkResult.Error)
        assertEquals("Connection refused", (result as NetworkResult.Error).message)
    }
}
