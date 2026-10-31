package net.aieat.netswissknife.app.ui.screens.lan

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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LanScanViewModel state kept across leaving and returning")
class LanScanSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var lanScanUseCase: LanScanUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<LanScanViewModel>()

    private val subnet = "192.168.1.0/24"
    private val stubHost =
        LanHost(
            ip = "192.168.1.1",
            hostname = "router",
            macAddress = null,
            vendor = null,
            openPorts = emptyList(),
            pingTimeMs = 5L,
        )
    private val stubSummary =
        LanScanSummary(
            subnet = subnet,
            totalScanned = 1,
            aliveHosts = 1,
            scanDurationMs = 100L,
            hosts = listOf(stubHost),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lanScanUseCase = mockk()
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
    ): LanScanViewModel {
        val viewModel =
            LanScanViewModel(
                lanScanUseCase = lanScanUseCase,
                dataStore = dataStoreWith(prefs),
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: LanScanViewModel) {
        ViewModelStore().apply {
            put("lan", viewModel)
            clear()
        }
    }

    @Test
    fun `a finished result and its inputs are shown again after leaving and returning`() =
        runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(LanScanFlowResult.ScanComplete(stubSummary))
            val first = newViewModel()
            first.onSubnetChange(subnet)
            first.onTimeoutChange(2_500)
            first.startScan()
            // The scan's logging hops to Dispatchers.IO, so the terminal state lands after startScan returns.
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { first.uiState.first { it is LanScanUiState.Finished } }
            }
            leave(first)

            val returned = newViewModel()

            val restored = returned.uiState.value as LanScanUiState.Finished
            assertEquals(listOf(stubHost), restored.summary.hosts)
            assertEquals(subnet, returned.subnet.value)
            assertEquals(2_500, returned.timeoutMs.value)
        }

    @Test
    fun `a scan still running when the tool is left is kept as a partial result with the hosts found`() =
        runTest {
            every { lanScanUseCase(any(), any()) } returns
                flow {
                    emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 2))
                    awaitCancellation()
                }
            val first = newViewModel()
            first.onSubnetChange(subnet)
            first.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    first.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() }
                }
            }
            leave(first)

            val returned = newViewModel()

            // Leaving is not a user Stop: the scan keeps its hosts as a partial result, as a pause does.
            val restored = returned.uiState.value as LanScanUiState.Finished
            assertTrue(restored.partial)
            assertEquals(listOf(stubHost), restored.summary.hosts)
        }

    @Test
    fun `leaving a scan that a pause was already stopping keeps it as a partial result, not a user cancel`() =
        runTest {
            every { lanScanUseCase(any(), any()) } returns
                flow {
                    emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 2))
                    awaitCancellation()
                }
            val first = newViewModel()
            first.onSubnetChange(subnet)
            first.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    first.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() }
                }
            }
            first.onLifecyclePause()
            leave(first)

            val returned = newViewModel()

            val restored = returned.uiState.value as LanScanUiState.Finished
            assertTrue(restored.partial)
            assertEquals(listOf(stubHost), restored.summary.hosts)
        }

    @Test
    fun `a kept result is not restored when the subnet has since changed`() =
        runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(LanScanFlowResult.ScanComplete(stubSummary))
            val first = newViewModel()
            first.onSubnetChange(subnet)
            first.startScan()
            leave(first)

            val returned = newViewModel(handle = SavedStateHandle(mapOf("lanSubnet" to "10.0.0.0/24")))

            assertTrue(returned.uiState.value is LanScanUiState.Idle)
            assertEquals("10.0.0.0/24", returned.subnet.value)
        }

    @Test
    fun `saved inputs after process death beat the DataStore defaults and keep no result`() =
        runTest {
            val handle =
                SavedStateHandle(
                    mapOf("lanSubnet" to "10.0.0.0/24", "lanTimeoutMs" to 2_000, "lanConcurrency" to 20),
                )
            val defaults =
                mutablePreferencesOf(
                    AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 5_000,
                    AppPreferenceKeys.DEFAULT_CONCURRENCY to 40,
                )

            val restored = newViewModel(handle = handle, prefs = defaults)

            assertEquals("10.0.0.0/24", restored.subnet.value)
            assertEquals(2_000, restored.timeoutMs.value)
            assertEquals(20, restored.concurrency.value)
            assertTrue(restored.uiState.value is LanScanUiState.Idle)
        }

    @Test
    fun `a fresh tool with no saved state uses the DataStore defaults`() =
        runTest {
            val defaults =
                mutablePreferencesOf(
                    AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 5_000,
                    AppPreferenceKeys.DEFAULT_CONCURRENCY to 40,
                )

            val fresh = newViewModel(prefs = defaults)

            assertEquals(5_000, fresh.timeoutMs.value)
            assertEquals(40, fresh.concurrency.value)
            assertTrue(fresh.uiState.value is LanScanUiState.Idle)
        }
}
