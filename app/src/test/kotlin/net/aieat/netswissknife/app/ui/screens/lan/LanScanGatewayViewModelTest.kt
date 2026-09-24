package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfo
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
class LanScanGatewayViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: LanScanUseCase
    private lateinit var provider: LinkInfoProvider
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        useCase = mockk()
        provider = mockk {
            every { getLinkInfo() } returns LinkInfo(
                cidr = "192.168.1.0/24",
                gatewayIp = "192.168.1.254",
                dnsServers = emptyList(),
                interfaceName = "wlan0",
                isWifi = true,
                isVpnActive = false,
            )
        }
        val dataStore = mockk<DataStore<Preferences>> {
            every { data } returns flowOf(emptyPreferences())
        }
        val recents = mockk<RecentHostsRepository>(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = LanScanViewModel(useCase, dataStore, recents, provider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh prefers link info and stores gateway`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { viewModel.gatewayIp.first { it != null } }
        }
        assertEquals("192.168.1.0/24", viewModel.subnet.value)
        assertEquals("192.168.1.254", viewModel.gatewayIp.value)
    }

    @Test
    fun `start scan forwards gateway`() = runTest {
        every { useCase(any(), any()) } returns flowOf(
            LanScanFlowResult.ScanComplete(
                LanScanSummary("192.168.1.0/24", 0, 0, 0, emptyList()),
            ),
        )
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { viewModel.gatewayIp.first { it != null } }
        }
        viewModel.startScan()
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
        }
        io.mockk.verify { useCase(match { it.gatewayIp == "192.168.1.254" }, any()) }
        assertTrue(viewModel.uiState.value is LanScanUiState.Finished)
    }

    @Test
    fun `scan ports emits navigation event`() = runTest {
        viewModel.onScanPorts("192.168.1.10")
        assertEquals(
            LanNavEvent.NavigateToPorts("192.168.1.10"),
            viewModel.navigationEvents.first(),
        )
    }
}
