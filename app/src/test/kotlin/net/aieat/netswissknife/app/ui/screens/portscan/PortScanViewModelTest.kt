package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
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
import net.aieat.netswissknife.core.network.MonotonicClock
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
    private val testClock = FakeMonotonicClock()

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
        viewModel = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            monotonicClock = testClock
        )
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
        fun `an exception during the scan flow surfaces as Error instead of crashing`() = runTest {
            every { portScanUseCase(any()) } returns kotlinx.coroutines.flow.flow {
                throw java.net.SocketException("network unreachable")
            }
            viewModel.onHostChange("example.com")
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

    @Test
    fun `startScan normalizes host before probing and saving`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ValidationError("test"))
        viewModel.onHostChange("  Example.COM.  ")

        viewModel.startScan()

        assertEquals("example.com", viewModel.host.value)
        verify { portScanUseCase(match { it.host == "example.com" }) }
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, "example.com") }
    }

    @Test
    fun `startScan passes concurrency maximum of 500 to scan params`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.onConcurrencyChange(500)

        viewModel.startScan()

        verify { portScanUseCase(match { it.concurrency == 500 }) }
    }

    @Test
    fun `stored concurrency above the supported range is clamped before scan`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        val seededPreferences = mutablePreferencesOf(AppPreferenceKeys.DEFAULT_CONCURRENCY to 501)
        val seededDataStore = mockk<DataStore<Preferences>> {
            every { data } returns flowOf(seededPreferences)
        }
        val seededViewModel = PortScanViewModel(
            portScanUseCase,
            seededDataStore,
            recentHostsRepository,
            monotonicClock = testClock
        )
        seededViewModel.onHostChange("example.com")

        seededViewModel.startScan()

        assertEquals(500, seededViewModel.concurrency.value)
        verify { portScanUseCase(match { it.concurrency == 500 }) }
    }

    @Test
    fun `concurrency changes are clamped to the supported range`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.onConcurrencyChange(-1)

        viewModel.startScan()

        assertEquals(1, viewModel.concurrency.value)
        verify { portScanUseCase(match { it.concurrency == 1 }) }
    }

    @Test
    fun `invalid internal whitespace is not saved to recents`() = runTest {
        every { portScanUseCase(any()) } returns flowOf(PortScanFlowResult.ValidationError("invalid host"))
        viewModel.onHostChange("bad host")

        viewModel.startScan()

        verify { portScanUseCase(match { it.host == "bad host" }) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, any()) }
    }

    @Test
    fun `stopping a scan preserves resolved IP and measures duration through stop`() = runTest {
        testClock.nowNanos = 4_000_000_000L
        every { portScanUseCase(any()) } returns kotlinx.coroutines.flow.flow {
            emit(PortScanFlowResult.Started(resolvedIp = "93.184.216.34", totalCount = 3))
            emit(
                PortScanFlowResult.PortScanned(
                    result = stubResult,
                    scannedCount = 1,
                    totalCount = 3
                )
            )
            awaitCancellation()
        }
        viewModel.onHostChange("example.com")

        viewModel.startScan()
        testClock.nowNanos += 725_000_000L // Time passes after the last result while probes remain in flight.
        viewModel.onStopScan()

        val state = viewModel.uiState.value as PortScanUiState.Finished
        assertEquals("example.com", state.summary.host)
        assertEquals("93.184.216.34", state.summary.resolvedIp)
        assertEquals(725L, state.summary.scanDurationMs)
        assertEquals(listOf(80), state.summary.scannedPorts)
        assertEquals(listOf(stubResult), state.summary.results)
    }

    @Test
    fun `scan enters Scanning immediately with custom total before first result`() = runTest {
        every { portScanUseCase(any()) } returns kotlinx.coroutines.flow.flow {
            awaitCancellation()
        }
        viewModel.onHostChange("example.com")
        viewModel.onPresetChange(PortScanPreset.CUSTOM)
        viewModel.onStartPortChange("80")
        viewModel.onEndPortChange("82")

        viewModel.startScan()

        val state = viewModel.uiState.value as PortScanUiState.Scanning
        assertEquals(emptyList<PortScanResult>(), state.liveResults)
        assertEquals(0, state.scannedCount)
        assertEquals(3, state.totalCount)
    }

    @Test
    fun `stopping after target resolution but before first result keeps resolved IP`() = runTest {
        testClock.nowNanos = 8_000_000_000L
        every { portScanUseCase(any()) } returns kotlinx.coroutines.flow.flow {
            emit(PortScanFlowResult.Started(resolvedIp = "93.184.216.34", totalCount = 20))
            awaitCancellation()
        }
        viewModel.onHostChange("example.com")

        viewModel.startScan()
        val scanning = viewModel.uiState.value as PortScanUiState.Scanning
        assertEquals("93.184.216.34", scanning.resolvedIp)
        assertEquals(emptyList<PortScanResult>(), scanning.liveResults)
        testClock.nowNanos += 1_250_000_000L
        viewModel.onStopScan()

        val summary = (viewModel.uiState.value as PortScanUiState.Finished).summary
        assertEquals("example.com", summary.host)
        assertEquals("93.184.216.34", summary.resolvedIp)
        assertEquals(1_250L, summary.scanDurationMs)
        assertTrue(summary.results.isEmpty())
    }

    private class FakeMonotonicClock(var nowNanos: Long = 1_000_000_000L) : MonotonicClock {
        override fun nowNanos(): Long = nowNanos
    }
}
