package net.aieat.netswissknife.app.ui.screens.tls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.awaitCancellation
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
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
import net.aieat.netswissknife.core.domain.TlsInspectorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
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
@DisplayName("TlsInspectorViewModel")
class TlsInspectorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: TlsInspectorUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: TlsInspectorViewModel

    private val stubCert = TlsCertificate(
        subjectCN = "example.com",
        subjectOrg = null,
        issuerCN = "Let's Encrypt",
        issuerOrg = null,
        notBefore = 0L,
        notAfter = Long.MAX_VALUE,
        isExpired = false,
        isSelfSigned = false,
        sans = listOf("example.com"),
        serialNumber = "abc123",
        signatureAlgorithm = "SHA256withRSA",
        publicKeyAlgorithm = "RSA",
        publicKeyBits = 2048,
        sha256Fingerprint = "AA:BB:CC"
    )

    private val stubResult = TlsInspectorResult(
        host = "example.com",
        port = 443,
        tlsVersion = "TLSv1.3",
        cipherSuite = "TLS_AES_128_GCM_SHA256",
        chain = listOf(stubCert),
        isChainTrusted = true,
        handshakeTimeMs = 50L
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = TlsInspectorViewModel(useCase, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty host and no result`() {
        val state = viewModel.uiState.value
        assertEquals("", state.host)
        assertNull(state.result)
        assertNull(state.error)
    }

    @Test
    fun `typed LAN TLS handoff prefills host and port without inspecting`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.TLS,
                requireNotNull(ToolHost.parse("192.0.2.8")),
                requireNotNull(ToolPort.parse(8443)),
            ),
            ToolSource.LAN,
        )
        val handoff = TlsInspectorViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to ToolIntentCodec.encode(intent),
                    "host" to "192.0.2.8",
                    "port" to "8443",
                ),
            ),
        )

        assertEquals("192.0.2.8", handoff.uiState.value.host)
        assertEquals("8443", handoff.uiState.value.port)
        assertEquals(ToolSource.LAN, handoff.sourceContext)
        assertFalse(handoff.hasInvalidHandoff.value)
        assertFalse(handoff.uiState.value.isLoading)
        assertNull(handoff.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `TLS handoff is consumed once and clear removes host port and source across recreation`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.TLS,
                requireNotNull(ToolHost.parse("192.0.2.8")),
                requireNotNull(ToolPort.parse(8443)),
            ),
            ToolSource.LAN,
        )
        val routeArgs = mapOf(
            "intent" to ToolIntentCodec.encode(intent),
            "host" to "192.0.2.8",
            "port" to "8443",
        )
        val savedState = SavedStateHandle(routeArgs)
        val handoff = TlsInspectorViewModel(useCase, recentHostsRepository, savedStateHandle = savedState)

        assertEquals("192.0.2.8", handoff.uiState.value.host)
        assertEquals("8443", handoff.uiState.value.port)
        assertEquals(true, savedState.get<Boolean>("tlsHandoffConsumed"))
        assertEquals("192.0.2.8", savedState.get<String>("editedTlsHost"))
        assertEquals("8443", savedState.get<String>("editedTlsPort"))

        handoff.clearPrefill()

        assertEquals("", handoff.uiState.value.host)
        assertEquals("443", handoff.uiState.value.port)
        assertNull(handoff.sourceContext)
        assertEquals("", savedState.get<String>("editedTlsHost"))
        assertEquals("443", savedState.get<String>("editedTlsPort"))
        assertNull(savedState.get<String>("tlsHandoffSource"))

        val recreated = TlsInspectorViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(routeArgs + mapOf(
                "editedTlsHost" to "",
                "editedTlsPort" to "443",
                "tlsHandoffConsumed" to true,
            )),
        )
        assertEquals("", recreated.uiState.value.host)
        assertEquals("443", recreated.uiState.value.port)
        assertNull(recreated.sourceContext)
        assertFalse(recreated.uiState.value.isLoading)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `clear prefill is ignored while TLS inspection is active`() = runTest {
        val sessionSlot = slot<OperationSession>()
        val routeArgs = tlsRouteArgs()
        val active = TlsInspectorViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(routeArgs),
        )
        coEvery { useCase(any(), capture(sessionSlot)) } coAnswers { awaitCancellation() }

        active.inspect()
        assertTrue(active.uiState.value.isLoading)
        active.clearPrefill()

        assertEquals("192.0.2.8", active.uiState.value.host)
        assertEquals("8443", active.uiState.value.port)
        assertEquals(ToolSource.LAN, active.sourceContext)
        assertTrue(active.uiState.value.isLoading)
        ViewModelStore().apply { put("tls-active", active) }.clear()
        assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
    }

    @Test
    fun `invalid typed handoff stays editable and valid host port replacement restores`() {
        val validTls = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.TLS,
                requireNotNull(ToolHost.parse("192.0.2.8")),
                requireNotNull(ToolPort.parse(8443)),
            ),
            ToolSource.LAN,
        )
        val wrongTool = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.PING,
                requireNotNull(ToolHost.parse("192.0.2.8")),
            ),
            ToolSource.LAN,
        )
        val invalidArguments = listOf(
            mapOf("intent" to "ti1.invalid", "host" to "192.0.2.8", "port" to "8443"),
            mapOf("intent" to ToolIntentCodec.encode(validTls), "host" to "192.0.2.9", "port" to "8443"),
            mapOf("intent" to ToolIntentCodec.encode(validTls), "host" to "192.0.2.8", "port" to "9443"),
            mapOf("intent" to ToolIntentCodec.encode(wrongTool), "host" to "192.0.2.8", "port" to "8443"),
            mapOf("intent" to ToolIntentCodec.encode(validTls), "host" to "192.0.2.8"),
        )
        invalidArguments.forEach { args ->
            val invalid = TlsInspectorViewModel(
                useCase,
                recentHostsRepository,
                savedStateHandle = SavedStateHandle(args),
            )
            assertTrue(invalid.hasInvalidHandoff.value)
            assertEquals("", invalid.uiState.value.host)
            assertNull(invalid.sourceContext)
        }

        val invalidState = SavedStateHandle(
            mapOf(
                "intent" to "ti1.invalid",
                "host" to "192.0.2.8",
                "port" to "8443",
                "tlsHandoffSource" to "lan",
            ),
        )
        val invalid = TlsInspectorViewModel(useCase, recentHostsRepository, savedStateHandle = invalidState)
        assertNull(invalidState.get<String>("tlsHandoffSource"))

        invalid.onHostChange("192.0.2.9")
        assertFalse(invalid.hasInvalidHandoff.value)
        assertEquals(true, invalidState.get<Boolean>("tlsHandoffRecovered"))
        invalid.onPortChange("9443")

        val recreated = TlsInspectorViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to "ti1.invalid",
                    "host" to "192.0.2.8",
                    "port" to "8443",
                    "editedTlsHost" to "192.0.2.9",
                    "editedTlsPort" to "9443",
                    "tlsHandoffRecovered" to true,
                ),
            ),
        )
        assertFalse(recreated.hasInvalidHandoff.value)
        assertEquals("192.0.2.9", recreated.uiState.value.host)
        assertEquals("9443", recreated.uiState.value.port)

        val staleConsumedSource = TlsInspectorViewModel(
            useCase,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to "ti1.invalid",
                    "host" to "192.0.2.8",
                    "port" to "8443",
                    "tlsHandoffConsumed" to true,
                    "tlsHandoffSource" to "lan",
                    "editedTlsHost" to "",
                    "editedTlsPort" to "443",
                ),
            ),
        )
        assertNull(staleConsumedSource.sourceContext)
        assertTrue(staleConsumedSource.hasInvalidHandoff.value)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Nested
    @DisplayName("inspect state transitions")
    inner class InspectStateTransitions {

        @Test
        fun `success sets result and clears error`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
            viewModel.onHostChange("example.com")
            viewModel.inspect()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertNull(state.error)
            assertTrue(!state.isLoading)
        }

        @Test
        fun `error sets error message and clears result`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Error("connection refused")
            viewModel.onHostChange("badhost")
            viewModel.inspect()
            val state = viewModel.uiState.value
            assertNull(state.result)
            assertEquals("connection refused", state.error)
        }

        @Test
        fun `exception sets actionable error and stops loading`() = runTest {
            coEvery { useCase(any(), any()) } throws IllegalStateException("handshake failed")
            viewModel.onHostChange("example.com")

            viewModel.inspect()

            val state = viewModel.uiState.value
            assertTrue(!state.isLoading)
            assertNull(state.result)
            assertEquals("TLS inspection failed: handshake failed", state.error)
        }

        @Test
        fun `isLoading is false after completion`() = runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
            viewModel.onHostChange("example.com")
            viewModel.inspect()
            assertTrue(!viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `onHostChange updates state and clears error`() {
        viewModel.onHostChange("newhost.com")
        assertEquals("newhost.com", viewModel.uiState.value.host)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `addRecent is called on inspect`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
        viewModel.onHostChange("example.com")
        viewModel.inspect()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, "example.com") }
    }

    @Test
    fun `inspect normalizes host before calling use case and saving`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Error("test")
        viewModel.onHostChange("  EXAMPLE.COM.  ")

        viewModel.inspect()

        assertEquals("example.com", viewModel.uiState.value.host)
        coVerify { useCase(match { it.host == "example.com" }, any()) }
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, "example.com") }
    }

    @Test
    fun `inspect rejects internal whitespace without saving or probing`() = runTest {
        viewModel.onHostChange("bad host")

        viewModel.inspect()

        assertEquals("Invalid hostname or IP address", viewModel.uiState.value.error)
        coVerify(exactly = 0) { useCase(any(), any()) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, any()) }
    }

    @Test
    fun `inspect rejects invalid ports without probing or saving recent host`() = runTest {
        viewModel.onHostChange("example.com")
        viewModel.onPortChange("not-a-port")

        viewModel.inspect()

        assertEquals("Port must be a number from 1 to 65535", viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.result)
        coVerify(exactly = 0) { useCase(any(), any()) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, any()) }
    }

    @Test
    fun `inspect rejects ports outside the valid range without probing`() = runTest {
        viewModel.onHostChange("example.com")
        viewModel.onPortChange("65536")

        viewModel.inspect()

        assertEquals("Port must be a number from 1 to 65535", viewModel.uiState.value.error)
        coVerify(exactly = 0) { useCase(any(), any()) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, any()) }
    }

    @Test
    fun `inspect rejects port zero without probing or saving recent host`() = runTest {
        viewModel.onHostChange("example.com")
        viewModel.onPortChange("0")

        viewModel.inspect()

        assertEquals("Port must be a number from 1 to 65535", viewModel.uiState.value.error)
        coVerify(exactly = 0) { useCase(any(), any()) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, any()) }
    }

    @Test
    fun `inspect forwards a valid custom port unchanged`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
        viewModel.onHostChange("example.com")
        viewModel.onPortChange("8443")

        viewModel.inspect()

        coVerify { useCase(match { it.host == "example.com" && it.port == 8443 }, any()) }
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, "example.com") }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `editing host or port clears the previous result`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
        viewModel.onHostChange("example.com")
        viewModel.inspect()
        assertNotNull(viewModel.uiState.value.result)

        viewModel.onHostChange("other.example")

        assertNull(viewModel.uiState.value.result)
        viewModel.inspect()
        assertNotNull(viewModel.uiState.value.result)

        viewModel.onPortChange("8443")

        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun `clearing view model records lifecycle pause on active session`() = runTest {
        val sessionSlot = slot<OperationSession>()
        coEvery { useCase(any(), capture(sessionSlot)) } coAnswers { awaitCancellation() }
        val store = ViewModelStore()
        store.put("tls-inspector", viewModel)
        viewModel.onHostChange("example.com")

        viewModel.inspect()
        store.clear()

        assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
    }

    private fun tlsRouteArgs(): Map<String, String> {
        val intent = ToolIntent(
            ToolDestination.HostTarget(
                HostTool.TLS,
                requireNotNull(ToolHost.parse("192.0.2.8")),
                requireNotNull(ToolPort.parse(8443)),
            ),
            ToolSource.LAN,
        )
        return mapOf(
            "intent" to ToolIntentCodec.encode(intent),
            "host" to "192.0.2.8",
            "port" to "8443",
        )
    }
}
