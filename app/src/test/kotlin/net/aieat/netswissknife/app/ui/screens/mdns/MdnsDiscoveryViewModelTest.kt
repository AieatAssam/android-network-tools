package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("MdnsDiscoveryViewModel")
class MdnsDiscoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: MdnsDiscoveryUseCase
    private lateinit var viewModel: MdnsDiscoveryViewModel

    private fun stubService(name: String = "printer", type: String = "_ipp._tcp.local.") =
        DiscoveredService(
            serviceType = type,
            instanceName = name,
            displayName = name,
            hostname = "$name.local",
            port = 631,
            ipAddresses = listOf("192.168.1.50")
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        viewModel = MdnsDiscoveryViewModel(useCase)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        // startScan() also runs a while(true){delay(100)} timer job in viewModelScope;
        // drain it before resetMain() so it never touches Main afterwards.
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts idle with no services`() {
            val state = viewModel.uiState.value
            assertTrue(!state.isScanning)
            assertTrue(state.services.isEmpty())
            assertTrue(!state.scanComplete)
        }
    }

    @Nested
    @DisplayName("startScan")
    inner class StartScan {

        @Test
        fun `sets isScanning true immediately`() {
            every { useCase(any()) } returns flow { /* completes without emitting: leaves isScanning untouched */ }

            viewModel.startScan()

            assertTrue(viewModel.uiState.value.isScanning)
        }

        @Test
        fun `accumulates discovered services grouped by type`() = runTest {
            val svc = stubService()
            every { useCase(any()) } returns flowOf(
                MdnsUpdate.ServiceFound(svc),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals(1, state.services.size)
            assertEquals(listOf(svc), state.servicesByType[svc.serviceType])
            assertTrue(state.scanComplete)
            assertTrue(!state.isScanning)
            assertEquals(1, state.totalFound)
        }

        @Test
        fun `re-discovering the same instance replaces it instead of duplicating`() = runTest {
            val first = stubService()
            val updated = first.copy(hostname = "printer2.local")
            every { useCase(any()) } returns flowOf(
                MdnsUpdate.ServiceFound(first),
                MdnsUpdate.ServiceFound(updated),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals(1, state.services.size)
            assertEquals("printer2.local", state.services.single().hostname)
        }

        @Test
        fun `is a no-op while already scanning`() {
            every { useCase(any()) } returns flow { /* completes without emitting: leaves isScanning untouched */ }
            viewModel.startScan()

            viewModel.startScan()

            verify(exactly = 1) { useCase(any()) }
        }

        @Test
        fun `sets error state and stops scanning on failure`() = runTest {
            every { useCase(any()) } returns flow { throw RuntimeException("mdns failed") }

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals("mdns failed", state.error)
            assertTrue(!state.isScanning)
        }
    }

    @Nested
    @DisplayName("stopScan and reset")
    inner class StopAndReset {

        @Test
        fun `stopScan sets isScanning false`() {
            every { useCase(any()) } returns flow { /* completes without emitting: leaves isScanning untouched */ }
            viewModel.startScan()

            viewModel.stopScan()

            assertTrue(!viewModel.uiState.value.isScanning)
        }

        @Test
        fun `reset clears back to default state`() = runTest {
            every { useCase(any()) } returns flowOf(
                MdnsUpdate.ServiceFound(stubService()),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )
            viewModel.startScan()

            viewModel.reset()

            assertEquals(MdnsDiscoveryUiState(), viewModel.uiState.value)
        }
    }
}
