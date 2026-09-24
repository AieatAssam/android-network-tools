package net.aieat.netswissknife.app.ui.screens.httprobe

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.domain.HttpProbeParams
import net.aieat.netswissknife.core.domain.validateHttpProbeUrl
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.CrossOriginEntityReplay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
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

    @Test
    fun `typed mDNS HTTP route pre-fills editable URL with provenance and never sends`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.MDNS,
        )
        val encoded = ToolIntentCodec.encode(intent)
        val decoded = ToolIntentCodec.decode(encoded)
        assertEquals(intent, decoded)
        val savedState = SavedStateHandle(mapOf("intent" to encoded, "host" to "printer.local"))

        val handoffVm = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = savedState,
        )

        assertEquals("http://printer.local:8080/", handoffVm.uiState.value.url)
        assertEquals(ToolSource.MDNS, handoffVm.sourceContext)
        assertEquals(true, savedState.get<Boolean>("httpHandoffConsumed"))
        assertEquals("mdns", savedState.get<String>("httpHandoffSource"))
        assertFalse(handoffVm.uiState.value.isLoading)
        assertNull(handoffVm.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `typed LAN HTTP route pre-fills discovered port with provenance and never sends`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("192.0.2.8")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.LAN,
        )
        val handoffVm = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf("intent" to ToolIntentCodec.encode(intent), "host" to "192.0.2.8"),
            ),
        )

        assertEquals("http://192.0.2.8:8080/", handoffVm.uiState.value.url)
        assertEquals(ToolSource.LAN, handoffVm.sourceContext)
        assertFalse(handoffVm.uiState.value.isLoading)
        assertNull(handoffVm.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `IPv6 targets get a valid bracketed editable HTTP URL`() {
        listOf("fe80::1%wlan0", "[fe80::1%wlan0]", "[2001:db8::1]").forEach { host ->
            val target = ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse(host)),
                requireNotNull(ToolPort.parse(8080)),
            )

            val url = httpUrlForTarget(target)

            val expectedHost = if ("%" in host) "fe80::1%25wlan0" else "2001:db8::1"
            assertEquals("http://[$expectedHost]:8080/", url)
            assertNull(validateHttpProbeUrl(url))
        }
    }

    @Test
    fun `edited mDNS URL is saved and takes precedence after recreation`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.MDNS,
        )
        val savedState = SavedStateHandle(
            mapOf("intent" to ToolIntentCodec.encode(intent), "host" to "printer.local"),
        )
        val first = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = savedState)
        first.onUrlChange("http://edited.local:9000/custom")

        assertEquals("http://edited.local:9000/custom", savedState.get<String>("editedHttpUrl"))
        assertEquals(true, savedState.get<Boolean>("httpHandoffConsumed"))
        assertEquals("mdns", savedState.get<String>("httpHandoffSource"))
        val recreated = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to ToolIntentCodec.encode(intent),
                    "host" to "printer.local",
                    "httpHandoffConsumed" to true,
                    "httpHandoffSource" to "mdns",
                    "editedHttpUrl" to "http://edited.local:9000/custom",
                ),
            ),
        )
        assertEquals("http://edited.local:9000/custom", recreated.uiState.value.url)
        assertEquals(ToolSource.MDNS, recreated.sourceContext)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `untouched route prefill and provenance survive recreation`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.MDNS,
        )
        val encodedIntent = ToolIntentCodec.encode(intent)
        val firstState = SavedStateHandle(mapOf("intent" to encodedIntent, "host" to "printer.local"))
        val first = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = firstState)

        val recreated = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to encodedIntent,
                    "host" to "printer.local",
                    "httpHandoffConsumed" to true,
                    "httpHandoffSource" to "mdns",
                    "editedHttpUrl" to "http://printer.local:8080/",
                ),
            ),
        )

        assertEquals("http://printer.local:8080/", first.uiState.value.url)
        assertEquals("http://printer.local:8080/", recreated.uiState.value.url)
        assertEquals(ToolSource.MDNS, recreated.sourceContext)
        assertFalse(recreated.uiState.value.isLoading)
        assertNull(recreated.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `clearing a handoff removes provenance and preserves intentional blank after recreation`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.MDNS,
        )
        val encodedIntent = ToolIntentCodec.encode(intent)
        val savedState = SavedStateHandle(mapOf("intent" to encodedIntent, "host" to "printer.local"))
        val first = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = savedState)

        first.clearPrefill()

        assertEquals("", first.uiState.value.url)
        assertNull(first.sourceContext)
        assertNull(first.sourceContextState.value)
        assertEquals(true, savedState.get<Boolean>("httpHandoffConsumed"))
        assertEquals("", savedState.get<String>("editedHttpUrl"))
        assertNull(savedState.get<String>("httpHandoffSource"))

        val recreated = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to encodedIntent,
                    "host" to "printer.local",
                    "httpHandoffConsumed" to true,
                    "editedHttpUrl" to "",
                ),
            ),
        )
        assertEquals("", recreated.uiState.value.url)
        assertNull(recreated.sourceContext)
        assertFalse(recreated.hasInvalidHandoff.value)
        assertFalse(recreated.uiState.value.isLoading)
        assertNull(recreated.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `clear prefill is ignored while a request is active`() = runTest {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.LAN,
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { useCase(any(), any()) } coAnswers {
            started.complete(Unit)
            release.await()
            NetworkResult.Success(stubResult)
        }
        val handoffVm = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to ToolIntentCodec.encode(intent),
                    "host" to "printer.local",
                ),
            ),
        )

        handoffVm.send()
        try {
            started.await()
            assertTrue(handoffVm.uiState.value.isLoading)

            handoffVm.clearPrefill()

            assertEquals("http://printer.local:8080/", handoffVm.uiState.value.url)
            assertEquals(ToolSource.LAN, handoffVm.sourceContext)
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun `invalid typed HTTP routes are suppressed and do not send`() {
        val valid = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.HTTP,
                requireNotNull(ToolHost.parse("printer.local")),
                requireNotNull(ToolPort.parse(8080)),
            ),
            ToolSource.MDNS,
        )
        val wrongTool = ToolIntent(
            ToolDestination.HostTarget(HostTool.PING, requireNotNull(ToolHost.parse("printer.local"))),
            ToolSource.MDNS,
        )
        listOf(
            mapOf("intent" to "ti1.invalid", "host" to "printer.local"),
            mapOf("intent" to ToolIntentCodec.encode(wrongTool), "host" to "printer.local"),
            mapOf("intent" to ToolIntentCodec.encode(valid), "host" to "other.local"),
            mapOf("intent" to ToolIntentCodec.encode(valid)),
        ).forEach { args ->
            val invalidVm = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = SavedStateHandle(args))
            assertEquals("", invalidVm.uiState.value.url)
            assertNull(invalidVm.sourceContext)
            assertTrue(invalidVm.hasInvalidHandoff.value)
        }

        val recoveringState = SavedStateHandle(mapOf("intent" to "ti1.invalid", "host" to "printer.local"))
        val recovering = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = recoveringState)
        recovering.onUrlChange("http://replacement.local/")
        assertFalse(recovering.hasInvalidHandoff.value)
        assertEquals(true, recoveringState.get<Boolean>("handoffRecovered"))

        val restored = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to "ti1.invalid",
                    "host" to "printer.local",
                    "editedHttpUrl" to "http://replacement.local/",
                    "handoffRecovered" to true,
                ),
            ),
        )
        assertFalse(restored.hasInvalidHandoff.value)
        assertEquals("http://replacement.local/", restored.uiState.value.url)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `invalid unconsumed route drops stale source provenance`() {
        val savedState = SavedStateHandle(
            mapOf(
                "intent" to "ti1.invalid",
                "host" to "printer.local",
                "httpHandoffSource" to "mdns",
            ),
        )

        val invalidVm = HttpProbeViewModel(useCase, recentHostsRepository, savedStateHandle = savedState)

        assertTrue(invalidVm.hasInvalidHandoff.value)
        assertNull(invalidVm.sourceContext)
        assertNull(savedState.get<String>("httpHandoffSource"))

        val recreated = HttpProbeViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to "ti1.invalid",
                    "host" to "printer.local",
                    "httpHandoffConsumed" to true,
                    "httpHandoffSource" to "mdns",
                    "editedHttpUrl" to "",
                ),
            ),
        )
        assertNull(recreated.sourceContext)
        assertTrue(recreated.hasInvalidHandoff.value)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Nested
    @DisplayName("send state transitions")
    inner class SendStateTransitions {

        @Test
        fun `success sets result and resets loading`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
            viewModel.onUrlChange("https://example.com")
            viewModel.send()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

        @Test
        fun `error sets error message`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Error("timeout")
            viewModel.onUrlChange("https://example.com")
            viewModel.send()
            val state = viewModel.uiState.value
            assertNull(state.result)
            assertEquals("timeout", state.error)
        }

        @Test
        fun `exception sets error and stops loading`() = runTest {
            coEvery { useCase(any(), any()) } throws IllegalStateException("connection reset")
            viewModel.onUrlChange("https://example.com")

            viewModel.send()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("Request failed: connection reset", state.error)
        }

        @Test
        fun `blank url does not trigger send`() = runTest {
            viewModel.onUrlChange("  ")
            viewModel.send()
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `double send while loading is ignored`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
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
    fun `stores only safe origin on send even when request fails`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Error("timeout")
        viewModel.onUrlChange("https://user:secret@example.com/private?token=sensitive#section")
        viewModel.send()
        coVerify {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_HTTP_HOSTS, "https://example.com")
        }
        assertEquals("timeout", viewModel.uiState.value.error)
    }

    @Test
    fun `invalid URL is not stored in recents`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Error("Only HTTP and HTTPS URLs are supported")
        viewModel.onUrlChange("ftp://user:secret@example.com/private?token=sensitive")

        viewModel.send()

        coVerify(exactly = 0) {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_HTTP_HOSTS, any())
        }
    }

    @Test
    fun `legacy unsafe recents are sanitized before they reach UI state`() {
        every { recentHostsRepository.getRecents(AppPreferenceKeys.RECENT_HTTP_HOSTS) } returns flowOf(
            listOf(
                "https://user:secret@example.com/private?token=sensitive",
                "not a URL"
            )
        )

        viewModel = HttpProbeViewModel(useCase, recentHostsRepository)

        assertEquals(listOf("https://example.com"), viewModel.recentHosts.value)
        coVerify { recentHostsRepository.sanitizeRecents(AppPreferenceKeys.RECENT_HTTP_HOSTS, any()) }
    }

    @Test
    fun `stale approval token cannot approve the next redirect in the same run`() = runTest {
        coEvery { useCase(any(), any()) } coAnswers {
            val request = firstArg<HttpProbeParams>()
            val requestApproval = requireNotNull(request.approveCrossOriginEntityReplay)
            assertTrue(
                requestApproval(
                    CrossOriginEntityReplay(
                        destinationUrl = "https://first.example/path",
                        method = HttpMethod.POST,
                        statusCode = 307
                    )
                )
            )
            assertTrue(
                requestApproval(
                    CrossOriginEntityReplay(
                        destinationUrl = "https://second.example/path",
                        method = HttpMethod.POST,
                        statusCode = 307
                    )
                )
            )
            NetworkResult.Success(stubResult)
        }
        viewModel.onUrlChange("https://source.example/start")
        viewModel.onMethodChange(HttpMethod.POST)
        viewModel.onBodyChange("payload")

        viewModel.send()
        val hopA = requireNotNull(viewModel.uiState.value.pendingEntityReplayApproval)
        assertEquals("https://first.example/path", hopA.destinationUrl)

        viewModel.respondToEntityReplayApproval(hopA.runId, hopA.approvalId, approved = true)
        val hopB = requireNotNull(viewModel.uiState.value.pendingEntityReplayApproval)
        assertEquals(hopA.runId, hopB.runId)
        assertNotEquals(hopA.approvalId, hopB.approvalId)
        assertEquals("https://second.example/path", hopB.destinationUrl)

        viewModel.respondToEntityReplayApproval(hopA.runId, hopA.approvalId, approved = true)
        assertEquals(hopB, viewModel.uiState.value.pendingEntityReplayApproval)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.respondToEntityReplayApproval(hopB.runId, hopB.approvalId, approved = true)
        assertNull(viewModel.uiState.value.pendingEntityReplayApproval)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(stubResult, viewModel.uiState.value.result)
    }
}
