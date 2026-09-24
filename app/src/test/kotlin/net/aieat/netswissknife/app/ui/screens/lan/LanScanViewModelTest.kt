package net.aieat.netswissknife.app.ui.screens.lan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.domain.LanScanFlowResult
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.InternalCoroutinesApi::class)
@DisplayName("LanScanViewModel")
class LanScanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var lanScanUseCase: LanScanUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: LanScanViewModel

    private val stubHost = LanHost(
        ip = "192.168.1.1",
        hostname = "router",
        macAddress = null,
        vendor = null,
        openPorts = emptyList(),
        pingTimeMs = 5L
    )
    private val stubSummary = LanScanSummary(
        subnet = "192.168.1.0/24",
        totalScanned = 1,
        aliveHosts = 1,
        scanDurationMs = 100L,
        hosts = listOf(stubHost)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lanScanUseCase = mockk()
        dataStore = mockk {
            every { data } returns flowOf(emptyPreferences())
        }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = LanScanViewModel(lanScanUseCase, dataStore, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        // The scan pipeline hops through Dispatchers.IO; its final resumption lands on
        // Main. Cancel and join before resetMain() so no coroutine touches Main after
        // the test dispatcher is gone (flaky Looper IllegalStateException otherwise).
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts in Idle state`() = runTest {
            // uiState is set synchronously; just verify initial value
            assertTrue(viewModel.uiState.value is LanScanUiState.Idle)
        }
    }

    @Test
    fun `TLS handoff event includes selected host and discovered port`() = runTest {
        viewModel.onInspectTls("192.0.2.8", 8443)

        assertEquals(LanNavEvent.NavigateToTls("192.0.2.8", 8443), viewModel.navigationEvents.first())
    }

    @Test
    fun `HTTP handoff event includes selected host and chosen port`() = runTest {
        viewModel.onProbeHttp("192.0.2.8", 8080)

        assertEquals(LanNavEvent.NavigateToHttp("192.0.2.8", 8080), viewModel.navigationEvents.first())
    }

    @Test
    fun `HTTP handoff prefers a discovered conventional port and falls back to port 80`() {
        assertEquals(80, preferredHttpProbePort(listOf(443, 8080, 80)))
        assertEquals(8080, preferredHttpProbePort(listOf(443, 8080)))
        assertEquals(8000, preferredHttpProbePort(listOf(8888, 8000)))
        assertEquals(8888, preferredHttpProbePort(listOf(8888)))
        assertEquals(80, preferredHttpProbePort(listOf(443, 8443)))
        assertEquals(80, preferredHttpProbePort(emptyList()))
    }

    @Nested
    @DisplayName("startScan state transitions")
    inner class StartScanStateTransitions {

        @Test
        fun `transitions to Finished on ScanComplete`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            // withContext(Dispatchers.Default) uses real time so withTimeout works correctly
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            assertTrue(state is LanScanUiState.Finished, "Expected Finished but was $state")
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.ValidationError("invalid subnet")
            )
            viewModel.onSubnetChange("bad")
            viewModel.startScan()
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            assertTrue(state is LanScanUiState.Error)
            assertEquals("invalid subnet", (state as LanScanUiState.Error).message)
        }

        @Test
        fun `marks thrown local permission denial distinctly from generic scan errors`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flow {
                throw LocalNetworkPermissionDeniedException(SecurityException("denied"))
            }
            viewModel.startScan()
            val denied = withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Error } }
            } as LanScanUiState.Error
            assertEquals(NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED, denied.networkErrorKind)

            every { lanScanUseCase(any(), any()) } returns flow { throw IllegalStateException("timeout") }
            viewModel.startScan()
            val generic = withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Error } }
            } as LanScanUiState.Error
            assertEquals(NetworkErrorKind.GENERAL, generic.networkErrorKind)
        }

        @Test
        fun `accumulates hosts during scan`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 10),
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            val state = withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            } as LanScanUiState.Finished
            assertEquals(1, state.summary.hosts.size)
        }

        @Test
        fun `keeps confirmed and uncertain counts separate through a stopped scan`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flow {
                emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 10, uncertainCount = 1))
                emit(
                    LanScanFlowResult.ScanProgress(
                        scannedCount = 2,
                        totalCount = 10,
                        uncertainCount = 2,
                        diagnostic = LanScanDiagnostic(
                            ip = "192.168.1.3",
                            reason = LanScanDiagnosticReason.TCP_REFUSED,
                            port = 80,
                        ),
                    ),
                )
                awaitCancellation()
            }
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Scanning && it.scannedCount == 2 } }
            }

            viewModel.onStopScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Canceled } }
            }
            val state = viewModel.uiState.value as LanScanUiState.Canceled
            assertEquals(1, state.summary.aliveHosts)
            assertEquals(2, state.summary.uncertainCount)
            assertEquals(2, state.summary.totalScanned)
            assertEquals("192.168.1.3", state.summary.uncertainHosts.single().ip)
        }

        @Test
        fun `forwards an owned session and cancels it as user stop while preserving partial results`() = runTest {
            val sessionSlot = slot<OperationSession>()
            every { lanScanUseCase(any(), capture(sessionSlot)) } returns flow {
                emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 8))
                awaitCancellation()
            }
            viewModel.onSubnetChange("192.168.1.0/24")

            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    viewModel.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() }
                }
            }
            viewModel.onStopScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Canceled } }
            }

            assertEquals(CancellationReason.USER_STOP, sessionSlot.captured.cancellationReason)
            val partial = viewModel.uiState.value as LanScanUiState.Canceled
            assertEquals(listOf(stubHost), partial.summary.hosts)
            assertEquals(1, partial.summary.totalScanned)
        }

        @Test
        fun `shows Canceling until upstream resource cleanup finishes`() = runTest {
            val cleanupStarted = CompletableDeferred<Unit>()
            val allowCleanup = CompletableDeferred<Unit>()
            every { lanScanUseCase(any(), any()) } returns flow {
                emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 8))
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        allowCleanup.await()
                    }
                }
            }
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    viewModel.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() }
                }
            }

            viewModel.onStopScan()
            withContext(Dispatchers.Default) { withTimeout(2_000) { cleanupStarted.await() } }
            assertTrue(viewModel.uiState.value is LanScanUiState.Canceling)
            allowCleanup.complete(Unit)
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Canceled } }
            }
        }

        @Test
        fun `duplicate start while prior scan is active does not replace or cancel it`() = runTest {
            val sessionSlot = slot<OperationSession>()
            val collectorStarted = CompletableDeferred<Unit>()
            every { lanScanUseCase(any(), capture(sessionSlot)) } returns flow {
                collectorStarted.complete(Unit)
                awaitCancellation()
            }
            viewModel.startScan()
            withContext(Dispatchers.Default) { withTimeout(2_000) { collectorStarted.await() } }

            viewModel.startScan()

            verify(exactly = 1) { lanScanUseCase(any(), any()) }
            assertEquals(null, sessionSlot.captured.cancellationReason)
        }

        @Test
        fun `lifecycle pause finishes partial scan without user-canceled state`() = runTest {
            val sessionSlot = slot<OperationSession>()
            every { lanScanUseCase(any(), capture(sessionSlot)) } returns flow {
                emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 8))
                awaitCancellation()
            }
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() } }
            }

            viewModel.onLifecyclePause()

            assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
            val state = viewModel.uiState.value
            assertTrue(state is LanScanUiState.Canceling || state is LanScanUiState.Finished)
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            assertTrue(viewModel.uiState.value !is LanScanUiState.Canceled)
            val finished = viewModel.uiState.value as LanScanUiState.Finished
            assertTrue(finished.partial)
            assertEquals(listOf(stubHost), finished.summary.hosts)
        }

        @Test
        fun `late completion from cancelled scan cannot replace newer scan result`() = runTest {
            val oldCollectorStarted = CompletableDeferred<Unit>()
            val allowOldCompletion = CompletableDeferred<Unit>()
            val oldCollectorFinished = CompletableDeferred<Unit>()
            val oldSession = slot<OperationSession>()
            val oldSummary = stubSummary.copy(subnet = "192.168.1.0/24", aliveHosts = 0, hosts = emptyList())
            val newSummary = stubSummary.copy(subnet = "10.0.0.0/24")
            every { lanScanUseCase(match { it.subnet == "192.168.1.0/24" }, capture(oldSession)) } returns
                object : Flow<LanScanFlowResult> {
                    override suspend fun collect(collector: FlowCollector<LanScanFlowResult>) {
                        oldCollectorStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            withContext(NonCancellable) {
                                allowOldCompletion.await()
                                try {
                                    collector.emit(LanScanFlowResult.ScanComplete(oldSummary))
                                } finally {
                                    oldCollectorFinished.complete(Unit)
                                }
                            }
                        }
                    }
                }
            every { lanScanUseCase(match { it.subnet == "10.0.0.0/24" }, any()) } returns
                flowOf(LanScanFlowResult.ScanComplete(newSummary))

            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) { withTimeout(2_000) { oldCollectorStarted.await() } }
            viewModel.onStopScan()
            assertTrue(viewModel.uiState.value is LanScanUiState.Canceling)
            viewModel.startScan()
            assertTrue(viewModel.uiState.value is LanScanUiState.Canceling)
            allowOldCompletion.complete(Unit)
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Canceled } }
            }
            withContext(Dispatchers.Default) { withTimeout(2_000) { oldCollectorFinished.await() } }

            assertEquals(CancellationReason.USER_STOP, oldSession.captured.cancellationReason)
            viewModel.onSubnetChange("10.0.0.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            assertEquals(newSummary, (viewModel.uiState.value as LanScanUiState.Finished).summary)
        }
    }

    @Nested
    @DisplayName("stop and clear")
    inner class StopAndClear {

        @Test
        fun `onClear resets to Idle`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("192.168.1.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            viewModel.onClear()
            assertTrue(viewModel.uiState.value is LanScanUiState.Idle)
        }

        @Test
        fun `ViewModelStore clear cancels active operation as lifecycle pause`() = runTest {
            val sessionSlot = slot<OperationSession>()
            val collectorStarted = CompletableDeferred<Unit>()
            every { lanScanUseCase(any(), capture(sessionSlot)) } returns flow {
                collectorStarted.complete(Unit)
                awaitCancellation()
            }

            viewModel.startScan()
            withContext(Dispatchers.Default) { withTimeout(2_000) { collectorStarted.await() } }
            ViewModelStore().also { store ->
                store.put("lan", viewModel)
                store.clear()
            }

            assertTrue(viewModel.viewModelScope.coroutineContext.job.isCancelled)
            assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
        }

        @Test
        fun `foreground pause cancels operation and preserves partial result`() = runTest {
            val sessionSlot = slot<OperationSession>()
            every { lanScanUseCase(any(), capture(sessionSlot)) } returns flow {
                emit(LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 8))
                awaitCancellation()
            }

            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Scanning && it.hosts.isNotEmpty() } }
            }
            viewModel.onLifecyclePause()

            assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
            withContext(Dispatchers.Default) {
                withTimeout(2_000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            val partial = viewModel.uiState.value as LanScanUiState.Finished
            assertTrue(partial.partial)
            assertEquals(listOf(stubHost), partial.summary.hosts)
        }
    }

    @Nested
    @DisplayName("recent subnets")
    inner class RecentSubnets {

        @Test
        fun `addRecent is called on first host found`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.HostFound(stubHost, scannedCount = 1, totalCount = 1),
                LanScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onSubnetChange("10.0.0.0/24")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it is LanScanUiState.Finished } }
            }
            coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_LAN_SUBNETS, "10.0.0.0/24") }
        }

        @Test
        fun `addRecent is NOT called when ValidationError fires`() = runTest {
            every { lanScanUseCase(any(), any()) } returns flowOf(
                LanScanFlowResult.ValidationError("invalid subnet")
            )
            viewModel.onSubnetChange("bad")
            viewModel.startScan()
            withContext(Dispatchers.Default) {
                withTimeout(2000) { viewModel.uiState.first { it !is LanScanUiState.Scanning } }
            }
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
        }
    }
}
