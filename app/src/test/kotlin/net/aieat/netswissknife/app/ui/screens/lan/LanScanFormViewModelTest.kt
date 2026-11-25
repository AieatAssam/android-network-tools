package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfo
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Form defaults, saved list choices and error copy of the LAN scanner. */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanFormViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<LanScanViewModel>()
    private lateinit var useCase: LanScanUseCase
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        useCase = mockk()
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            viewModels.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
            viewModels.clear()
            Dispatchers.resetMain()
        }

    private fun viewModel(
        prefs: Preferences = emptyPreferences(),
        cidr: String = "10.1.77.0/24",
        savedState: SavedStateHandle = SavedStateHandle(),
    ): LanScanViewModel {
        val provider =
            mockk<LinkInfoProvider> {
                every { getLinkInfo() } returns
                    LinkInfo(
                        cidr = cidr,
                        gatewayIp = "10.1.77.1",
                        dnsServers = emptyList(),
                        interfaceName = "wlan0",
                        isWifi = true,
                        isVpnActive = false,
                        localIp = "10.1.77.9",
                    )
            }
        dataStore =
            mockk {
                every { data } returns flowOf(prefs)
                coEvery { updateData(any()) } returns prefs
            }
        val recents =
            mockk<RecentHostsRepository>(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        return LanScanViewModel(useCase, dataStore, recents, provider, savedStateHandle = savedState)
            .also(viewModels::add)
    }

    private suspend fun LanScanViewModel.awaitSubnet(): String {
        val dispatcher = Dispatchers.Default
        return withContext(dispatcher) { withTimeout(2_000) { subnet.first { it.isNotBlank() } } }
    }

    private suspend fun LanScanViewModel.awaitState(done: (LanScanUiState) -> Boolean): LanScanUiState {
        val dispatcher = Dispatchers.Default
        return withContext(dispatcher) { withTimeout(2_000) { uiState.first(done) } }
    }

    @Test
    fun `saved concurrency too low for the detected subnet is raised and the hint names it`() =
        runTest {
            val prefs =
                preferencesOf(
                    AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 10_000,
                    AppPreferenceKeys.DEFAULT_CONCURRENCY to 2,
                )
            val vm = viewModel(prefs)

            assertEquals("10.1.77.0/24", vm.awaitSubnet())
            assertEquals(24, vm.concurrency.value)
            assertEquals(2, vm.concurrencyRaisedFrom.value)

            vm.onConcurrencyChange(32)
            assertNull(vm.concurrencyRaisedFrom.value)
        }

    @Test
    fun `concurrency the user already chose is never raised`() =
        runTest {
            val prefs = preferencesOf(AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 10_000)
            val vm = viewModel(prefs, savedState = SavedStateHandle(mapOf("lanConcurrency" to 2)))

            vm.awaitSubnet()
            assertEquals(2, vm.concurrency.value)
            assertNull(vm.concurrencyRaisedFrom.value)
        }

    @Test
    fun `detect replaces a typed subnet with the detected one`() =
        runTest {
            val vm = viewModel()
            vm.awaitSubnet()
            vm.onSubnetChange("192.168.50.0/24")

            vm.detectSubnet()

            assertEquals("10.1.77.0/24", vm.awaitSubnet())
        }

    @Test
    fun `saved device filter is restored and a new choice is saved`() =
        runTest {
            val vm = viewModel(preferencesOf(AppPreferenceKeys.LAN_HOST_FILTER to "HasPorts"))
            vm.awaitSubnet()
            assertEquals(LanHostFilter.HasPorts, vm.hostFilter.value)

            vm.onHostFilterChange(LanHostFilter.Gateway)

            assertEquals(LanHostFilter.Gateway, vm.hostFilter.value)
            coVerify { dataStore.updateData(any()) }
        }

    @Test
    fun `error card goes once a different subnet is chosen`() =
        runTest {
            val info = ErrorInfo(code = ErrorCode.SUBNET_INVALID, developerMessage = "Invalid subnet")
            every { useCase(any(), any()) } returns flowOf(LanScanFlowResult.ValidationError(info))
            val vm = viewModel()
            vm.awaitSubnet()
            vm.onSubnetChange("not a host")
            vm.startScan()
            vm.awaitState { it is LanScanUiState.Error }

            vm.onSubnetChange("10.1.77.0/24")

            assertEquals(LanScanUiState.Idle, vm.uiState.value)
        }

    @Test
    fun `time limit error goes once timeout or concurrency changes`() =
        runTest {
            val vm = viewModel()
            vm.awaitSubnet()
            vm.onSubnetChange("10.0.0.0/8")
            vm.startScan()
            vm.awaitState { it is LanScanUiState.Error }
            vm.onTimeoutChange(500)
            assertEquals(LanScanUiState.Idle, vm.uiState.value)

            vm.onSubnetChange("10.0.0.0/8")
            vm.startScan()
            vm.awaitState { it is LanScanUiState.Error }
            vm.onConcurrencyChange(496)
            assertEquals(LanScanUiState.Idle, vm.uiState.value)
        }

    @Test
    fun `scan request carries this device's address`() =
        runTest {
            every { useCase(any(), any()) } returns
                flowOf(LanScanFlowResult.ScanComplete(LanScanSummary("10.1.77.0/24", 0, 0, 0, emptyList())))
            val vm = viewModel()
            vm.awaitSubnet()

            vm.startScan()
            vm.awaitState { it is LanScanUiState.Finished }

            verify { useCase(match { it.localIp == "10.1.77.9" }, any()) }
        }

    @Test
    fun `validation error is shown through the shared error copy`() =
        runTest {
            val info = ErrorInfo(code = ErrorCode.SUBNET_INVALID, developerMessage = "Invalid subnet")
            every { useCase(any(), any()) } returns flowOf(LanScanFlowResult.ValidationError(info))
            val vm = viewModel()
            vm.awaitSubnet()

            vm.startScan()
            val error = vm.awaitState { it is LanScanUiState.Error } as LanScanUiState.Error

            assertEquals(R.string.err_subnet_invalid, (error.text as UiText.Res).id)
        }

    @Test
    fun `unexpected failure shows translated copy and keeps the cause for logs only`() =
        runTest {
            every { useCase(any(), any()) } returns flow { throw IllegalStateException("socket exploded") }
            val vm = viewModel()
            vm.awaitSubnet()

            vm.startScan()
            val error = vm.awaitState { it is LanScanUiState.Error } as LanScanUiState.Error

            assertEquals(UiText.Res(R.string.lan_error_unexpected), error.text)
            assertEquals("socket exploded", error.message)
            assertFalse(error.message.startsWith("Scan failed"))
        }
}
