package net.aieat.netswissknife.app.ui.screens.netinfo

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.platform.NetInfoLocalReader
import net.aieat.netswissknife.core.domain.PublicIpLookupUseCase
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.netinfo.IpAddressScope
import net.aieat.netswissknife.core.network.netinfo.IpFamily
import net.aieat.netswissknife.core.network.netinfo.LocalAddress
import net.aieat.netswissknife.core.network.netinfo.NetInfoSnapshot
import net.aieat.netswissknife.core.network.netinfo.NetworkTransport
import net.aieat.netswissknife.core.network.netinfo.PublicAddressInfo
import net.aieat.netswissknife.core.network.netinfo.PublicIpReport
import net.aieat.netswissknife.core.network.netinfo.SsidState
import net.aieat.netswissknife.core.network.netinfo.WifiLinkDetails
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetInfoViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val lookup = mockk<PublicIpLookupUseCase>()
    private lateinit var reader: FakeReader

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        reader = FakeReader(snapshot(), changes)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeReader(
        var snapshot: NetInfoSnapshot,
        override val changes: Flow<Unit>,
    ) : NetInfoLocalReader {
        var reads = 0
            private set

        override fun read(): NetInfoSnapshot {
            reads++
            return snapshot
        }
    }

    private fun snapshot(
        address: String = "192.168.1.20",
        rssi: Int = -50,
    ): NetInfoSnapshot =
        NetInfoSnapshot(
            hasActiveNetwork = true,
            transports = listOf(NetworkTransport.WIFI),
            metered = false,
            roaming = false,
            wifi = WifiLinkDetails(SsidState.Known("Home"), rssi, 866, 5180),
            interfaceName = "wlan0",
            addresses = listOf(LocalAddress(address, IpFamily.IPV4, 24, IpAddressScope.PRIVATE)),
            gateways = listOf("192.168.1.1"),
            dnsServers = listOf("192.168.1.1"),
            mtuBytes = 1500,
            mac = null,
            searchDomains = emptyList(),
            proxy = null,
        )

    private fun report(address: String = "203.0.113.7"): PublicIpReport =
        PublicIpReport(
            ipv4 = NetworkResult.Success(PublicAddressInfo(address, IpFamily.IPV4, null, null)),
            ipv6 = NetworkResult.error(ErrorCode.NETWORK_REQUEST_FAILED, "no route"),
        )

    private fun viewModel(): NetInfoViewModel = NetInfoViewModel(reader, lookup)

    @Test
    fun `opening the screen reads the network but never contacts the public IP services`() {
        val viewModel = viewModel()

        assertEquals(reader.snapshot, viewModel.uiState.value.local)
        assertEquals(PublicIpUiState.Idle, viewModel.uiState.value.publicIp)
        assertEquals(1, reader.reads)
        coVerify(exactly = 0) { lookup(Unit) }
    }

    @Test
    fun `checking the public IP publishes the report`() {
        coEvery { lookup(Unit) } returns report()
        val viewModel = viewModel()

        viewModel.checkPublicIp()

        val loaded = viewModel.uiState.value.publicIp as PublicIpUiState.Loaded
        assertEquals("203.0.113.7", (loaded.report.ipv4 as NetworkResult.Success).data.address)
    }

    @Test
    fun `a second check while one is running does not start another lookup`() {
        val gate = CompletableDeferred<PublicIpReport>()
        coEvery { lookup(Unit) } coAnswers { gate.await() }
        val viewModel = viewModel()

        viewModel.checkPublicIp()
        assertEquals(PublicIpUiState.Loading, viewModel.uiState.value.publicIp)
        viewModel.checkPublicIp()
        gate.complete(report())

        coVerify(exactly = 1) { lookup(Unit) }
        assertTrue(viewModel.uiState.value.publicIp is PublicIpUiState.Loaded)
    }

    @Test
    fun `refresh re-reads the network without contacting the public IP services`() {
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(2, reader.reads)
        coVerify(exactly = 0) { lookup(Unit) }
    }

    @Test
    fun `signal changes on the same network keep the public IP result`() {
        coEvery { lookup(Unit) } returns report()
        val viewModel = viewModel()
        viewModel.checkPublicIp()

        reader.snapshot = snapshot(rssi = -70)
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.publicIp is PublicIpUiState.Loaded)
    }

    @Test
    fun `moving to another network clears the public IP result`() {
        coEvery { lookup(Unit) } returns report()
        val viewModel = viewModel()
        viewModel.checkPublicIp()

        reader.snapshot = snapshot(address = "10.0.0.5")
        viewModel.refresh()

        assertEquals(PublicIpUiState.Idle, viewModel.uiState.value.publicIp)
    }

    @Test
    fun `a lookup that finishes after the network changed is discarded`() {
        val gate = CompletableDeferred<PublicIpReport>()
        coEvery { lookup(Unit) } coAnswers { gate.await() }
        val viewModel = viewModel()
        viewModel.checkPublicIp()

        reader.snapshot = snapshot(address = "10.0.0.5")
        viewModel.refresh()
        gate.complete(report())

        assertEquals(PublicIpUiState.Idle, viewModel.uiState.value.publicIp)
    }

    @Test
    fun `a live network change event re-reads the network after the debounce`() {
        val viewModel = viewModel()

        changes.tryEmit(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, reader.reads)
        assertEquals(reader.snapshot, viewModel.uiState.value.local)
        coVerify(exactly = 0) { lookup(Unit) }
    }
}
