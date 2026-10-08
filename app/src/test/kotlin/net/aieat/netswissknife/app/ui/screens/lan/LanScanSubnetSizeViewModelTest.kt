package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import net.aieat.netswissknife.app.platform.LinkInfo
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Detected networks too large for the scan limit default to this device's /24. */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanSubnetSizeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: LanScanUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        useCase = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        cidr: String,
        localIp: String? = "10.1.77.9",
        prefs: Preferences = emptyPreferences(),
    ): LanScanViewModel {
        val provider =
            mockk<LinkInfoProvider> {
                every { getLinkInfo() } returns
                    LinkInfo(
                        cidr = cidr,
                        gatewayIp = null,
                        dnsServers = emptyList(),
                        interfaceName = "wlan0",
                        isWifi = true,
                        isVpnActive = false,
                        localIp = localIp,
                    )
            }
        val dataStore =
            mockk<DataStore<Preferences>> {
                every { data } returns flowOf(prefs)
            }
        val recents =
            mockk<RecentHostsRepository>(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        return LanScanViewModel(useCase, dataStore, recents, provider)
    }

    private suspend fun LanScanViewModel.awaitDetectedSubnet(): String =
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { subnet.first { it.isNotBlank() } }
        }

    @Test
    fun `oversized detected network defaults to the device slash-24`() =
        runTest {
            val vm = viewModel(cidr = "10.1.0.0/16")

            assertEquals("10.1.77.0/24", vm.awaitDetectedSubnet())
            assertEquals("10.1.0.0/16", vm.subnetNarrowedFrom.value)
        }

    @Test
    fun `network that fits the limit is kept whole`() =
        runTest {
            val vm = viewModel(cidr = "10.1.76.0/22")

            assertEquals("10.1.76.0/22", vm.awaitDetectedSubnet())
            assertNull(vm.subnetNarrowedFrom.value)
        }

    @Test
    fun `saved concurrency is applied before deciding to narrow`() =
        runTest {
            val vm =
                viewModel(
                    cidr = "10.1.64.0/20",
                    prefs = preferencesOf(AppPreferenceKeys.DEFAULT_CONCURRENCY to 500),
                )

            assertEquals("10.1.64.0/20", vm.awaitDetectedSubnet())
            assertNull(vm.subnetNarrowedFrom.value)
        }

    @Test
    fun `editing the subnet clears the narrowed hint`() =
        runTest {
            val vm = viewModel(cidr = "10.1.0.0/16")
            vm.awaitDetectedSubnet()

            vm.onSubnetChange("10.1.78.0/24")

            assertNull(vm.subnetNarrowedFrom.value)
        }

    @Test
    fun `budget error suggests the device slice and can scan it`() =
        runTest {
            every { useCase(any(), any()) } returns
                flowOf(
                    LanScanFlowResult.ScanComplete(LanScanSummary("10.1.77.0/24", 0, 0, 0, emptyList())),
                )
            val vm = viewModel(cidr = "10.1.77.0/24")
            vm.awaitDetectedSubnet()
            vm.onSubnetChange("10.1.0.0/16")

            vm.startScan()

            val error = vm.uiState.value as LanScanUiState.Error
            assertTrue(error.isBudgetLimit)
            assertEquals("10.1.77.0/24", error.suggestedSubnet)

            vm.scanSubnet(error.suggestedSubnet!!)
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { vm.uiState.first { it is LanScanUiState.Finished } }
            }
            verify { useCase(match { it.subnet == "10.1.77.0/24" }, any()) }
        }

    @Test
    fun `prefix broader than slash-16 reports the limit with a suggestion`() =
        runTest {
            val vm = viewModel(cidr = "10.1.77.0/24", localIp = "10.12.34.56")
            vm.awaitDetectedSubnet()
            vm.onSubnetChange("10.0.0.0/8")

            vm.startScan()

            val error = vm.uiState.value as LanScanUiState.Error
            assertTrue(error.isBudgetLimit)
            assertEquals("10.12.34.0/24", error.suggestedSubnet)
        }
}
