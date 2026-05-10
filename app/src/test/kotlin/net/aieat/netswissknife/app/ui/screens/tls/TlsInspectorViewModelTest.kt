package net.aieat.netswissknife.app.ui.screens.tls

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
import net.aieat.netswissknife.core.domain.TlsInspectorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.tls.TlsCertificate
import net.aieat.netswissknife.core.network.tls.TlsInspectorResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Nested
    @DisplayName("inspect state transitions")
    inner class InspectStateTransitions {

        @Test
        fun `success sets result and clears error`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onHostChange("example.com")
            viewModel.inspect()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertNull(state.error)
            assertTrue(!state.isLoading)
        }

        @Test
        fun `error sets error message and clears result`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Error("connection refused")
            viewModel.onHostChange("badhost")
            viewModel.inspect()
            val state = viewModel.uiState.value
            assertNull(state.result)
            assertEquals("connection refused", state.error)
        }

        @Test
        fun `isLoading is false after completion`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
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
        coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
        viewModel.onHostChange("example.com")
        viewModel.inspect()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TLS_HOSTS, "example.com") }
    }
}
