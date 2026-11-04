package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PortScanViewModel form and result kept across leaving and returning")
class PortScanSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var portScanUseCase: PortScanUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<PortScanViewModel>()

    private val stubResult =
        PortScanResult(
            port = 80,
            status = PortStatus.OPEN,
            serviceName = "HTTP",
            serviceDescription = null,
            banner = null,
            responseTimeMs = 10L,
        )
    private val stubSummary =
        PortScanSummary(
            host = "example.com",
            resolvedIp = "93.184.216.34",
            scannedPorts = listOf(80),
            openPorts = 1,
            closedPorts = 0,
            filteredPorts = 0,
            scanDurationMs = 50L,
            results = listOf(stubResult),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        portScanUseCase = mockk()
        every { portScanUseCase.newSession(any()) } answers {
            OperationSession(OperationBudget.start())
        }
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        memory = ToolSessionMemory()
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            // Every ViewModel must finish its Main-dispatched work before resetMain(), or the
            // leaked coroutine fails the next test class.
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
            Dispatchers.resetMain()
        }

    private fun dataStoreWith(prefs: Preferences): DataStore<Preferences> =
        mockk {
            every { data } returns flowOf(prefs)
        }

    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        prefs: Preferences = emptyPreferences(),
    ): PortScanViewModel {
        val viewModel =
            PortScanViewModel(
                portScanUseCase = portScanUseCase,
                dataStore = dataStoreWith(prefs),
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                linkInfoProvider = LinkInfoProvider({ true }, { true }),
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: PortScanViewModel) {
        ViewModelStore().apply {
            put("ports", viewModel)
            clear()
        }
    }

    @Test
    fun `a finished result and the form are shown again after leaving and returning`() =
        runTest {
            every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
            val first = newViewModel()
            first.onHostChange("example.com")
            first.onPresetChange(PortScanPreset.WEB)
            first.onStartPortChange("80")
            first.onEndPortChange("443")
            first.onTimeoutChange(3_000)
            first.startScan()
            assertTrue(first.uiState.value is PortScanUiState.Finished)
            leave(first)

            val returned = newViewModel()

            val restored = returned.uiState.value as PortScanUiState.Finished
            assertEquals("example.com", restored.summary.host)
            assertEquals("example.com", returned.host.value)
            assertEquals(PortScanPreset.WEB, returned.selectedPreset.value)
            assertEquals("80", returned.startPort.value)
            assertEquals("443", returned.endPort.value)
            assertEquals(3_000, returned.timeoutMs.value)
        }

    @Test
    fun `a result is not shown again for a different host after a handoff`() =
        runTest {
            every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
            val first = newViewModel()
            first.onHostChange("example.com")
            first.startScan()
            leave(first)

            val returned = newViewModel(handle = SavedStateHandle(mapOf("host" to "192.0.2.8")))

            assertTrue(returned.uiState.value is PortScanUiState.Idle)
            assertEquals("192.0.2.8", returned.host.value)
        }

    @Test
    fun `saved form values after process death beat the DataStore defaults and keep no result`() =
        runTest {
            val handle =
                SavedStateHandle(
                    mapOf(
                        "portPreset" to PortScanPreset.DATABASE.name,
                        "portStartPort" to "5000",
                        "portEndPort" to "5010",
                        "portTimeoutMs" to 1_500,
                        "portConcurrency" to 12,
                        "portAggressiveProbes" to false,
                    ),
                )
            val defaults =
                mutablePreferencesOf(
                    AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 5_000,
                    AppPreferenceKeys.DEFAULT_CONCURRENCY to 40,
                )

            val restored = newViewModel(handle = handle, prefs = defaults)

            assertEquals(PortScanPreset.DATABASE, restored.selectedPreset.value)
            assertEquals("5000", restored.startPort.value)
            assertEquals("5010", restored.endPort.value)
            assertEquals(1_500, restored.timeoutMs.value)
            assertEquals(12, restored.concurrency.value)
            assertTrue(restored.uiState.value is PortScanUiState.Idle)
        }
}
