package net.aieat.netswissknife.app.ui.screens.httprobe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("HttpProbeViewModel")
class HttpProbeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: HttpProbeUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: HttpProbeViewModel

    private val stubRequest = HttpProbeRequest(url = "https://example.com")
    private val stubResult = HttpProbeResult(
        request = stubRequest,
        statusCode = 200,
        statusMessage = "OK",
        responseTimeMs = 50L,
        responseHeaders = mapOf("Content-Type" to listOf("text/html")),
        responseBody = "<html></html>",
        responseBodyBytes = 15L,
        finalUrl = "https://example.com",
        redirectChain = emptyList(),
        securityChecks = emptyList()
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = HttpProbeViewModel(useCase, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty url, GET method, no result`() {
        val state = viewModel.uiState.value
        assertEquals("", state.url)
        assertEquals(HttpMethod.GET, state.method)
        assertNull(state.result)
        assertFalse(state.isLoading)
    }

    @Nested
    @DisplayName("send state transitions")
    inner class SendStateTransitions {

        @Test
        fun `success sets result and resets loading`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onUrlChange("https://example.com")
            viewModel.send()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

        @Test
        fun `error sets error message`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Error("timeout")
            viewModel.onUrlChange("https://example.com")
            viewModel.send()
            val state = viewModel.uiState.value
            assertNull(state.result)
            assertEquals("timeout", state.error)
        }

        @Test
        fun `blank url does not trigger send`() = runTest {
            viewModel.onUrlChange("  ")
            viewModel.send()
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `double send while loading is ignored`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onUrlChange("https://example.com")
            viewModel.send()
            val firstResult = viewModel.uiState.value.result
            viewModel.send()
            assertEquals(firstResult, viewModel.uiState.value.result)
        }
    }

    @Nested
    @DisplayName("form field actions")
    inner class FormFieldActions {

        @Test
        fun `onMethodChange updates method`() {
            viewModel.onMethodChange(HttpMethod.POST)
            assertEquals(HttpMethod.POST, viewModel.uiState.value.method)
        }

        @Test
        fun `onFollowRedirectsToggle toggles value`() {
            assertTrue(viewModel.uiState.value.followRedirects)
            viewModel.onFollowRedirectsToggle()
            assertFalse(viewModel.uiState.value.followRedirects)
        }

        @Test
        fun `addHeader adds empty entry`() {
            viewModel.addHeader()
            assertEquals(1, viewModel.uiState.value.customHeaders.size)
        }

        @Test
        fun `removeHeader removes entry at index`() {
            viewModel.addHeader()
            viewModel.addHeader()
            viewModel.removeHeader(0)
            assertEquals(1, viewModel.uiState.value.customHeaders.size)
        }

        @Test
        fun `onTabSelected updates selectedTab`() {
            viewModel.onTabSelected(2)
            assertEquals(2, viewModel.uiState.value.selectedTab)
        }
    }

    @Test
    fun `addRecent is called on send`() = runTest {
        coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
        viewModel.onUrlChange("https://example.com")
        viewModel.send()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_HTTP_HOSTS, "https://example.com") }
    }
}
