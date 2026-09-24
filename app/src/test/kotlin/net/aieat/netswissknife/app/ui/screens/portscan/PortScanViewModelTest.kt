package net.aieat.netswissknife.app.ui.screens.portscan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.SavedStateHandle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.PortScanFlowResult
import net.aieat.netswissknife.core.domain.PortScanPreset
import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.portscan.PortStatus
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.CancellationReason
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
@DisplayName("PortScanViewModel")
class PortScanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var portScanUseCase: PortScanUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: PortScanViewModel
    private val testClock = FakeMonotonicClock()
    private lateinit var lastOperationSession: OperationSession

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
        every { portScanUseCase.newSession(any()) } answers {
            OperationSession(OperationBudget.start()).also { lastOperationSession = it }
        }
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
    fun `LAN typed handoff prefills host without scanning and preserves edits in saved state`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                ToolSource.LAN,
            ),
        )
        val routeState = SavedStateHandle(mapOf("intent" to encoded, "host" to "192.0.2.8"))
        val handoffViewModel = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = routeState,
            monotonicClock = testClock,
        )

        assertEquals("192.0.2.8", handoffViewModel.host.value)
        assertEquals(ToolSource.LAN, handoffViewModel.sourceContext)
        assertTrue(!handoffViewModel.hasInvalidHandoff.value)
        assertTrue(handoffViewModel.uiState.value is PortScanUiState.Idle)
        assertEquals(true, routeState.get<Boolean>("portsHandoffConsumed"))
        assertEquals("lan", routeState.get<String>("portsHandoffSource"))
        assertEquals("192.0.2.8", routeState.get<String>("editedHost"))
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }

        handoffViewModel.onHostChange("edited.example")
        assertEquals("edited.example", routeState.get<String>("editedHost"))
        assertEquals("192.0.2.8", routeState.get<String>("host"))
        val recreated = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf("intent" to encoded, "host" to "192.0.2.8", "editedHost" to "edited.example"),
            ),
            monotonicClock = testClock,
        )
        assertEquals("edited.example", recreated.host.value)
        assertEquals(ToolSource.LAN, recreated.sourceContext)
        assertTrue(!recreated.hasInvalidHandoff.value)
        assertTrue(recreated.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `consumed typed route snapshots initial host and never replays route after recreation`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                ToolSource.LAN,
            ),
        )
        val routeState = SavedStateHandle(mapOf("intent" to encoded, "host" to "192.0.2.8"))
        val first = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = routeState,
            monotonicClock = testClock,
        )
        assertEquals("192.0.2.8", first.host.value)
        assertEquals(true, routeState.get<Boolean>("portsHandoffConsumed"))
        assertEquals("192.0.2.8", routeState.get<String>("editedHost"))

        val recreated = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to encoded,
                    "host" to "192.0.2.8",
                    "portsHandoffConsumed" to true,
                    "portsHandoffSource" to "lan",
                    "editedHost" to "192.0.2.8",
                ),
            ),
            monotonicClock = testClock,
        )
        assertEquals("192.0.2.8", recreated.host.value)
        assertEquals(ToolSource.LAN, recreated.sourceContext)
        assertTrue(recreated.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `present empty edit does not fall back to route host while absent edit consumes prefill`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                ToolSource.LAN,
            ),
        )
        val route = mapOf("intent" to encoded, "host" to "192.0.2.8")

        val untouched = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(route),
            monotonicClock = testClock,
        )
        assertEquals("192.0.2.8", untouched.host.value)

        val explicitlyBlank = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(route + mapOf("editedHost" to "")),
            monotonicClock = testClock,
        )
        assertEquals("", explicitlyBlank.host.value)
        assertFalse(explicitlyBlank.hasInvalidHandoff.value)
        assertTrue(explicitlyBlank.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `cleared prefill and source remain cleared after recreation with original route arguments`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                ToolSource.LAN,
            ),
        )
        val originalRouteArgs = mapOf("intent" to encoded, "host" to "192.0.2.8")
        val routeState = SavedStateHandle(originalRouteArgs)
        val handoff = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = routeState,
            monotonicClock = testClock,
        )
        assertEquals("192.0.2.8", handoff.host.value)
        assertEquals(ToolSource.LAN, handoff.sourceContext)

        handoff.clearPrefill()

        assertEquals("", handoff.host.value)
        assertEquals(null, handoff.sourceContext)
        assertEquals("", routeState.get<String>("editedHost"))
        assertEquals(null, routeState.get<String>("portsHandoffSource"))
        assertEquals(true, routeState.get<Boolean>("portsHandoffConsumed"))

        val recreated = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                originalRouteArgs + mapOf(
                    "editedHost" to "",
                    "portsHandoffConsumed" to true,
                ),
            ),
            monotonicClock = testClock,
        )
        assertEquals("", recreated.host.value)
        assertEquals(null, recreated.sourceContext)
        assertTrue(recreated.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }

        // A consumed route with no editedHost snapshot is also authoritative; it
        // must not fall back to the still-present navigation argument.
        val absentEdit = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                originalRouteArgs + mapOf("portsHandoffConsumed" to true),
            ),
            monotonicClock = testClock,
        )
        assertEquals("", absentEdit.host.value)
        assertEquals(null, absentEdit.sourceContext)
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `legacy host route remains supported and its host survives recreation`() {
        val routeState = SavedStateHandle(mapOf("host" to "legacy.example"))
        val legacy = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = routeState,
            monotonicClock = testClock,
        )

        assertEquals("legacy.example", legacy.host.value)
        assertEquals(null, legacy.sourceContext)
        assertEquals(true, routeState.get<Boolean>("portsHandoffConsumed"))
        assertEquals("legacy.example", routeState.get<String>("editedHost"))

        val recreated = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "host" to "legacy.example",
                    "editedHost" to "legacy.example",
                    "portsHandoffConsumed" to true,
                ),
            ),
            monotonicClock = testClock,
        )
        assertEquals("legacy.example", recreated.host.value)
        assertEquals(null, recreated.sourceContext)
        assertTrue(recreated.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `clear prefill is ignored while a scan is active`() = runTest {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                ToolSource.LAN,
            ),
        )
        val handoff = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(mapOf("intent" to encoded, "host" to "192.0.2.8")),
            monotonicClock = testClock,
        )
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(PortScanFlowResult.Started("192.0.2.8", 1))
            awaitCancellation()
        }

        handoff.startScan()
        assertTrue(handoff.uiState.value is PortScanUiState.Scanning)

        // The screen's clear icon calls onHostChange("") directly.
        handoff.onHostChange("")
        handoff.clearPrefill()

        assertEquals("192.0.2.8", handoff.host.value)
        assertEquals(ToolSource.LAN, handoff.sourceContext)
        assertTrue(handoff.uiState.value is PortScanUiState.Scanning)
        verify(exactly = 1) { portScanUseCase.newSession(any()) }
        handoff.onStopScan()
    }

    @Test
    fun `mismatched typed host and route host leave blank form and expose inline error`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("router-a.local"))),
                ToolSource.LAN,
            ),
        )
        val mismatched = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(mapOf("intent" to encoded, "host" to "router-b.local")),
            monotonicClock = testClock,
        )

        assertEquals("", mismatched.host.value)
        assertEquals(null, mismatched.sourceContext)
        assertTrue(mismatched.hasInvalidHandoff.value)
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `malformed typed handoff does not silently use legacy host`() {
        val savedState = SavedStateHandle(
            mapOf(
                "intent" to "tool-intent-v1.invalid",
                "host" to "router.local",
                "portsHandoffSource" to "lan",
            ),
        )
        val invalid = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = savedState,
            monotonicClock = testClock,
        )

        assertEquals("", invalid.host.value)
        assertEquals(null, invalid.sourceContext)
        assertTrue(invalid.hasInvalidHandoff.value)
        assertEquals(null, savedState.get<String>("portsHandoffSource"))

        val staleConsumedSource = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to "tool-intent-v1.invalid",
                    "host" to "router.local",
                    "portsHandoffConsumed" to true,
                    "portsHandoffSource" to "lan",
                    "editedHost" to "",
                ),
            ),
            monotonicClock = testClock,
        )
        assertEquals(null, staleConsumedSource.sourceContext)
        assertTrue(staleConsumedSource.hasInvalidHandoff.value)
    }

    @Test
    fun `valid recovery edit and dismissed warning survive route recreation`() {
        val encoded = ToolIntentCodec.encode(
            ToolIntent(
                ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("router-a.local"))),
                ToolSource.LAN,
            ),
        )
        val routeState = SavedStateHandle(mapOf("intent" to encoded, "host" to "router-b.local"))
        val recovery = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = routeState,
            monotonicClock = testClock,
        )
        assertTrue(recovery.hasInvalidHandoff.value)

        recovery.onHostChange("bad host")
        assertEquals("bad host", routeState.get<String>("editedHost"))
        val invalidEditRestored = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf("intent" to encoded, "host" to "router-b.local", "editedHost" to "bad host"),
            ),
            monotonicClock = testClock,
        )
        assertEquals("bad host", invalidEditRestored.host.value)
        assertTrue(invalidEditRestored.hasInvalidHandoff.value)

        recovery.onHostChange("replacement.example")
        assertFalse(recovery.hasInvalidHandoff.value)
        assertEquals(true, routeState.get<Boolean>("handoffRecovered"))
        val recreated = PortScanViewModel(
            portScanUseCase,
            dataStore,
            recentHostsRepository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "intent" to encoded,
                    "host" to "router-b.local",
                    "editedHost" to "replacement.example",
                    "handoffRecovered" to true,
                ),
            ),
            monotonicClock = testClock,
        )
        assertEquals("replacement.example", recreated.host.value)
        assertFalse(recreated.hasInvalidHandoff.value)
        assertTrue(recreated.uiState.value is PortScanUiState.Idle)
        verify(exactly = 0) { portScanUseCase(any()) }
        verify(exactly = 0) { portScanUseCase(any(), any()) }
        verify(exactly = 0) { portScanUseCase.newSession(any()) }
    }

    @Test
    fun `late completion and failure from replaced scan cannot overwrite current result`() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldFinished = CompletableDeferred<Unit>()
        val oldSummary = stubSummary.copy(host = "old.example")
        val newSummary = stubSummary.copy(host = "new.example")
        every { portScanUseCase(match { it.host == "old.example" }, any()) } returns
            object : Flow<PortScanFlowResult> {
                override suspend fun collect(collector: FlowCollector<PortScanFlowResult>) {
                    collector.emit(PortScanFlowResult.Started("192.0.2.1", 1))
                    oldStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        withContext(NonCancellable) {
                            try {
                                releaseOld.await()
                                collector.emit(PortScanFlowResult.ScanComplete(oldSummary))
                                throw IllegalStateException("late old scan failure")
                            } finally {
                                oldFinished.complete(Unit)
                            }
                        }
                    }
                }
            }
        every { portScanUseCase(match { it.host == "new.example" }, any()) } returns flowOf(
            PortScanFlowResult.Started("192.0.2.2", 1),
            PortScanFlowResult.ScanComplete(newSummary),
        )

        viewModel.onHostChange("old.example")
        viewModel.startScan()
        withContext(Dispatchers.Default) { kotlinx.coroutines.withTimeout(2_000) { oldStarted.await() } }

        viewModel.onHostChange("new.example")
        viewModel.startScan()
        withContext(Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(2_000) { viewModel.uiState.first { it is PortScanUiState.Finished } }
        }
        releaseOld.complete(Unit)
        withContext(Dispatchers.Default) { kotlinx.coroutines.withTimeout(2_000) { oldFinished.await() } }

        assertEquals(newSummary, (viewModel.uiState.value as PortScanUiState.Finished).summary)
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
            every { portScanUseCase(any(), any()) } returns flowOf(
                PortScanFlowResult.ScanComplete(stubSummary)
            )
            viewModel.onHostChange("example.com")
            viewModel.startScan()
            assertTrue(viewModel.uiState.value is PortScanUiState.Finished)
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            every { portScanUseCase(any(), any()) } returns flowOf(
                PortScanFlowResult.ValidationError("invalid host")
            )
            viewModel.onHostChange("bad##host")
            viewModel.startScan()
            val state = viewModel.uiState.value
            assertTrue(state is PortScanUiState.Error)
        }

        @Test
        fun `an exception during the scan flow surfaces as Error instead of crashing`() = runTest {
            every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
                throw java.net.SocketException("network unreachable")
            }
            viewModel.onHostChange("example.com")
            viewModel.startScan()
            val state = viewModel.uiState.value
            assertTrue(state is PortScanUiState.Error)
        }

        @Test
        fun `accumulates port results during scan`() = runTest {
            every { portScanUseCase(any(), any()) } returns flowOf(
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
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.startScan()
        viewModel.onClear()
        assertTrue(viewModel.uiState.value is PortScanUiState.Idle)
    }

    @Test
    fun `manual stop records USER_STOP on caller session`() = runTest {
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(PortScanFlowResult.Started("127.0.0.1", 1))
            awaitCancellation()
        }
        viewModel.onHostChange("example.com")
        viewModel.startScan()

        viewModel.onStopScan()

        assertEquals(CancellationReason.USER_STOP, lastOperationSession.cancellationReason)
        assertTrue(viewModel.uiState.value is PortScanUiState.Finished)
    }

    @Test
    fun `lifecycle pause records LIFECYCLE_PAUSE on caller session`() = runTest {
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(PortScanFlowResult.Started("127.0.0.1", 1))
            awaitCancellation()
        }
        viewModel.onHostChange("example.com")
        viewModel.startScan()

        viewModel.onLifecyclePause()

        assertEquals(CancellationReason.LIFECYCLE_PAUSE, lastOperationSession.cancellationReason)
        assertTrue(viewModel.uiState.value is PortScanUiState.Finished)
    }

    @Test
    fun `addRecent is called on startScan`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf()
        viewModel.onHostChange("example.com")
        viewModel.startScan()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, "example.com") }
    }

    @Test
    fun `startScan normalizes host before probing and saving`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ValidationError("test"))
        viewModel.onHostChange("  Example.COM.  ")

        viewModel.startScan()

        assertEquals("example.com", viewModel.host.value)
        verify { portScanUseCase(match { it.host == "example.com" }, any()) }
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, "example.com") }
    }

    @Test
    fun `startScan passes concurrency maximum of 500 to scan params`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.onConcurrencyChange(500)

        viewModel.startScan()

        verify { portScanUseCase(match { it.concurrency == 500 }, any()) }
    }

    @Test
    fun `stored concurrency above the supported range is clamped before scan`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
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
        verify { portScanUseCase(match { it.concurrency == 500 }, any()) }
    }

    @Test
    fun `concurrency changes are clamped to the supported range`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ScanComplete(stubSummary))
        viewModel.onHostChange("example.com")
        viewModel.onConcurrencyChange(-1)

        viewModel.startScan()

        assertEquals(1, viewModel.concurrency.value)
        verify { portScanUseCase(match { it.concurrency == 1 }, any()) }
    }

    @Test
    fun `invalid internal whitespace is not saved to recents`() = runTest {
        every { portScanUseCase(any(), any()) } returns flowOf(PortScanFlowResult.ValidationError("invalid host"))
        viewModel.onHostChange("bad host")

        viewModel.startScan()

        verify { portScanUseCase(match { it.host == "bad host" }, any()) }
        coVerify(exactly = 0) { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PORTS_HOSTS, any()) }
    }

    @Test
    fun `stopping a scan preserves resolved IP and measures duration through stop`() = runTest {
        testClock.nowNanos = 4_000_000_000L
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
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
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
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
        every { portScanUseCase(any(), any()) } returns kotlinx.coroutines.flow.flow {
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
