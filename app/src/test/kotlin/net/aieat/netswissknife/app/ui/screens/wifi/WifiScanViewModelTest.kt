package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.core.domain.WifiNotSupportedException
import net.aieat.netswissknife.core.domain.WifiScanUseCase
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiChannelInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wifi.WifiScanOperation
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
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefsFlow: MutableStateFlow<Preferences>
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

    private fun stubResult(
        vararg aps: WifiAccessPoint,
        wifiEnabled: Boolean = true,
        locationEnabled: Boolean = true,
        refreshStatus: WifiScanRefreshStatus = WifiScanRefreshStatus.NOT_REQUESTED,
        scanAgeMs: Long? = null,
        cacheReadElapsedRealtimeMs: Long = 0L
    ) = WifiScanResult(
        accessPoints = aps.toList(),
        channels = emptyList<WifiChannelInfo>(),
        connectedNetwork = null,
        scanTimestampMs = 0L,
        isWifiEnabled = wifiEnabled,
        isFresh = scanAgeMs == null || scanAgeMs <= 15_000L,
        scanAgeMs = scanAgeMs,
        cacheReadElapsedRealtimeMs = cacheReadElapsedRealtimeMs,
        refreshStatus = refreshStatus,
        locationEnabled = locationEnabled
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        wifiScanUseCase = mockk()
        prefsFlow = MutableStateFlow(emptyPreferences())
        dataStore = mockk {
            every { data } answers { prefsFlow }
            coEvery { updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                updated
            }
        }
        viewModel = WifiScanViewModel(wifiScanUseCase, dataStore)
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
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())

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

    @Test
    fun `denied permission retry retains the last successful scan`() = runTest(testDispatcher) {
        val previous = stubResult(stubAp()).copy(scanTimestampMs = System.currentTimeMillis() - 42_000L)
        coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns previous
        viewModel.startScan()
        runCurrent()

        viewModel.onPermissionDenied()

        val state = viewModel.uiState.value as WifiScanUiState.Success
        assertEquals(previous.accessPoints, state.result.accessPoints)
        assertEquals(WifiScanRefreshStatus.PERMISSION_DENIED, state.refreshStatus)
        assertTrue(state.result.scanAgeMs!! >= 42_000L)
        assertTrue(!viewModel.autoRefresh.value)
    }

    @Nested
    @DisplayName("startScan")
    inner class StartScan {

        @Test
        fun `Success state carries scan result`() = runTest(testDispatcher) {
            every { wifiScanUseCase.isSupported } returns true
            val ap = stubAp()
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(ap)

            viewModel.startScan()
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(1, state.result.accessPoints.size)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `WifiDisabled when scan reports Wi-Fi off`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(wifiEnabled = false)

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.WifiDisabled)
        }

        @Test
        fun `LocationDisabled when Location Services are off`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(locationEnabled = false)

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.LocationDisabled)
            assertTrue(!viewModel.autoRefresh.value)
        }

        @Test
        fun `Success exposes freshness and rejected refresh status`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns
                stubResult(stubAp(), refreshStatus = WifiScanRefreshStatus.REJECTED, scanAgeMs = 42_000L)

            viewModel.startScan()
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(WifiScanRefreshStatus.REJECTED, state.refreshStatus)
            assertEquals(42_000L, state.scanAgeMs)
            assertTrue(!state.isFresh)
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `failed refresh retains the prior data and sample timestamp`() = runTest(testDispatcher) {
            val ap = stubAp()
            val previous = stubResult(
                ap,
                scanAgeMs = 42_000L,
                cacheReadElapsedRealtimeMs = 100_000L
            ).copy(scanTimestampMs = 123_000L)
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns previous
            viewModel.startScan()
            runCurrent()
            viewModel.stopAutoRefresh()
            viewModel.selectAccessPoint(ap)

            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(
                refreshStatus = WifiScanRefreshStatus.TIMED_OUT,
                cacheReadElapsedRealtimeMs = 105_000L
            )
            viewModel.startScan(silent = true)
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(listOf("AA:BB:CC:DD:EE:01"), state.result.accessPoints.map { it.bssid })
            assertEquals(123_000L, state.result.scanTimestampMs)
            assertEquals(47_000L, state.result.scanAgeMs)
            assertEquals(ap, state.selectedAp)
            assertNull(viewModel.apDisappearedEvent.value)
            assertEquals(WifiScanRefreshStatus.TIMED_OUT, state.refreshStatus)
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `NotSupported on WifiNotSupportedException`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws WifiNotSupportedException()

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.NotSupported)
        }

        @Test
        fun `NoPermission on SecurityException`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws SecurityException("denied")

            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.uiState.value is WifiScanUiState.NoPermission)
        }

        @Test
        fun `Error state on generic failure`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws RuntimeException("boom")

            viewModel.startScan()
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Error
            assertEquals("boom", state.message)
        }

        @Test
        fun `user stop closes registered resources and ignores a late scan result`() = runTest(testDispatcher) {
            val lateResult = CompletableDeferred<WifiScanResult>()
            val resourcesClosed = CompletableDeferred<Unit>()
            var operationSession: OperationSession? = null
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } coAnswers {
                operationSession = secondArg()
                operationSession!!.resources.register(AutoCloseable { resourcesClosed.complete(Unit) })
                withContext(NonCancellable) { lateResult.await() }
            }

            viewModel.startScan()
            runCurrent()
            viewModel.cancelScan()
            runCurrent()

            resourcesClosed.await()
            assertEquals(CancellationReason.USER_STOP, operationSession?.cancellationReason)
            assertTrue(viewModel.uiState.value is WifiScanUiState.Cancelled)
            lateResult.complete(stubResult(stubAp()))
            runCurrent()
            assertTrue(viewModel.uiState.value is WifiScanUiState.Cancelled)
        }

        @Test
        fun `user stop restores the previous successful scan`() = runTest(testDispatcher) {
            val original = stubResult(stubAp())
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns original
            viewModel.startScan()
            runCurrent()
            viewModel.stopAutoRefresh()
            val previous = viewModel.uiState.value as WifiScanUiState.Success

            val pendingResult = CompletableDeferred<WifiScanResult>()
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } coAnswers { withContext(NonCancellable) { pendingResult.await() } }
            viewModel.startScan()
            runCurrent()
            viewModel.cancelScan()
            runCurrent()

            assertEquals(previous, viewModel.uiState.value)
            pendingResult.complete(stubResult(stubAp(ssid = "late")))
            runCurrent()
            assertEquals(previous, viewModel.uiState.value)
        }

        @Test
        fun `operation deadline closes resources and reports timed out without late success`() = runTest(testDispatcher) {
            val ap = stubAp()
            val previous = stubResult(ap).copy(
                scanTimestampMs = System.currentTimeMillis() - 42_000L,
                scanAgeMs = 42_000L,
            )
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } returns previous
            viewModel.startScan()
            runCurrent()
            viewModel.stopAutoRefresh()

            val lateResult = CompletableDeferred<WifiScanResult>()
            val resourcesClosed = CompletableDeferred<Unit>()
            val deadlineClock = MonotonicClock { testScheduler.currentTime * 1_000_000L }
            viewModel.operationSessionFactory = {
                OperationSession(
                    OperationBudget.start(
                        timeoutMillis = WifiScanOperation.TIMEOUT_MILLIS,
                        clock = deadlineClock,
                    ),
                )
            }
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } coAnswers {
                secondArg<OperationSession>().resources.register(AutoCloseable { resourcesClosed.complete(Unit) })
                withContext(NonCancellable) { lateResult.await() }
            }

            viewModel.startScan(silent = true)
            runCurrent()
            advanceTimeBy(WifiScanOperation.TIMEOUT_MILLIS)
            runCurrent()

            resourcesClosed.await()
            lateResult.complete(stubResult(stubAp()))
            runCurrent()
            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(previous.accessPoints, state.result.accessPoints)
            assertEquals(previous.scanTimestampMs, state.result.scanTimestampMs)
            assertEquals(WifiScanRefreshStatus.TIMED_OUT, state.refreshStatus)
            assertTrue(state.scanAgeMs!! >= 42_000L)
            assertTrue(!state.isFresh)
        }

        @Test
        fun `exception after a success keeps cached data with a failed refresh status`() = runTest(testDispatcher) {
            val previous = stubResult(stubAp()).copy(
                scanTimestampMs = System.currentTimeMillis() - 42_000L,
                scanAgeMs = 42_000L
            )
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns previous
            viewModel.startScan()
            runCurrent()
            viewModel.stopAutoRefresh()

            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws RuntimeException("boom")
            viewModel.startScan(silent = true)
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(previous.accessPoints, state.result.accessPoints)
            assertEquals(previous.scanTimestampMs, state.result.scanTimestampMs)
            assertEquals(WifiScanRefreshStatus.FAILED, state.refreshStatus)
            assertTrue(state.result.scanAgeMs!! >= 42_000L)
            assertTrue(!state.result.isFresh)
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `permission revoked after a success keeps data and reports permission failure`() = runTest(testDispatcher) {
            val previous = stubResult(stubAp()).copy(
                scanTimestampMs = System.currentTimeMillis() - 42_000L,
                scanAgeMs = 42_000L
            )
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns previous
            viewModel.startScan()
            runCurrent()

            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws SecurityException("denied")
            viewModel.startScan(silent = true)
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(previous.accessPoints, state.result.accessPoints)
            assertEquals(previous.scanTimestampMs, state.result.scanTimestampMs)
            assertEquals(WifiScanRefreshStatus.PERMISSION_DENIED, state.refreshStatus)
            assertTrue(state.result.scanAgeMs!! >= 42_000L)
            assertTrue(!state.result.isFresh)
            assertTrue(!viewModel.autoRefresh.value)
            viewModel.stopAutoRefresh()
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
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp(band = WifiBand.BAND_2_4GHZ))
            viewModel.startScan()
            runCurrent()

            viewModel.setBandFilter(WifiBand.BAND_2_4GHZ)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(WifiBand.BAND_2_4GHZ, state.bandFilter)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }

        @Test
        fun `setSortOrder updates Success state`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())
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
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(ap)
            viewModel.startScan()
            runCurrent()

            viewModel.selectAccessPoint(ap)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(ap, state.selectedAp)
            viewModel.stopAutoRefresh() // scan success starts the auto-refresh loop; stop it before runTest drains
        }
    }

    @Nested
    @DisplayName("order freezes while inspecting")
    inner class OrderFreeze {

        @Test
        fun `selecting an AP freezes the list order across a live refresh`() = runTest(testDispatcher) {
            val weak = stubAp(ssid = "Weak", bssid = "AA:BB:CC:DD:EE:01", rssi = -80)
            val strong = stubAp(ssid = "Strong", bssid = "AA:BB:CC:DD:EE:02", rssi = -40)
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(weak, strong)
            viewModel.startScan()
            runCurrent()

            // Default sort is by signal: "Strong" leads.
            var state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(listOf("Strong", "Weak"), state.filteredNetworks.map { it.displaySsid })

            // Inspect "Weak" — this pins the current (Strong, Weak) order.
            viewModel.selectAccessPoint(weak)

            // A live refresh now makes "Weak" the stronger signal — normally this would
            // flip the order, but it must stay pinned while something is selected.
            val weakNowStrong = weak.copy(rssi = -30)
            val strongNowWeak = strong.copy(rssi = -90)
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(weakNowStrong, strongNowWeak)
            viewModel.startScan(silent = true)
            runCurrent()

            state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(listOf("Strong", "Weak"), state.filteredNetworks.map { it.displaySsid })
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `deselecting resumes live sort order`() = runTest(testDispatcher) {
            val weak = stubAp(ssid = "Weak", bssid = "AA:BB:CC:DD:EE:01", rssi = -80)
            val strong = stubAp(ssid = "Strong", bssid = "AA:BB:CC:DD:EE:02", rssi = -40)
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(weak, strong)
            viewModel.startScan()
            runCurrent()
            viewModel.selectAccessPoint(weak)

            val weakNowStrong = weak.copy(rssi = -30)
            val strongNowWeak = strong.copy(rssi = -90)
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(weakNowStrong, strongNowWeak)
            viewModel.startScan(silent = true)
            runCurrent()

            viewModel.selectAccessPoint(null)

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertEquals(listOf("Weak", "Strong"), state.filteredNetworks.map { it.displaySsid })
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `selected AP dropping out of range surfaces a message and clears selection`() = runTest(testDispatcher) {
            val ap = stubAp(ssid = "Gone", bssid = "AA:BB:CC:DD:EE:01")
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(ap)
            viewModel.startScan()
            runCurrent()
            viewModel.selectAccessPoint(ap)

            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult() // ap no longer present
            viewModel.startScan(silent = true)
            runCurrent()

            val state = viewModel.uiState.value as WifiScanUiState.Success
            assertNull(state.selectedAp)
            assertEquals(R.string.wifi_ap_disappeared, viewModel.apDisappearedEvent.value?.messageResId)
            viewModel.stopAutoRefresh()
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
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())

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

        @Test
        fun `scan completing after lifecycle pause does not restart refresh`() = runTest(testDispatcher) {
            val original = stubResult(stubAp())
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns original
            viewModel.startScan()
            runCurrent()
            viewModel.stopAutoRefresh()
            val previous = viewModel.uiState.value as WifiScanUiState.Success

            val pendingResult = CompletableDeferred<WifiScanResult>()
            val resourcesClosed = CompletableDeferred<Unit>()
            var operationSession: OperationSession? = null
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } coAnswers {
                operationSession = secondArg()
                operationSession!!.resources.register(AutoCloseable { resourcesClosed.complete(Unit) })
                withContext(NonCancellable) { pendingResult.await() }
            }

            viewModel.startScan(silent = true)
            runCurrent()
            viewModel.onLifecyclePause()
            runCurrent()
            resourcesClosed.await()
            assertEquals(previous, viewModel.uiState.value)
            pendingResult.complete(stubResult(stubAp()))
            runCurrent()

            assertEquals(previous, viewModel.uiState.value)
            assertTrue(!viewModel.autoRefresh.value)
            assertEquals(CancellationReason.LIFECYCLE_PAUSE, operationSession?.cancellationReason)
            advanceTimeBy(WifiScanViewModel.DEFAULT_REFRESH_INTERVAL_MS * 2)
            runCurrent()
            coVerify(exactly = 1) { wifiScanUseCase(trigger = true, operationSession = any()) }
        }

        @Test
        fun `changing refresh interval while paused does not restart refresh`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())
            viewModel.startScan()
            runCurrent()
            assertTrue(viewModel.autoRefresh.value)

            viewModel.onLifecyclePause()
            viewModel.setRefreshInterval(15_000L)
            runCurrent()

            assertTrue(!viewModel.autoRefresh.value)
            advanceTimeBy(30_000L)
            runCurrent()
            coVerify(exactly = 1) { wifiScanUseCase(trigger = true, operationSession = any()) }
        }

        @Test
        fun `manual refresh off remains off after lifecycle pause and resume`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())
            viewModel.startScan()
            runCurrent()
            assertTrue(viewModel.autoRefresh.value)

            viewModel.toggleAutoRefresh()
            assertTrue(!viewModel.autoRefresh.value)
            viewModel.onLifecyclePause()
            viewModel.onLifecycleResume()
            assertTrue(!viewModel.autoRefresh.value)
            advanceTimeBy(WifiScanViewModel.DEFAULT_REFRESH_INTERVAL_MS * 2)
            runCurrent()
            coVerify(exactly = 1) { wifiScanUseCase(trigger = true, operationSession = any()) }

            // Off remains explicit until the user selects a real interval again.
            viewModel.setRefreshInterval(15_000L)
            runCurrent()
            assertTrue(viewModel.autoRefresh.value)
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `enabled refresh resumes after lifecycle pause and triggers one scan`() = runTest(testDispatcher) {
            coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returns stubResult(stubAp())
            viewModel.startScan()
            runCurrent()

            assertTrue(viewModel.autoRefresh.value)
            coVerify(exactly = 1) { wifiScanUseCase(trigger = true, operationSession = any()) }

            viewModel.onLifecyclePause()
            assertTrue(!viewModel.autoRefresh.value)
            viewModel.onLifecycleResume()
            runCurrent()
            advanceTimeBy(WifiScanViewModel.DEFAULT_REFRESH_INTERVAL_MS)
            runCurrent()

            assertTrue(viewModel.autoRefresh.value)
            coVerify(exactly = 2) { wifiScanUseCase(trigger = true, operationSession = any()) }
            viewModel.stopAutoRefresh()
        }

        @Test
        fun `first scan paused by lifecycle shows retry state and ignores late result`() = runTest(testDispatcher) {
            val pendingResult = CompletableDeferred<WifiScanResult>()
            val resourcesClosed = CompletableDeferred<Unit>()
            var operationSession: OperationSession? = null
            coEvery {
                wifiScanUseCase(trigger = true, operationSession = any())
            } coAnswers {
                operationSession = secondArg()
                operationSession!!.resources.register(AutoCloseable { resourcesClosed.complete(Unit) })
                withContext(NonCancellable) { pendingResult.await() }
            }

            viewModel.startScan()
            runCurrent()
            viewModel.onLifecyclePause()
            runCurrent()
            resourcesClosed.await()
            assertTrue(viewModel.uiState.value is WifiScanUiState.Paused)
            assertEquals(CancellationReason.LIFECYCLE_PAUSE, operationSession?.cancellationReason)

            pendingResult.complete(stubResult(stubAp()))
            runCurrent()
            assertTrue(viewModel.uiState.value is WifiScanUiState.Paused)
            assertTrue(!viewModel.autoRefresh.value)
        }
    }

    @Test
    fun `onRetry resets to Idle`() = runTest(testDispatcher) {
        coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } throws RuntimeException("boom")
        viewModel.startScan()
        runCurrent()

        viewModel.onRetry()

        assertTrue(viewModel.uiState.value is WifiScanUiState.Idle)
    }

    @Test
    fun `setRefreshInterval persists selected value and supports Off`() = runTest(testDispatcher) {
        assertEquals(30_000L, viewModel.refreshIntervalMs.value)

        viewModel.setRefreshInterval(15_000L)
        runCurrent()
        assertEquals(15_000L, prefsFlow.value[AppPreferenceKeys.WIFI_REFRESH_INTERVAL_MS])
        assertEquals(15_000L, viewModel.refreshIntervalMs.value)

        viewModel.setRefreshInterval(null)
        runCurrent()
        assertEquals(-1L, prefsFlow.value[AppPreferenceKeys.WIFI_REFRESH_INTERVAL_MS])
        assertEquals(null, viewModel.refreshIntervalMs.value)
    }

    @Test
    fun `onRetry from LocationDisabled starts another scan`() = runTest(testDispatcher) {
        coEvery { wifiScanUseCase(trigger = true, operationSession = any()) } returnsMany listOf(
            stubResult(locationEnabled = false),
            stubResult(stubAp())
        )

        viewModel.startScan()
        runCurrent()
        assertTrue(viewModel.uiState.value is WifiScanUiState.LocationDisabled)

        viewModel.onRetry()
        runCurrent()

        assertTrue(viewModel.uiState.value is WifiScanUiState.Success)
        coVerify(exactly = 2) { wifiScanUseCase(trigger = true, operationSession = any()) }
        viewModel.stopAutoRefresh()
    }
}
