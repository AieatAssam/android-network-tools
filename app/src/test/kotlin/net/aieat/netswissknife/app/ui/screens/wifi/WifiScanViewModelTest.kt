package net.aieat.netswissknife.app.ui.screens.wifi

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.WifiNotSupportedException
import net.aieat.netswissknife.core.domain.WifiScanUseCase
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiChannelInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WifiScanViewModel")
class WifiScanViewModelTest {

    // StandardTestDispatcher is required here (not Unconfined): WifiScanViewModel
    // owns an unbounded `while (true) { delay(...) }` auto-refresh loop, and an
    // eager/unconfined dispatcher lets that loop free-run to completion the moment
    // it's launched instead of yielding back -- it never returns control to the
    // test, spinning forever. Standard queues the loop's continuation instead of
    // running it, so it only advances when a test explicitly pumps with runCurrent().
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var wifiScanUseCase: WifiScanUseCase
    private lateinit var viewModel: WifiScanViewModel

    private fun stubAp(
        ssid: String = "TestNet",
        bssid: String = "AA:BB:CC:DD:EE:01",
        rssi: Int = -50,
        band: WifiBand = WifiBand.BAND_5GHZ
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = 5180,
        channelWidthMhz = 80,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 36,
        band = band,
        standard = WifiStandard.WIFI_6,
        security = WifiSecurity.WPA2,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 5210,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    private fun stubResult(vararg aps: WifiAccessPoint, wifiEnabled: Boolean = true) = WifiScanResult(
        accessPoints = aps.toList(),
        channels = emptyList<WifiChannelInfo>(),
        connectedNetwork = null,
        scanTimestampMs = 0L,
        isWifiEnabled = wifiEnabled
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        wifiScanUseCase = mockk()
        viewModel = WifiScanViewModel(wifiScanUseCase)
    }

    @AfterEach
    fun tearDown() {
        // Backstop: cancel any auto-refresh loop a test left running so it can't
        // bleed into the next test's dispatcher/scheduler.
        viewModel.stopAutoRefresh()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts in Idle`() {
            assertTrue(viewModel.uiState.value is WifiScanUiState.Idle)
        }

        @Test
        fun `auto-refresh starts disabled`() {
            assertTrue(!viewModel.autoRefresh.value)
        }
    }

    @Nested
    @DisplayName("onPermissionGranted")
    inner class PermissionGranted {

        @Test
        fun `starts scan when Wi-Fi is supported`() = runTest(testDispatcher) {
            every { wifiScanUseCase.isSupported } returns true
            coEvery { wifiScanUseCase() } returns stubResult(stubAp())

            viewModel.onPermissionGranted()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.Success)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `transitions to NotSupported when Wi-Fi hardware absent`() {
            every { wifiScanUseCase.isSupported } returns false

            viewModel.onPermissionGranted()

            assertTrue(viewModel.uiState.value is WifiScanUiState.NotSupported)
        }
    }

    @Test
    fun `onPermissionDenied transitions to NoPermission`() {
        viewModel.onPermissionDenied()
        assertTrue(viewModel.uiState.value is WifiScanUiState.NoPermission)
    }

    @Nested
    @DisplayName("startScan")
    inner class StartScan {

        @Test
        fun `Success state carries scan result`() = runTest(testDispatcher) {
            every { wifiScanUseCase.isSupported } returns true
            val ap = stubAp()
            coEvery { wifiScanUseCase() } returns stubResult(ap)

            viewModel.startScan()
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(1, state.result.accessPoints.size)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `WifiDisabled when scan reports Wi-Fi off`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } returns stubResult(wifiEnabled = false)

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.WifiDisabled)
        }

        @Test
        fun `NotSupported on WifiNotSupportedException`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } throws WifiNotSupportedException()

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.NotSupported)
        }

        @Test
        fun `NoPermission on SecurityException`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } throws SecurityException("denied")

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.NoPermission)
        }

        @Test
        fun `Error state on generic failure`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } throws RuntimeException("boom")

            viewModel.startScan()
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Error
            assertEquals("boom", state.message)
        }
    }

    @Nested
    @DisplayName("filters and sort")
    inner class FiltersAndSort {

        @Test
        fun `setBandFilter is a no-op outside Success state`() {
            viewModel.setBandFilter(WifiBand.BAND_5GHZ)
            assertTrue(viewModel.uiState.value is WifiScanUiState.Idle)
        }

        @Test
        fun `setBandFilter updates Success state`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } returns stubResult(stubAp(band = WifiBand.BAND_2_4GHZ))
            viewModel.startScan()
            runCurrent()

            viewModel.setBandFilter(WifiBand.BAND_2_4GHZ)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(WifiBand.BAND_2_4GHZ, state.bandFilter)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `setSortOrder updates Success state`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } returns stubResult(stubAp())
            viewModel.startScan()
            runCurrent()

            viewModel.setSortOrder(ApSortOrder.SSID)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(ApSortOrder.SSID, state.sortOrder)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `selectAccessPoint updates selectedAp`() = runTest(testDispatcher) {
            val ap = stubAp()
            coEvery { wifiScanUseCase() } returns stubResult(ap)
            viewModel.startScan()
            runCurrent()

            viewModel.selectAccessPoint(ap)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(ap, state.selectedAp)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }
    }

    @Nested
    @DisplayName("network expansion")
    inner class NetworkExpansion {

        @Test
        fun `toggleNetworkExpanded adds then removes id`() {
            viewModel.toggleNetworkExpanded("net-1")
            assertTrue("net-1" in viewModel.expandedNetworks.value)

            viewModel.toggleNetworkExpanded("net-1")
            assertTrue("net-1" !in viewModel.expandedNetworks.value)
        }
    }

    @Nested
    @DisplayName("auto-refresh")
    inner class AutoRefresh {

        @Test
        fun `toggleAutoRefresh flips state`() {
            viewModel.toggleAutoRefresh()
            assertTrue(viewModel.autoRefresh.value)

            viewModel.toggleAutoRefresh()
            assertTrue(!viewModel.autoRefresh.value)
        }

        @Test
        fun `startScan success enables auto-refresh`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase() } returns stubResult(stubAp())

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.autoRefresh.value)
            viewModel.stopAutoRefresh() // stop before runTest drains
        }

        @Test
        fun `stopAutoRefresh disables it`() {
            viewModel.startAutoRefresh()
            viewModel.stopAutoRefresh()
            assertTrue(!viewModel.autoRefresh.value)
        }
    }

    @Test
    fun `onRetry resets to Idle`() = runTest(testDispatcher) {
        coEvery { wifiScanUseCase() } throws RuntimeException("boom")
        viewModel.startScan()
        runCurrent()

        viewModel.onRetry()

        assertTrue(viewModel.uiState.value is WifiScanUiState.Idle)
    }
}
