package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PortScanViewModel")
class PortScanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var portScanUseCase: PortScanUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: PortScanViewModel

    private val stubResult = PortScanResult(
        port = 80,
        status = PortStatus.OPEN,
        serviceName = "HTTP",
        serviceDescription = null,
        banner = null,
        responseTimeMs = 10L
    )
    private val stubSummary = PortScanSummary(
        host = "example.com",
        resolvedIp = "93.184.216.34",
        scannedPorts = listOf(80),
        openPorts = 1,
        closedPorts = 0,
        filteredPorts = 0,
        scanDurationMs = 50L,
        results = listOf(stubResult)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        portScanUseCase = mockk()
        dataStore = mockk {
            every { data } returns flowOf(emptyPreferences())
        }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = PortScanViewModel(portScanUseCase, dataStore, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.uiState.value is PortScanUiState.Idle)
    }

    @Test
    fun `preset defaults to COMMON`() {
        assertEquals(PortScanPreset.COMMON, viewModel.selectedPreset.value)
    }

    @Nested
    @DisplayName("startScan state transitions")
    inner class StartScanStateTransitions {

        @Test
        fun `transitions to Finished on ScanComplete`() = runTest {
            every { portScanUseCase(any()) } returns flowOf(
                PortScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onHostChange("example.com")
            viewModel.startScan()
            assertTrue(viewModel.uiState.value is PortScanUiState.Finished)
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            every { portScanUseCase(any()) } returns flowOf(
                PortScanFlowResult.ValidationError("invalid host")
            )
            viewModel.onHostChange("bad##host")
            viewModel.startScan()
            val state = viewModel.uiState.value
            assertTrue(state is PortScanUiState.Error)
        }

        @Test
        fun `accumulates port results during scan`() = runTest {
            every { portScanUseCase(any()) } returns flowOf(
                PortScanFlowResult.PortScanned(stubResult, scannedCount = 1, totalCount = 1),
                PortScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onHostChange("example.com")
            viewModel.startScan()
            val state = viewModel.uiState.value as PortScanUiState.Finished
            assertEquals(1, state.summary.openPorts)
        }
    }

    @Test
    fun `onClear resets to Idle`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.startScan()
        viewModel.onClear()
        assertTrue(viewModel.uiState.value is PortScanUiState.Idle)
    }

    @Test
    fun `addRecent is called on startScan`() = runTest {
        every { portScanUseCase(any()) } returns flowOf()
        viewModel.onHostChange("example.com")
        viewModel.startScan()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, "example.com") }
    }
}
