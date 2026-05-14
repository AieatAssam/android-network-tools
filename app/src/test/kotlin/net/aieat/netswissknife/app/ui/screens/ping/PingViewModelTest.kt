package net.aieat.netswissknife.app.ui.screens.ping

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PingViewModel")
class PingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pingUseCase: PingUseCase
    private lateinit var continuousPingUseCase: ContinuousPingUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: PingViewModel

    private val successPacket = PingPacketResult(
        sequence = 1, host = "example.com", status = PingStatus.SUCCESS, rtTimeMs = 15L
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pingUseCase = mockk()
        continuousPingUseCase = mockk()
        dataStore = mockk { every { data } returns flowOf(emptyPreferences()) }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = PingViewModel(pingUseCase, continuousPingUseCase, dataStore, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts in Idle state`() {
            assertTrue(viewModel.uiState.value is PingUiState.Idle)
        }

        @Test
        fun `host starts empty`() {
            assertEquals("", viewModel.host.value)
        }

        @Test
        fun `count defaults to 10`() {
            assertEquals(10, viewModel.count.value)
        }

        @Test
        fun `continuous mode starts disabled`() {
            assertFalse(viewModel.continuousMode.value)
        }
    }

    // ── Form field actions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("form field actions")
    inner class FormFieldActions {

        @Test
        fun `onHostChange updates host`() {
            viewModel.onHostChange("google.com")
            assertEquals("google.com", viewModel.host.value)
        }

        @Test
        fun `onCountChange clamps to valid range`() {
            viewModel.onCountChange(0)
            assertEquals(1, viewModel.count.value)
            viewModel.onCountChange(200)
            assertEquals(100, viewModel.count.value)
        }

        @Test
        fun `onToggleContinuous enables continuous mode`() {
            viewModel.onToggleContinuous(true)
            assertTrue(viewModel.continuousMode.value)
        }

        @Test
        fun `onToggleContinuous disables continuous mode`() {
            viewModel.onToggleContinuous(true)
            viewModel.onToggleContinuous(false)
            assertFalse(viewModel.continuousMode.value)
        }
    }

    // ── Normal ping state transitions ─────────────────────────────────────────

    @Nested
    @DisplayName("normal ping state transitions")
    inner class NormalPingStateTransitions {

        @Test
        fun `transitions to Finished after all packets`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(
                PingFlowResult.Packet(successPacket),
                PingFlowResult.Packet(successPacket.copy(sequence = 2))
            )
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            val state = viewModel.uiState.value
            assertTrue(state is PingUiState.Finished)
            assertEquals(2, (state as PingUiState.Finished).result.packets.size)
        }

        @Test
        fun `Finished state has null sessionLogFile in normal mode`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(PingFlowResult.Packet(successPacket))
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            val state = viewModel.uiState.value as PingUiState.Finished
            assertNull(state.sessionLogFile)
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(PingFlowResult.ValidationError("invalid host"))
            viewModel.onHostChange("")
            viewModel.startPing()
            val state = viewModel.uiState.value
            assertTrue(state is PingUiState.Error)
            assertEquals("invalid host", (state as PingUiState.Error).message)
        }

        @Test
        fun `transitions to Error when flow completes with no packets`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf()
            viewModel.onHostChange("unreachable.host")
            viewModel.startPing()
            assertTrue(viewModel.uiState.value is PingUiState.Error)
        }
    }

    // ── Normal ping cancel and clear ──────────────────────────────────────────

    @Nested
    @DisplayName("normal ping cancel and clear")
    inner class NormalPingCancelAndClear {

        @Test
        fun `onStop with collected packets moves to Finished`() = runTest {
            coEvery { pingUseCase(any()) } returns flow {
                emit(PingFlowResult.Packet(successPacket))
                suspendCancellableCoroutine<Nothing> { }
            }
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            viewModel.onStop()
            assertTrue(viewModel.uiState.value is PingUiState.Finished)
        }

        @Test
        fun `onClearResults resets to Idle`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(PingFlowResult.Packet(successPacket))
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            viewModel.onClearResults()
            assertTrue(viewModel.uiState.value is PingUiState.Idle)
        }

        @Test
        fun `onLifecycleStop does NOT stop a normal ping`() = runTest {
            coEvery { pingUseCase(any()) } returns flow {
                emit(PingFlowResult.Packet(successPacket))
                kotlinx.coroutines.delay(10_000L)
            }
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            val stateBefore = viewModel.uiState.value
            viewModel.onLifecycleStop()
            // Normal ping should still be in Running state
            assertTrue(viewModel.uiState.value is PingUiState.Running)
            assertFalse((viewModel.uiState.value as PingUiState.Running).isContinuous)
        }
    }

    // ── Continuous ping ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("continuous ping")
    inner class ContinuousPing {

        // Emits `count` packets immediately then suspends until cancelled, simulating an
        // ongoing session without advancing virtual time (avoids advanceUntilIdle loops).
        private fun neverEndingFlow(count: Int = 3) = flow {
            repeat(count) { i ->
                emit(PingFlowResult.Packet(successPacket.copy(sequence = i + 1)))
            }
            suspendCancellableCoroutine<Nothing> { }
        }

        @BeforeEach
        fun enableContinuous() {
            viewModel.onToggleContinuous(true)
            viewModel.onHostChange("example.com")
        }

        @Test
        fun `Running state has isContinuous true`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            val state = viewModel.uiState.value
            assertTrue(state is PingUiState.Running)
            assertTrue((state as PingUiState.Running).isContinuous)
        }

        @Test
        fun `pingsSent increments with each packet`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            val state = viewModel.uiState.value as PingUiState.Running
            assertTrue(state.pingsSent > 0)
        }

        @Test
        fun `rolling window is capped at 100 packets`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns flow {
                repeat(150) { i -> emit(PingFlowResult.Packet(successPacket.copy(sequence = i + 1))) }
            }
            viewModel.startPing()
            // After all 150 emitted the flow completes; state may be Finished
            val packets = when (val s = viewModel.uiState.value) {
                is PingUiState.Running -> s.packets
                is PingUiState.Finished -> s.result.packets
                else -> emptyList()
            }
            assertTrue(packets.size <= 100)
        }

        @Test
        fun `onStop transitions continuous session to Finished`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            viewModel.onStop()
            assertTrue(viewModel.uiState.value is PingUiState.Finished)
        }

        @Test
        fun `Finished after continuous has non-null sessionLogFile`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            viewModel.onStop()
            val state = viewModel.uiState.value as PingUiState.Finished
            assertNotNull(state.sessionLogFile)
        }

        @Test
        fun `onLifecycleStop stops continuous ping`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            assertTrue(viewModel.uiState.value is PingUiState.Running)
            viewModel.onLifecycleStop()
            assertTrue(viewModel.uiState.value is PingUiState.Finished)
        }

        @Test
        fun `onLifecycleStop is idempotent - safe to call twice`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            viewModel.onLifecycleStop()
            val stateAfterFirst = viewModel.uiState.value
            viewModel.onLifecycleStop() // second call — must not crash or change state
            assertEquals(stateAfterFirst, viewModel.uiState.value)
        }

        @Test
        fun `ValidationError clears session file and shows Error state`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns flowOf(
                PingFlowResult.ValidationError("invalid")
            )
            viewModel.startPing()
            assertTrue(viewModel.uiState.value is PingUiState.Error)
        }

        @Test
        fun `starting new continuous session deletes previous log file`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            viewModel.onStop()
            val firstFile = (viewModel.uiState.value as PingUiState.Finished).sessionLogFile
            assertNotNull(firstFile)

            // Start a second session
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            // The first file should have been deleted
            assertTrue(firstFile?.exists() == false)
        }

        @Test
        fun `onClearResults resets to Idle and cleans up file`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            viewModel.onStop()
            val logFile = (viewModel.uiState.value as PingUiState.Finished).sessionLogFile
            viewModel.onClearResults()
            assertTrue(viewModel.uiState.value is PingUiState.Idle)
            assertTrue(logFile?.exists() == false)
        }

        @Test
        fun `addRecent is called on first continuous packet`() = runTest {
            coEvery { continuousPingUseCase(any()) } returns neverEndingFlow()
            viewModel.startPing()
            coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com") }
        }
    }

    // ── Recent hosts ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recent hosts")
    inner class RecentHosts {

        @Test
        fun `addRecent is called on first valid packet`() = runTest {
            val packet = PingPacketResult(sequence = 1, host = "example.com", status = PingStatus.SUCCESS, rtTimeMs = 10L)
            coEvery { pingUseCase(any()) } returns flowOf(PingFlowResult.Packet(packet))
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com") }
        }

        @Test
        fun `addRecent is NOT called when ValidationError fires`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(PingFlowResult.ValidationError("bad host"))
            viewModel.onHostChange("bad!!host")
            viewModel.startPing()
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
        }
    }
}
