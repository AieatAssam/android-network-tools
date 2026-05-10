package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LanScanViewModel")
class LanScanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var lanScanUseCase: LanScanUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: LanScanViewModel

    private val stubHost = LanHost(
        ip = "192.168.1.1",
        hostname = "router",
        macAddress = null,
        vendor = null,
        openPorts = emptyList(),
        pingTimeMs = 5L
    )
    private val stubSummary = LanScanSummary(
        subnet = "192.168.1.0/24",
        totalScanned = 1,
        aliveHosts = 1,
        scanDurationMs = 100L,
        hosts = listOf(stubHost)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lanScanUseCase = mockk()
        dataStore = mockk {
            every { data } returns flowOf(emptyPreferences())
        }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = LanScanViewModel(lanScanUseCase, dataStore, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts in Idle state`() = runTest {
            // uiState is set synchronously; just verify initial value
            assertTrue(viewModel.uiState.value is LanScanUiState.Idle)
        }
    }

    @Nested
    @DisplayName("startScan state transitions")
    inner class StartScanStateTransitions {

        @Test
        fun `transitions to Finished on ScanComplete`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            // withContext(Dispatchers.Default) uses real time so withTimeout works correctly
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            assertTrue(state is LanScanUiState.Finished, "Expected Finished but was $state")
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.ValidationError("invalid subnet")
            )
            viewModel.onSubnetChange("bad")
            viewModel.startScan()
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            assertTrue(state is LanScanUiState.Error)
            assertEquals("invalid subnet", (state as LanScanUiState.Error).message)
        }

        @Test
        fun `accumulates hosts during scan`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 10),
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            } as LanScanUiState.Finished
            assertEquals(1, state.summary.hosts.size)
        }
    }

    @Nested
    @DisplayName("stop and clear")
    inner class StopAndClear {

        @Test
        fun `onClear resets to Idle`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            viewModel.onClear()
            assertTrue(viewModel.uiState.value is LanScanUiState.Idle)
        }
    }

    @Nested
    @DisplayName("recent subnets")
    inner class RecentSubnets {

        @Test
        fun `addRecent is called on first host found`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 1),
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("10.0.0.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_LAN_SUBNETS, "10.0.0.0/24") }
        }

        @Test
        fun `addRecent is NOT called when ValidationError fires`() = runTest {
            every { lanScanUseCase(any()) } returns flowOf(
                LanScanFlowResult.ValidationError("invalid subnet")
            )
            viewModel.onSubnetChange("bad")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
        }
    }
}
