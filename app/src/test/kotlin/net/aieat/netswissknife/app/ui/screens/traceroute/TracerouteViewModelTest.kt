package net.aieat.netswissknife.app.ui.screens.traceroute

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TracerouteViewModel")
class TracerouteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var tracerouteUseCase: TracerouteUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: TracerouteViewModel
    private var networkAvailable = true

    private val stubHop = HopResult(
        hopNumber = 1,
        ip = "10.0.0.1",
        hostname = "gateway",
        status = HopStatus.SUCCESS,
        rtTimeMs = 2L
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracerouteUseCase = mockk()
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = TracerouteViewModel(
            tracerouteUseCase,
            recentHostsRepository,
            LinkInfoProvider { networkAvailable },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.uiState.value is TracerouteUiState.Idle)
    }

    @Nested
    @DisplayName("startTrace state transitions")
    inner class StartTraceStateTransitions {

        @Test
        fun `transitions through Running to Finished`() = runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(
                TracerouteFlowResult.Hop(stubHop)
            )
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            val state = viewModel.uiState.value
            assertTrue(state is TracerouteUiState.Finished, "Expected Finished but was $state")
        }

        @Test
        fun `transitions to Error on ValidationError`() = runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(
                TracerouteFlowResult.ValidationError("empty host")
            )
            viewModel.onHostChange("")
            viewModel.startTrace()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Error)
        }

        @Test
        fun `transitions to Error when no hops received`() = runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf()
            viewModel.onHostChange("unreachable")
            viewModel.startTrace()
            assertEquals(
                TracerouteUiState.Error("No route found to unreachable"),
                viewModel.uiState.value
            )
        }

        @Test
        fun `offline trace fails before invoking the probe`() = runTest {
            networkAvailable = false
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            assertEquals(TracerouteUiState.Error("No network connection"), viewModel.uiState.value)
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
            io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
        }

        @Test
        fun `probe exception is exposed as an error`() = runTest {
            every { tracerouteUseCase(any(), any()) } throws IllegalStateException("route socket closed")
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            assertEquals(TracerouteUiState.Error("route socket closed"), viewModel.uiState.value)
        }

        @Test
        fun `accumulates hops in Finished result`() = runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(
                TracerouteFlowResult.Hop(stubHop),
                TracerouteFlowResult.Hop(stubHop.copy(hopNumber = 2, ip = "8.8.8.8"))
            )
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            val state = viewModel.uiState.value as TracerouteUiState.Finished
            assertEquals(2, state.result.hops.size)
        }
    }

    @Nested
    @DisplayName("cancel and clear")
    inner class CancelAndClear {

        @Test
        fun `onStop with hops transitions to Finished`() = runTest {
            val stopped = CountDownLatch(1)
            every { tracerouteUseCase(any(), any()) } returns flow {
                try {
                    emit(TracerouteFlowResult.Hop(stubHop))
                    kotlinx.coroutines.delay(10_000L)
                } finally {
                    stopped.countDown()
                }
            }
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            // Hop was emitted synchronously by UnconfinedTestDispatcher
            viewModel.onStop()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Finished ||
                       viewModel.uiState.value is TracerouteUiState.Idle)
            assertTrue(withContext(Dispatchers.IO) { stopped.await(2, TimeUnit.SECONDS) })
        }

        @Test
        fun `offline restart invalidates prior emissions before returning`() = runTest {
            val firstChannel = Channel<TracerouteFlowResult>(Channel.UNLIMITED)
            val firstCollectorCancelled = CountDownLatch(1)
            every { tracerouteUseCase(any(), any()) } returnsMany listOf(
                firstChannel.receiveAsFlow().onCompletion { firstCollectorCancelled.countDown() },
                flowOf(),
            )
            viewModel.onHostChange("example.com")
            viewModel.startTrace()

            networkAvailable = false
            viewModel.startTrace()
            firstChannel.trySend(TracerouteFlowResult.Hop(stubHop.copy(ip = "203.0.113.1")))
            runCurrent()
            assertEquals(TracerouteUiState.Error("No network connection"), viewModel.uiState.value)
            assertTrue(withContext(Dispatchers.IO) { firstCollectorCancelled.await(2, TimeUnit.SECONDS) })
        }

        @Test
        fun `onClear resets to Idle`() = runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            viewModel.onClear()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Idle)
        }
    }

    @Test
    fun `addRecent is called on startTrace`() = runTest {
        every { tracerouteUseCase(any(), any()) } returns flowOf()
        viewModel.onHostChange("example.com")
        viewModel.startTrace()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, "example.com") }
    }

    @Test
    fun `startTrace normalizes host before saving and probing`() = runTest {
        every { tracerouteUseCase(any(), any()) } returns flowOf()
        viewModel.onHostChange("  Example.COM. ")

        viewModel.startTrace()

        coVerify {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, "example.com")
        }
        io.mockk.verify { tracerouteUseCase(match { it.host == "example.com" }, any()) }
    }

    @Test
    fun `invalid host is not saved to recents`() = runTest {
        every { tracerouteUseCase(any(), any()) } returns flowOf(
            TracerouteFlowResult.ValidationError("Invalid host or IP address")
        )
        viewModel.onHostChange("bad host")

        viewModel.startTrace()

        coVerify(exactly = 0) {
            recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, any())
        }
        assertTrue(viewModel.uiState.value is TracerouteUiState.Error)
    }

    @Test
    fun `Finished result has non-null rawOutput`() = runTest {
        every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
        viewModel.onHostChange("example.com")
        viewModel.startTrace()
        val state = viewModel.uiState.value as TracerouteUiState.Finished
        assertNotNull(state.result.rawOutput)
    }
}
