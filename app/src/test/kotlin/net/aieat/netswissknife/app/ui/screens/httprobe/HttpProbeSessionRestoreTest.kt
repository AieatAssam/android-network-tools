package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("HttpProbeViewModel URL, method, redirect flag and result kept across leaving and returning")
class HttpProbeSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: HttpProbeUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<HttpProbeViewModel>()

    private val url = "https://example.com/api"
    private val secretHeaderValue = "Bearer secret-xyz"
    private val secretBody = "secret-body"

    /** A result whose request carries a token-like header and a body, as a real send does. */
    private val resultWithSecrets =
        HttpProbeResult(
            request =
                HttpProbeRequest(
                    url = url,
                    method = HttpMethod.POST,
                    headers = listOf("Authorization" to secretHeaderValue),
                    body = secretBody,
                ),
            statusCode = 200,
            statusMessage = "OK",
            responseTimeMs = 5L,
            responseHeaders = mapOf("Content-Type" to listOf("text/plain")),
            responseBody = "ok",
            responseBodyBytes = 2L,
            finalUrl = url,
            redirectChain = emptyList(),
            securityChecks = emptyList(),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        memory = ToolSessionMemory()
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
            Dispatchers.resetMain()
        }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): HttpProbeViewModel {
        val viewModel =
            HttpProbeViewModel(
                useCase = useCase,
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: HttpProbeViewModel) {
        ViewModelStore().apply {
            put("http", viewModel)
            clear()
        }
    }

    @Test
    fun `the URL, method, redirect flag and finished result are shown again after returning`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(resultWithSecrets)
            val first = newViewModel()
            first.onUrlChange(url)
            first.onMethodChange(HttpMethod.POST)
            first.onFollowRedirectsToggle()
            first.send()
            assertTrue(first.uiState.value.result != null)
            leave(first)

            val returned = newViewModel()

            assertEquals(url, returned.uiState.value.url)
            assertEquals(HttpMethod.POST, returned.uiState.value.method)
            assertFalse(returned.uiState.value.followRedirects)
            val restored = returned.uiState.value
            assertEquals(200, restored.result?.statusCode)
        }

    @Test
    fun `request headers, body and their values are never kept in memory or shown again`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(resultWithSecrets)
            val first = newViewModel()
            first.onUrlChange(url)
            first.onMethodChange(HttpMethod.POST)
            first.onBodyChange(secretBody)
            first.addHeader()
            first.updateHeaderKey(0, "Authorization")
            first.updateHeaderValue(0, secretHeaderValue)
            first.send()
            leave(first)

            val returned = newViewModel()

            val restoredResult = returned.uiState.value.result
            val restoredRequest = restoredResult?.request
            assertTrue(restoredRequest?.headers.orEmpty().isEmpty())
            assertNull(restoredRequest?.body)
            val restoredState = returned.uiState.value
            assertTrue(restoredState.customHeaders.isEmpty())
            assertEquals("", returned.uiState.value.body)
            val kept = memory.slots.values.toString()
            assertFalse(kept.contains("secret-xyz"), "a header value reached the session memory")
            assertFalse(kept.contains(secretBody), "a request body reached the session memory")
        }

    @Test
    fun `a request still running when the tool is left is not restored as running`() =
        runTest {
            coEvery { useCase(any(), any()) } coAnswers { awaitCancellation() }
            val first = newViewModel()
            first.onUrlChange(url)
            first.send()
            assertTrue(first.uiState.value.isLoading)
            leave(first)

            val returned = newViewModel()

            assertFalse(returned.uiState.value.isLoading)
            assertNull(returned.uiState.value.result)
            assertEquals(url, returned.uiState.value.url)
        }
}
