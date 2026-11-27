package net.aieat.netswissknife.app.ui.screens.traceroute

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.impa.icmpenguin.trace.Response
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.Transport
import net.aieat.netswissknife.app.traceroute.IcmpEnginTracerouteRepositoryImpl
import net.aieat.netswissknife.app.traceroute.TracerouteHostResolutionTimeoutException
import net.aieat.netswissknife.app.traceroute.TracerouteHostResolver
import net.aieat.netswissknife.app.traceroute.mapNativeHop
import net.aieat.netswissknife.app.ui.i18n.ErrorTextMapper
import net.aieat.netswissknife.app.ui.i18n.UiText
import net.aieat.netswissknife.core.domain.TracerouteFlowResult
import net.aieat.netswissknife.core.domain.TracerouteParams
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteOperation
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import me.impa.icmpenguin.trace.HopStatus as NativeHopStatus

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TracerouteViewModel")
class TracerouteViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var tracerouteUseCase: TracerouteUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: TracerouteViewModel
    private val networkStatus = MutableStateFlow(NetworkStatus(hasInternet = true, hasLocalNetwork = true))
    private var localNetworkPermissionAllowed = true

    private val stubHop =
        HopResult(
            hopNumber = 1,
            ip = "10.0.0.1",
            hostname = "gateway",
            status = HopStatus.SUCCESS,
            rtTimeMs = 2L,
        )

    private fun errorMessage() = (viewModel.uiState.value as TracerouteUiState.Error).message

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracerouteUseCase = mockk()
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        networkStatus.value =
            NetworkStatus(
                hasInternet = true,
                hasLocalNetwork = true,
                transport = Transport.WIFI,
            )
        localNetworkPermissionAllowed = true
        viewModel = createViewModel()
    }

    private fun createViewModel() =
        TracerouteViewModel(
            tracerouteUseCase,
            recentHostsRepository,
            object : NetworkStatusProvider {
                override val status = networkStatus
            },
            LinkInfoProvider({ true }, { localNetworkPermissionAllowed }),
        )

    private fun createNativeAdapterUseCase(
        resolvedTarget: String,
        enrichment: CompletableDeferred<HopGeoLocation?>,
    ) = TracerouteUseCase(
        IcmpEnginTracerouteRepositoryImpl(
            nativeTraceFactory = { _, _, _, _, _, _, _ ->
                flowOf(
                    mapNativeHop(
                        NativeHopStatus(
                            1,
                            setOf("192.0.2.1"),
                            listOf(Response.Success(1_500, 0)),
                            false,
                        ),
                    ),
                    HopResult(2, null, null, null, HopStatus.TIMEOUT),
                )
            },
            hostResolver = TracerouteHostResolver { _, _ -> resolvedTarget },
            dispatcher = Dispatchers.Unconfined,
        ),
        object : GeoIpRepository {
            override suspend fun lookup(ip: String): HopGeoLocation? = enrichment.await()
        },
    )

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
        fun `transitions through Running to Finished`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(stubHop),
                    )
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                val state = viewModel.uiState.value
                assertTrue(state is TracerouteUiState.Finished, "Expected Finished but was $state")
            }

        @Test
        fun `enrichment updates its hop in place without changing order`() =
            runTest {
                val first =
                    stubHop.copy(
                        hostname = null,
                        geoLocation = null,
                        probeRttsMs = listOf(2L, null, 3L),
                    )
                val second = stubHop.copy(hopNumber = 2, ip = "10.0.0.2", hostname = null)
                val firstGeo = HopGeoLocation("10.0.0.1", "United Kingdom", "GB", "London", 51.5, -0.1)
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(first),
                        TracerouteFlowResult.Hop(second),
                        TracerouteFlowResult.HopEnriched(1, "router.example", firstGeo),
                    )
                viewModel.onHostChange("example.com")

                viewModel.startTrace()

                val finished = viewModel.uiState.value as TracerouteUiState.Finished
                assertEquals(listOf(1, 2), finished.result.hops.map { it.hopNumber })
                assertEquals("router.example", finished.result.hops[0].hostname)
                assertEquals(firstGeo, finished.result.hops[0].geoLocation)
                assertEquals(listOf(2L, null, 3L), finished.result.hops[0].probeRttsMs)
                assertEquals(null, finished.result.hops[1].hostname)
            }

        @Test
        fun `transitions to Error on ValidationError`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.ValidationError("empty host"),
                    )
                viewModel.onHostChange("")
                viewModel.startTrace()
                assertTrue(viewModel.uiState.value is TracerouteUiState.Error)
                coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
            }

        @Test
        fun `transitions to Error when no hops received`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns flowOf()
                viewModel.onHostChange("unreachable")
                viewModel.startTrace()
                assertEquals("No route found to unreachable", errorMessage())
            }

        @Test
        fun `offline trace fails before invoking the probe`() =
            runTest {
                networkStatus.value = NetworkStatus()
                viewModel.onHostChange("example.com")

                viewModel.startTrace()

                assertEquals("No network connection", errorMessage())
                coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
                io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
            }

        @Test
        fun `unvalidated private literal permits the bounded trace`() =
            runTest {
                networkStatus.value = NetworkStatus(hasLocalNetwork = true, transport = Transport.WIFI)
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(stubHop),
                    )
                viewModel.onHostChange("192.168.1.1")

                viewModel.startTrace()

                assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
                io.mockk.verify(exactly = 1) {
                    tracerouteUseCase(match { it.host == "192.168.1.1" }, any())
                }
            }

        @Test
        fun `unvalidated public literal permits the bounded trace`() =
            runTest {
                networkStatus.value = NetworkStatus(hasInternet = true, transport = Transport.WIFI)
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(stubHop),
                    )
                viewModel.onHostChange("1.1.1.1")

                viewModel.startTrace()

                assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
                io.mockk.verify(exactly = 1) {
                    tracerouteUseCase(match { it.host == "1.1.1.1" }, any())
                }
            }

        @Test
        fun `unvalidated hostname through system resolver permits the bounded trace`() =
            runTest {
                networkStatus.value =
                    NetworkStatus(
                        hasInternet = true,
                        hasLocalNetwork = true,
                        transport = Transport.WIFI,
                    )
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(stubHop),
                    )
                viewModel.onHostChange("example.com")

                viewModel.startTrace()

                assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
                io.mockk.verify(exactly = 1) {
                    tracerouteUseCase(match { it.host == "example.com" }, any())
                }
            }

        @Test
        fun `private literal is denied when local network permission is explicitly unavailable`() =
            runTest {
                localNetworkPermissionAllowed = false
                viewModel.onHostChange("192.168.1.1")

                viewModel.startTrace()

                assertEquals("Local network permission denied", errorMessage())
                io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
            }

        @Test
        fun `private literal is denied when only a cellular Internet route is present`() =
            runTest {
                networkStatus.value = NetworkStatus(hasInternet = true, transport = Transport.CELLULAR)
                viewModel.onHostChange("192.168.1.1")

                viewModel.startTrace()

                assertEquals("No network connection", errorMessage())
                io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
            }

        @Test
        fun `local network without validated internet can start a trace`() =
            runTest {
                networkStatus.value = NetworkStatus(hasInternet = false, hasLocalNetwork = true)
                every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
                viewModel.onHostChange("192.168.1.1")

                viewModel.startTrace()

                assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
                io.mockk.verify(exactly = 1) { tracerouteUseCase(match { it.host == "192.168.1.1" }, any()) }
            }

        @Test
        fun `request over hard ceiling is rejected before invoking use case or recording host`() =
            runTest {
                viewModel.onHostChange("example.com")
                viewModel.onMaxHopsChange(64)
                viewModel.onTimeoutChange(20_000)

                viewModel.startTrace()

                assertEquals(
                    "Requested trace exceeds the 20-minute time limit; reduce max hops, probes per hop, or timeout",
                    errorMessage(),
                )
                io.mockk.verify(exactly = 0) { tracerouteUseCase(any(), any()) }
                coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
            }

        @Test
        fun `vpn only connectivity can start a trace`() =
            runTest {
                networkStatus.value = NetworkStatus(hasInternet = false, hasLocalNetwork = false, vpnActive = true)
                every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
                viewModel.onHostChange("internal.example")

                viewModel.startTrace()

                assertTrue(viewModel.uiState.value is TracerouteUiState.Finished)
                io.mockk.verify(exactly = 1) { tracerouteUseCase(match { it.host == "internal.example" }, any()) }
            }

        @Test
        fun `probe exception is exposed as an error`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } throws IllegalStateException("route socket closed")
                viewModel.onHostChange("example.com")

                viewModel.startTrace()

                assertEquals("route socket closed", errorMessage())
            }

        @Test
        fun `accumulates hops in Finished result`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns
                    flowOf(
                        TracerouteFlowResult.Hop(stubHop),
                        TracerouteFlowResult.Hop(stubHop.copy(hopNumber = 2, ip = "8.8.8.8")),
                    )
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                val state = viewModel.uiState.value as TracerouteUiState.Finished
                assertEquals(2, state.result.hops.size)
                assertEquals(null, state.result.resolvedIp)
                assertEquals(false, state.result.reachedDestination)
            }

        @Test
        fun `native adapter destination is retained through late enrichment when final hop fails`() =
            runBlocking {
                Dispatchers.setMain(Dispatchers.Unconfined)
                viewModel = createViewModel()

                val resolvedTarget = "203.0.113.7"
                val enrichment = CompletableDeferred<HopGeoLocation?>()
                val geoLocation =
                    HopGeoLocation(
                        ip = "192.0.2.1",
                        country = "Example",
                        countryCode = "EX",
                        city = "Router",
                        lat = 1.0,
                        lon = 2.0,
                    )
                val useCase = createNativeAdapterUseCase(resolvedTarget, enrichment)
                every { tracerouteUseCase(any(), any()) } answers {
                    useCase(firstArg<TracerouteParams>(), secondArg<OperationSession>())
                }

                viewModel.onHostChange("example.com")
                viewModel.startTrace()

                val partial =
                    withTimeout(2_000) {
                        viewModel.uiState.first {
                            it is TracerouteUiState.Running && it.hops.size == 2
                        }
                    } as TracerouteUiState.Running
                assertEquals(2, partial.hops.size)
                assertEquals(resolvedTarget, partial.hops.first().resolvedDestinationIp)
                enrichment.complete(geoLocation)

                val finished =
                    withTimeout(2_000) {
                        viewModel.uiState.first { it is TracerouteUiState.Finished }
                    } as TracerouteUiState.Finished
                val result = finished.result
                assertEquals(resolvedTarget, result.resolvedIp)
                assertEquals(HopStatus.TIMEOUT, result.hops.last().status)
                assertEquals(resolvedTarget, result.hops.first().resolvedDestinationIp)
                assertEquals(resolvedTarget, result.hops.last().resolvedDestinationIp)
                assertEquals(geoLocation, result.hops.first().geoLocation)
                assertEquals(false, result.reachedDestination)
            }
    }

    @Nested
    @DisplayName("cancel and clear")
    inner class CancelAndClear {
        @Test
        fun `user stop preserves elapsed duration in canceled partial result`() =
            runTest {
                val cleanupStarted = CountDownLatch(1)
                val allowCleanupToFinish = CountDownLatch(1)
                var nowNanos = 5_000_000_000L
                viewModel.monotonicTimeNs = { nowNanos }
                every { tracerouteUseCase(any(), any()) } answers {
                    val session = secondArg<OperationSession>()
                    flow {
                        session.resources.register(
                            AutoCloseable {
                                cleanupStarted.countDown()
                                check(allowCleanupToFinish.await(5, TimeUnit.SECONDS))
                            },
                        )
                        emit(TracerouteFlowResult.Hop(stubHop))
                        awaitCancellation()
                    }
                }
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                assertEquals(listOf(stubHop), (viewModel.uiState.value as TracerouteUiState.Running).hops)

                nowNanos += 2_350_000_000L
                viewModel.onStop()

                when (val stoppedState = viewModel.uiState.value) {
                    is TracerouteUiState.Canceling -> {
                        assertEquals(2_350L, stoppedState.elapsedMs)
                    }

                    is TracerouteUiState.Canceled -> {
                        assertEquals(2_350L, stoppedState.result.totalTimeMs)
                        assertEquals(listOf(stubHop), stoppedState.result.hops)
                    }

                    else -> {
                        assertTrue(false, "Expected Canceling or Canceled, got $stoppedState")
                    }
                }
                assertTrue(withContext(Dispatchers.IO) { cleanupStarted.await(2, TimeUnit.SECONDS) })
                allowCleanupToFinish.countDown()

                val canceled =
                    viewModel.uiState.first { it is TracerouteUiState.Canceled }
                        as TracerouteUiState.Canceled
                assertEquals(2_350L, canceled.result.totalTimeMs)
                assertEquals(listOf(stubHop), canceled.result.hops)
            }

        @Test
        fun `lifecycle stop cancels trace and retains partial hops after cleanup`() =
            runTest {
                var session: OperationSession? = null
                viewModel.operationSessionFactory = { params ->
                    TracerouteOperation
                        .newSession(params.maxHops, params.timeoutMs, params.probesPerHop)
                        .also { session = it }
                }
                every { tracerouteUseCase(any(), any()) } returns
                    flow {
                        emit(TracerouteFlowResult.Hop(stubHop))
                        awaitCancellation()
                    }
                viewModel.onHostChange("example.com")

                viewModel.startTrace()
                viewModel.onLifecycleStop()

                (viewModel.uiState.value as? TracerouteUiState.Canceling)?.let { canceling ->
                    assertEquals(listOf(stubHop), canceling.hops)
                }
                val canceled =
                    viewModel.uiState.first { it is TracerouteUiState.Canceled }
                        as TracerouteUiState.Canceled
                assertEquals(CancellationReason.LIFECYCLE_PAUSE, session?.cancellationReason)
                assertEquals(listOf(stubHop), canceled.result.hops)
                assertTrue(canceled.stoppedWhenLeft)
            }

        @Test
        fun `onStop holds Canceling until cleanup finishes then keeps partial hops`() =
            runTest {
                val cleanupStarted = CountDownLatch(1)
                val allowCleanupToFinish = CountDownLatch(1)
                val cleanupFinished = CountDownLatch(1)
                every { tracerouteUseCase(any(), any()) } answers {
                    val session = secondArg<OperationSession>()
                    channelFlow {
                        val output = this
                        OperationRunner.run(session) {
                            resources.register(
                                AutoCloseable {
                                    cleanupStarted.countDown()
                                    check(allowCleanupToFinish.await(5, TimeUnit.SECONDS))
                                    cleanupFinished.countDown()
                                },
                            )
                            output.send(TracerouteFlowResult.Hop(stubHop))
                            awaitCancellation()
                        }
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
                val canceled =
                    viewModel.uiState.first { it is TracerouteUiState.Canceled }
                        as TracerouteUiState.Canceled
                assertEquals(listOf(stubHop), canceled.result.hops)
            }

        @Test
        fun `first hop remains visible and in partial results while recent write is suspended`() =
            runTest {
                val recentWrite = CompletableDeferred<Unit>()
                val recentWriteStarted = CompletableDeferred<Unit>()
                val traceCollectorFinished = CountDownLatch(1)
                coEvery {
                    recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, "example.com")
                } coAnswers {
                    recentWriteStarted.complete(Unit)
                    recentWrite.await()
                }
                every { tracerouteUseCase(any(), any()) } returns
                    kotlinx.coroutines.flow
                        .flow {
                            emit(TracerouteFlowResult.Hop(stubHop))
                            awaitCancellation()
                        }.onCompletion { traceCollectorFinished.countDown() }
                viewModel.onHostChange("example.com")

                viewModel.startTrace()
                recentWriteStarted.await()
                assertTrue(!recentWrite.isCompleted)

                assertEquals(listOf(stubHop), (viewModel.uiState.value as TracerouteUiState.Running).hops)
                viewModel.onStop()
                when (val stoppedState = viewModel.uiState.value) {
                    is TracerouteUiState.Canceling -> assertEquals(listOf(stubHop), stoppedState.hops)
                    is TracerouteUiState.Canceled -> assertEquals(listOf(stubHop), stoppedState.result.hops)
                    else -> assertTrue(false, "Expected Canceling or Canceled, got $stoppedState")
                }
                recentWrite.complete(Unit)
                assertTrue(withContext(Dispatchers.IO) { traceCollectorFinished.await(2, TimeUnit.SECONDS) })
                val canceled =
                    viewModel.uiState.first { it is TracerouteUiState.Canceled }
                        as TracerouteUiState.Canceled
                assertEquals(listOf(stubHop), canceled.result.hops)
                assertFalse(canceled.stoppedWhenLeft)
            }

        @Test
        fun `duplicate start during trace does not replace active operation`() =
            runTest {
                val firstChannel = Channel<TracerouteFlowResult>(Channel.UNLIMITED)
                val firstCollectorCancelled = CountDownLatch(1)
                every { tracerouteUseCase(any(), any()) } returnsMany
                    listOf(
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
                val canceled =
                    viewModel.uiState.first { it is TracerouteUiState.Canceled }
                        as TracerouteUiState.Canceled
                assertEquals("example.com", canceled.result.host)
                assertTrue(canceled.result.hops.isEmpty())
            }

        @Test
        fun `deadline keeps partial hops and reports the time limit`() =
            runTest {
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

                val partial =
                    viewModel.uiState.first { it is TracerouteUiState.Finished }
                        as TracerouteUiState.Finished
                assertTrue(partial.timeLimitReached)
                assertEquals(listOf(stubHop), partial.result.hops)
            }

        @Test
        fun `repository deadline after first native hop is retained as partial in ViewModel`() =
            runBlocking {
                Dispatchers.setMain(Dispatchers.Unconfined)
                viewModel = createViewModel()

                val expectedHop = stubHop.copy(resolvedDestinationIp = "192.0.2.1")
                val repository =
                    IcmpEnginTracerouteRepositoryImpl(
                        nativeTraceFactory = { _, _, _, _, _, _, _ ->
                            flow {
                                emit(stubHop)
                                // The second hop never arrives; the repository's real deadline runner wins.
                                awaitCancellation()
                            }
                        },
                    )
                every { tracerouteUseCase(any(), any()) } answers {
                    val params = firstArg<TracerouteParams>()
                    val session = secondArg<OperationSession>()
                    repository
                        .trace(
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

                val running =
                    withTimeout(2_000) {
                        viewModel.uiState.first {
                            it is TracerouteUiState.Running && expectedHop in it.hops
                        }
                    } as TracerouteUiState.Running
                assertEquals(listOf(expectedHop), running.hops)

                val partial =
                    withTimeout(2_000) {
                        viewModel.uiState.first { it is TracerouteUiState.Finished }
                    }
                        as TracerouteUiState.Finished
                assertTrue(partial.timeLimitReached)
                assertEquals(listOf(expectedHop), partial.result.hops)
            }

        @Test
        fun `onClear resets to Idle`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                viewModel.onClear()
                assertTrue(viewModel.uiState.value is TracerouteUiState.Idle)
            }
    }

    @Test
    fun `addRecent is called once on first hop`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns
                flowOf(
                    TracerouteFlowResult.Hop(stubHop),
                    TracerouteFlowResult.Hop(stubHop.copy(hopNumber = 2, ip = "8.8.8.8")),
                )
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            coVerify(exactly = 1) {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, "example.com")
            }
        }

    @Test
    fun `startTrace normalizes host before saving and probing`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("  Example.COM. ")

            viewModel.startTrace()

            coVerify {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, "example.com")
            }
            io.mockk.verify { tracerouteUseCase(match { it.host == "example.com" }, any()) }
        }

    @Test
    fun `trace completing without a hop does not save a recent host`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf()
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            assertTrue(viewModel.uiState.value is TracerouteUiState.Error)
            coVerify(exactly = 0) {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, any())
            }
        }

    @Test
    fun `invalid host is not saved to recents`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns
                flowOf(
                    TracerouteFlowResult.ValidationError("Invalid host or IP address"),
                )
            viewModel.onHostChange("bad host")

            viewModel.startTrace()

            coVerify(exactly = 0) {
                recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_TRACEROUTE_HOSTS, any())
            }
            assertTrue(viewModel.uiState.value is TracerouteUiState.Error)
        }

    @Test
    fun `Finished result has non-null rawOutput`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            val state = viewModel.uiState.value as TracerouteUiState.Finished
            assertNotNull(state.result.rawOutput)
        }

    @Test
    fun `hops are ordered by hop number when probes complete out of order`() =
        runTest {
            val hop2 = HopResult(2, "10.0.0.2", null, 5L, HopStatus.SUCCESS)
            every { tracerouteUseCase(any(), any()) } returns
                flowOf(TracerouteFlowResult.Hop(hop2), TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            val finished = viewModel.uiState.value as TracerouteUiState.Finished
            assertEquals(listOf(1, 2), finished.result.hops.map { it.hopNumber })
            val rawLines = finished.result.rawOutput.lines()
            val hopLines = rawLines.drop(1).filter { it.isNotBlank() }
            assertEquals(listOf("1", "2"), hopLines.map { it.trim().substringBefore(' ') })
        }

    @Test
    fun `timed out hop is written with three asterisks in raw output`() =
        runTest {
            val timeout = HopResult(4, null, null, null, HopStatus.TIMEOUT)
            every { tracerouteUseCase(any(), any()) } returns
                flowOf(TracerouteFlowResult.Hop(stubHop), TracerouteFlowResult.Hop(timeout))
            viewModel.onHostChange("example.com")

            viewModel.startTrace()

            val finished = viewModel.uiState.value as TracerouteUiState.Finished
            val rawLines = finished.result.rawOutput.lines()
            assertTrue(rawLines.any { it.trim() == "4  * * *" }, finished.result.rawOutput)
        }

    @Test
    fun `editing the host clears a failed trace message`() =
        runTest {
            every { tracerouteUseCase(any(), any()) } returns
                flow { emit(TracerouteFlowResult.ValidationError("Invalid host or IP address")) }
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            assertTrue(viewModel.uiState.value is TracerouteUiState.Error)

            viewModel.onHostChange("example.org")

            assertEquals(TracerouteUiState.Idle, viewModel.uiState.value)
        }

    @Test
    fun `configuration change keeps a trace running`() =
        runTest {
            var session: OperationSession? = null
            viewModel.operationSessionFactory = { params ->
                TracerouteOperation
                    .newSession(params.maxHops, params.timeoutMs, params.probesPerHop)
                    .also { session = it }
            }
            every { tracerouteUseCase(any(), any()) } returns
                flow {
                    emit(TracerouteFlowResult.Hop(stubHop))
                    awaitCancellation()
                }
            viewModel.onHostChange("example.com")
            viewModel.startTrace()

            viewModel.onLifecycleStop(configurationChange = true)

            assertTrue(viewModel.uiState.value is TracerouteUiState.Running)
            assertEquals(null, session?.cancellationReason)
            viewModel.onStop()
            viewModel.uiState.first { it is TracerouteUiState.Canceled }
        }

    @Nested
    @DisplayName("view mode selection")
    inner class ViewModeSelection {
        private fun finishTrace() {
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
        }

        @Test
        fun `a finished trace starts on Hop Details`() {
            finishTrace()
            val state = viewModel.uiState.value as TracerouteUiState.Finished
            assertEquals(TracerouteViewMode.Visual, state.viewMode)
        }

        @Test
        fun `the tabs are ordered Hop Details, Map, Raw Output`() {
            assertEquals(
                listOf(TracerouteViewMode.Visual, TracerouteViewMode.Map, TracerouteViewMode.Raw),
                TracerouteViewMode.entries.toList(),
            )
        }

        @Test
        fun `each mode can be selected directly on a finished trace`() {
            finishTrace()
            TracerouteViewMode.entries.forEach { mode ->
                viewModel.onSelectViewMode(mode)
                assertEquals(mode, (viewModel.uiState.value as TracerouteUiState.Finished).viewMode)
            }
        }

        @Test
        fun `selecting the current mode keeps it`() {
            finishTrace()
            viewModel.onSelectViewMode(TracerouteViewMode.Map)
            viewModel.onSelectViewMode(TracerouteViewMode.Map)
            assertEquals(TracerouteViewMode.Map, (viewModel.uiState.value as TracerouteUiState.Finished).viewMode)
        }

        @Test
        fun `a canceled trace can show its partial route on the map`() =
            runTest {
                every { tracerouteUseCase(any(), any()) } returns
                    flow {
                        emit(TracerouteFlowResult.Hop(stubHop))
                        awaitCancellation()
                    }
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                viewModel.onStop()
                viewModel.uiState.first { it is TracerouteUiState.Canceled }

                viewModel.onSelectViewMode(TracerouteViewMode.Map)

                val state = viewModel.uiState.value as TracerouteUiState.Canceled
                assertEquals(TracerouteViewMode.Map, state.viewMode)
                assertEquals(listOf(stubHop), state.result.hops)
            }

        @Test
        fun `selecting a mode without a result changes nothing`() {
            viewModel.onSelectViewMode(TracerouteViewMode.Map)
            assertEquals(TracerouteUiState.Idle, viewModel.uiState.value)
        }
    }

    @Nested
    @DisplayName("hop rows")
    inner class HopRows {
        private fun hop(
            number: Int,
            probes: List<Long?> = listOf(5L),
            destination: Boolean = false,
        ) = HopResult(
            hopNumber = number,
            ip = "192.0.2.$number",
            hostname = null,
            rtTimeMs = probes.firstOrNull { it != null },
            status = HopStatus.SUCCESS,
            probeRttsMs = probes,
            destinationReached = destination,
        )

        private fun finish(vararg events: TracerouteFlowResult): TracerouteResult {
            every { tracerouteUseCase(any(), any()) } returns flowOf(*events)
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            return (viewModel.uiState.value as TracerouteUiState.Finished).result
        }

        @Test
        fun `a hop reported once per probe is one row with every probe`() {
            val result =
                finish(
                    TracerouteFlowResult.Hop(hop(1, listOf(5L))),
                    TracerouteFlowResult.Hop(hop(1, listOf(5L, null))),
                    TracerouteFlowResult.Hop(hop(1, listOf(5L, null, 6L))),
                )

            assertEquals(listOf(listOf(5L, null, 6L)), result.hops.map { it.probeRttsMs })
        }

        @Test
        fun `a location found for a hop survives its later probe reports`() {
            val geo = HopGeoLocation("192.0.2.1", "France", "FR", "Paris", 48.85, 2.35)

            val result =
                finish(
                    TracerouteFlowResult.Hop(hop(1, listOf(5L))),
                    TracerouteFlowResult.HopEnriched(1, "edge.example", geo),
                    TracerouteFlowResult.Hop(hop(1, listOf(5L, 6L))),
                )

            assertEquals(geo, result.hops.single().geoLocation)
            assertEquals("edge.example", result.hops.single().hostname)
        }

        @Test
        fun `every TTL up to the destination has a row, and the hop count is the destination's TTL`() {
            val result =
                finish(
                    TracerouteFlowResult.Hop(hop(1)),
                    TracerouteFlowResult.Hop(hop(2)),
                    TracerouteFlowResult.Hop(hop(4, destination = true)),
                )

            assertEquals(listOf(1, 2, 3, 4), result.hops.map { it.hopNumber })
            assertEquals(null, result.hops[2].ip)
            assertTrue(result.reachedDestination)
        }

        @Test
        fun `hops past the first TTL the destination answered are not shown`() {
            val result =
                finish(
                    TracerouteFlowResult.Hop(hop(1)),
                    TracerouteFlowResult.Hop(hop(3, destination = true)),
                    TracerouteFlowResult.Hop(hop(2, destination = true)),
                )

            assertEquals(listOf(1, 2), result.hops.map { it.hopNumber })
        }

        @Test
        fun `the running trace keeps its raw output up to date`() =
            runTest {
                val events = Channel<TracerouteFlowResult>(Channel.UNLIMITED)
                every { tracerouteUseCase(any(), any()) } returns events.receiveAsFlow()
                viewModel.onHostChange("example.com")
                viewModel.startTrace()

                events.send(TracerouteFlowResult.Hop(hop(1, listOf(5L, null, 7L))))

                val running = viewModel.uiState.value as TracerouteUiState.Running
                val lines = running.rawOutput.lines().map { it.trim() }
                assertTrue("1  192.0.2.1 5 ms * 7 ms" in lines, running.rawOutput)
                events.close()
            }
    }

    @Nested
    @DisplayName("live view")
    inner class LiveView {
        @Test
        fun `the view picked while running is kept when the trace finishes`() =
            runTest {
                val events = Channel<TracerouteFlowResult>(Channel.UNLIMITED)
                every { tracerouteUseCase(any(), any()) } returns events.receiveAsFlow()
                viewModel.onHostChange("example.com")
                viewModel.startTrace()
                val running = viewModel.uiState.value as TracerouteUiState.Running

                viewModel.onSelectViewMode(TracerouteViewMode.Map)
                events.send(TracerouteFlowResult.Hop(stubHop))
                events.close()

                val finished = viewModel.uiState.value as TracerouteUiState.Finished
                assertEquals(TracerouteViewMode.Map, finished.viewMode)
                assertEquals(running.traceId, finished.traceId)
            }

        @Test
        fun `the next trace opens in the view picked last`() {
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.Hop(stubHop))
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            val first = viewModel.uiState.value as TracerouteUiState.Finished
            viewModel.onSelectViewMode(TracerouteViewMode.Raw)

            viewModel.startTrace()

            val second = viewModel.uiState.value as TracerouteUiState.Finished
            assertEquals(TracerouteViewMode.Raw, second.viewMode)
            assertTrue(second.traceId != first.traceId)
        }
    }

    @Nested
    @DisplayName("form defaults and steps")
    inner class FormDefaults {
        @Test
        fun `three probes per hop are sent by default`() {
            assertEquals(3, viewModel.probesPerHop.value)
        }

        @Test
        fun `the timeout lands on half-second steps`() {
            viewModel.onTimeoutChange(3_275)
            assertEquals(3_500, viewModel.timeoutMs.value)
            viewModel.onTimeoutChange(3_200)
            assertEquals(3_000, viewModel.timeoutMs.value)
        }

        @Test
        fun `slider scales stop on whole hops and half seconds`() {
            assertEquals(58, SliderScale.MAX_HOPS.steps)
            assertEquals(18, SliderScale.TIMEOUT_MS.steps)
            assertEquals(3_500, SliderScale.TIMEOUT_MS.snap(3_275f))
            assertEquals(13, SliderScale.MAX_HOPS.snap(12.6f))
            assertEquals(64, SliderScale.MAX_HOPS.snap(70f))
            assertEquals(3, SliderScale.PROBES.steps)
        }

        @Test
        fun `every timeout the slider can report snaps to a half second the view model keeps`() {
            // Material's Slider reports floats between stops while dragging (QA saw 3.3 s on the time chip).
            (500..10_000 step 25).forEach { raw ->
                val snapped = SliderScale.TIMEOUT_MS.snap(raw.toFloat())
                assertEquals(0, snapped % 500, "snap($raw)")
                viewModel.onTimeoutChange(snapped)
                assertEquals(snapped, viewModel.timeoutMs.value, "onTimeoutChange($snapped)")
            }
            assertEquals(3_500, SliderScale.TIMEOUT_MS.snap(3_300f))
        }

        @Test
        fun `packet size moves between presets, and the 56 B default is one of them`() {
            val scale = SliderScale.PACKET_SIZE
            assertEquals(56, scale.valueAt(scale.positionOf(56)))
            assertEquals(8, scale.steps)
            assertEquals(0f..9f, scale.positions)
            assertEquals(28, scale.valueAt(-1f))
            assertEquals(1472, scale.valueAt(12f))
            assertEquals(576, scale.valueAt(6.4f))
            scale.presets.forEach { assertTrue(it in scale.range, "$it") }
        }

        @Test
        fun `a packet size off the presets rests on the nearest one`() {
            val scale = SliderScale.PACKET_SIZE
            assertEquals(2f, scale.positionOf(70))
            assertEquals(1f, scale.positionOf(60))
            assertEquals(0f, scale.positionOf(0))
        }

        @Test
        fun `a scale without presets keeps its own values as positions`() {
            assertEquals(5f..64f, SliderScale.MAX_HOPS.positions)
            assertEquals(13f, SliderScale.MAX_HOPS.positionOf(13))
            assertEquals(13, SliderScale.MAX_HOPS.valueAt(12.6f))
        }
    }

    @Nested
    @DisplayName("error copy")
    inner class ErrorCopy {
        private fun errorAfterStart(): TracerouteUiState.Error {
            viewModel.onHostChange("example.com")
            viewModel.startTrace()
            return viewModel.uiState.value as TracerouteUiState.Error
        }

        @Test
        fun `no network is explained with its own copy`() {
            networkStatus.value = NetworkStatus(hasInternet = false, hasLocalNetwork = false)

            assertEquals(UiText.Res(R.string.traceroute_error_no_route), errorAfterStart().text)
        }

        @Test
        fun `an unexpected failure shows generic copy, not the exception text`() {
            every { tracerouteUseCase(any(), any()) } throws IllegalStateException("route socket closed")

            assertEquals(UiText.Res(R.string.traceroute_error_generic), errorAfterStart().text)
        }

        @Test
        fun `a host with no address names the host`() {
            every { tracerouteUseCase(any(), any()) } returns flow { throw java.net.UnknownHostException("x") }

            assertEquals(
                UiText.Res(R.string.traceroute_error_host_not_found, listOf("example.com")),
                errorAfterStart().text,
            )
        }

        @Test
        fun `a slow host lookup is reported as such`() {
            every { tracerouteUseCase(any(), any()) } returns
                flow { throw TracerouteHostResolutionTimeoutException() }

            assertEquals(
                UiText.Res(R.string.traceroute_error_lookup_timeout, listOf("example.com")),
                errorAfterStart().text,
            )
        }

        @Test
        fun `a trace with no hops names the host`() {
            every { tracerouteUseCase(any(), any()) } returns flowOf()

            val expected = UiText.Res(R.string.traceroute_error_no_hops, listOf("example.com"))
            assertEquals(expected, errorAfterStart().text)
        }

        @Test
        fun `a too-long request has its own copy`() {
            viewModel.onMaxHopsChange(64)
            viewModel.onTimeoutChange(20_000)

            assertEquals(UiText.Res(R.string.traceroute_error_too_long), errorAfterStart().text)
        }

        @Test
        fun `a validation error uses the shared error copy`() {
            val info = ErrorInfo(ErrorCode.HOST_BLANK, developerMessage = "Host must not be empty")
            every { tracerouteUseCase(any(), any()) } returns flowOf(TracerouteFlowResult.ValidationError(info))

            assertEquals(ErrorTextMapper.map(info, "Host must not be empty"), errorAfterStart().text)
        }
    }
}
