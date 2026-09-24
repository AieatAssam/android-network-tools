package net.aieat.netswissknife.app.ui.screens.traceroute

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.app.traceroute.IcmpEnginTracerouteRepositoryImpl
import net.aieat.netswissknife.app.traceroute.TracerouteReverseDnsLookup
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
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
    private val networkStatus = MutableStateFlow(NetworkStatus(hasInternet = true, hasLocalNetwork = true))

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
            object : NetworkStatusProvider { override val status = networkStatus },
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
            networkStatus.value = NetworkStatus()
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            assertEquals(TracerouteUiState.Error("No network connection"), viewModel.uiState.value)
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
            io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
        }

        @Test
        fun `local network without validated internet can start a trace`() = runTest {
            networkStatus.value = NetworkStatus(hasInternet = false, hasLocalNetwork = true)
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("192.168.1.1")

            viewModel.startTrace()

            assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
            io.mockk.verify(exactly = 1) { tracerouteUseCase(match { it.host == "192.168.1.1" }, any()) }
        }

        @Test
        fun `request over hard ceiling is rejected before invoking use case or recording host`() = runTest {
            viewModel.onHostChange("example.com")
            viewModel.onMaxHopsChange(64)
            viewModel.onTimeoutChange(20_000)

            viewModel.startTrace()

            assertEquals(
                TracerouteUiState.Error(
                    "Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout",
                ),
                viewModel.uiState.value,
            )
            io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
            coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
        }

        @Test
        fun `vpn only connectivity can start a trace`() = runTest {
            networkStatus.value = NetworkStatus(hasInternet = false, hasLocalNetwork = false, vpnActive = true)
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("internal.example")

            viewModel.startTrace()

            assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
            io.mockk.verify(exactly = 1) { tracerouteUseCase(match { it.host == "internal.example" }, any()) }
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
        fun `onStop holds Canceling until cleanup finishes then keeps partial hops`() = runTest {
            val cleanupStarted = CountDownLatch(1)
            val allowCleanupToFinish = CountDownLatch(1)
            val cleanupFinished = CountDownLatch(1)
            every { tracerouteUseCase(any(), any()) } answers {
                val session = secondArg<OperationSession>()
                flow {
                    session.resources.register(AutoCloseable {
                        cleanupStarted.countDown()
                        check(allowCleanupToFinish.await(5, TimeUnit.SECONDS))
                        cleanupFinished.countDown()
                    })
                    emit(TracerouteFlowResult.Hop(stubHop))
                    awaitCancellation()
                }
            }
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            assertTrue((viewModel.uiState.value as TracerouteUiState.Running).hops.contains(stubHop))

            viewModel.onStop()
            val canceling = viewModel.uiState.value as TracerouteUiState.Canceling
            assertEquals(listOf(stubHop), canceling.hops)
            assertTrue(withContext(Dispatchers.IO) { cleanupStarted.await(2, TimeUnit.SECONDS) })

            viewModel.startTrace()
            viewModel.onClear()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Canceling)
            io.mockk.verify(exactly = 1) { tracerouteUseCase(any(), any()) }

            allowCleanupToFinish.countDown()
            assertTrue(withContext(Dispatchers.IO) { cleanupFinished.await(2, TimeUnit.SECONDS) })
            val canceled = viewModel.uiState.first { it is TracerouteUiState.Canceled }
                as TracerouteUiState.Canceled
            assertEquals(listOf(stubHop), canceled.result.hops)
        }

        @Test
        fun `duplicate start during trace does not replace active operation`() = runTest {
            val firstChannel = Channel<TracerouteFlowResult>(Channel.UNLIMITED)
            val firstCollectorCancelled = CountDownLatch(1)
            every { tracerouteUseCase(any(), any()) } returnsMany listOf(
                firstChannel.receiveAsFlow().onCompletion { firstCollectorCancelled.countDown() },
                flowOf(),
            )
            viewModel.onHostChange("example.com")
            viewModel.startTrace()

            networkStatus.value = NetworkStatus()
            viewModel.startTrace()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Running)
            io.mockk.verify(exactly = 1) { tracerouteUseCase(any(), any()) }

            viewModel.onStop()
            assertTrue(withContext(Dispatchers.IO) { firstCollectorCancelled.await(2, TimeUnit.SECONDS) })
            val canceled = viewModel.uiState.first { it is TracerouteUiState.Canceled }
                as TracerouteUiState.Canceled
            assertEquals("example.com", canceled.result.host)
            assertTrue(canceled.result.hops.isEmpty())
        }

        @Test
        fun `deadline keeps partial hops and reports the time limit`() = runTest {
            var session: OperationSession? = null
            every { tracerouteUseCase(any(), any()) } answers {
                session = secondArg()
                flow {
                    emit(TracerouteFlowResult.Hop(stubHop))
                    awaitCancellation()
                }
            }
            viewModel.onHostChange("example.com")
            viewModel.startTrace()

            checkNotNull(session).cancel(CancellationReason.DEADLINE_EXCEEDED)
            viewModel.onStop()

            val partial = viewModel.uiState.first { it is TracerouteUiState.Finished }
                as TracerouteUiState.Finished
            assertTrue(partial.timeLimitReached)
            assertEquals(listOf(stubHop), partial.result.hops)
        }

        @Test
        fun `repository deadline after first native hop is retained as partial in ViewModel`() = runTest {
            val repository = IcmpEnginTracerouteRepositoryImpl(
                nativeTraceFactory = { _, _, _, _, _, _, _ ->
                    flow {
                        emit(stubHop)
                        // The second hop never arrives; the repository's real deadline runner wins.
                        awaitCancellation()
                    }
                },
                reverseDnsLookup = TracerouteReverseDnsLookup { _, _ -> "gateway" },
            )
            every { tracerouteUseCase(any(), any()) } answers {
                val params = firstArg<TracerouteParams>()
                val session = secondArg<OperationSession>()
                repository.trace(
                    host = params.host,
                    maxHops = params.maxHops,
                    timeoutMs = params.timeoutMs,
                    probesPerHop = params.probesPerHop,
                    probeType = params.probeType,
                    packetSize = params.packetSize,
                    operationSession = session,
                ).map { TracerouteFlowResult.Hop(it) }
            }
            viewModel.operationSessionFactory = {
                OperationSession(
                    OperationBudget.start(
                        timeoutMillis = 250,
                        maxConcurrentProbes = TracerouteOperation.MAX_CONCURRENT_PROBES,
                    ),
                )
            }
            viewModel.onHostChange("192.0.2.1")

            viewModel.startTrace()

            val running = viewModel.uiState.first {
                it is TracerouteUiState.Running && stubHop in it.hops
            } as TracerouteUiState.Running
            assertEquals(listOf(stubHop), running.hops)

            val partial = viewModel.uiState.first { it is TracerouteUiState.Finished }
                as TracerouteUiState.Finished
            assertTrue(partial.timeLimitReached)
            assertEquals(listOf(stubHop), partial.result.hops)
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
