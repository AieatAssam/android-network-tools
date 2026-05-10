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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: PingViewModel

    private val successPacket = PingPacketResult(
        sequence = 1,
        host = "example.com",
        status = PingStatus.SUCCESS,
        rtTimeMs = 15L
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pingUseCase = mockk()
        dataStore = mockk {
            every { data } returns flowOf(emptyPreferences())
        }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = PingViewModel(pingUseCase, dataStore, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
    }

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
    }

    @Nested
    @DisplayName("startPing state transitions")
    inner class StartPingStateTransitions {

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
        fun `transitions to Error on ValidationError`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf(
                PingFlowResult.ValidationError("invalid host")
            )
            viewModel.onHostChange("")
            viewModel.startPing()
            val state = viewModel.uiState.value
            assertTrue(state is PingUiState.Error)
            assertEquals("invalid host", (state as PingUiState.Error).message)
        }

        @Test
        fun `stays Idle when flow completes with no packets`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf()
            viewModel.onHostChange("unreachable.host")
            viewModel.startPing()
            // Running state is never entered when flow emits nothing, so state stays Idle
            assertTrue(viewModel.uiState.value is PingUiState.Idle)
        }
    }

    @Nested
    @DisplayName("cancel and clear")
    inner class CancelAndClear {

        @Test
        fun `onStop with collected packets moves to Finished`() = runTest {
            coEvery { pingUseCase(any()) } returns flow {
                emit(PingFlowResult.Packet(successPacket))
                kotlinx.coroutines.delay(10_000L)
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
    }

    @Nested
    @DisplayName("recent hosts")
    inner class RecentHosts {

        @Test
        fun `addRecent is called when startPing is invoked`() = runTest {
            coEvery { pingUseCase(any()) } returns flowOf()
            viewModel.onHostChange("example.com")
            viewModel.startPing()
            coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com") }
        }
    }
}
