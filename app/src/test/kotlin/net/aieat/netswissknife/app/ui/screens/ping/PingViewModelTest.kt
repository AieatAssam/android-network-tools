package net.aieat.netswissknife.app.ui.screens.ping

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.LinkInfoProvider
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.Transport
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingFlowResult
import net.aieat.netswissknife.core.domain.PingSessionLogger
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PingViewModel")
class PingViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pingUseCase: PingUseCase
    private lateinit var continuousPingUseCase: ContinuousPingUseCase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: PingViewModel
    private var localNetworkPermissionAllowed = true
    private val connectivity =
        MutableStateFlow(
            NetworkStatus(
                hasInternet = true,
                hasValidatedInternet = true,
                hasLocalNetwork = true,
                transport = Transport.WIFI,
            ),
        )
    private val networkStatusProvider =
        object : NetworkStatusProvider {
            override val status = connectivity
        }

    private fun handoffViewModel(savedStateHandle: SavedStateHandle) =
        PingViewModel(
            pingUseCase,
            continuousPingUseCase,
            dataStore,
            recentHostsRepository,
            LinkInfoProvider({ true }, { localNetworkPermissionAllowed }),
            networkStatusProvider,
            savedStateHandle = savedStateHandle,
        )

    private suspend fun awaitFinished() = viewModel.uiState.first { it is PingUiState.Finished } as PingUiState.Finished

    private suspend fun awaitError() = viewModel.uiState.first { it is PingUiState.Error } as PingUiState.Error

    private fun neverEndingFlow(count: Int = 3) =
        flow {
            repeat(count) { i ->
                emit(PingFlowResult.Packet(successPacket.copy(sequence = i + 1)))
            }
            suspendCancellableCoroutine<Nothing> { }
        }

    private val successPacket =
        PingPacketResult(
            sequence = 1,
            host = "example.com",
            status = PingStatus.SUCCESS,
            rtTimeMs = 15L,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pingUseCase = mockk()
        continuousPingUseCase = mockk()
        dataStore = mockk { every { data } returns flowOf(emptyPreferences()) }
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        connectivity.value =
            NetworkStatus(
                hasInternet = true,
                hasValidatedInternet = true,
                hasLocalNetwork = true,
                transport = Transport.WIFI,
            )
        localNetworkPermissionAllowed = true
        viewModel =
            PingViewModel(
                pingUseCase,
                continuousPingUseCase,
                dataStore,
                recentHostsRepository,
                LinkInfoProvider({ true }, { localNetworkPermissionAllowed }),
                networkStatusProvider,
            )
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            // Continuous-mode pings hop a child coroutine onto Dispatchers.IO (real disk
            // writes via PingSessionLogger). Cancel and join before resetMain() so that
            // coroutine never touches Main after the test dispatcher is gone (flaky
            // IllegalStateException otherwise) -- same class of race fixed for
            // LanScanViewModelTest.
            viewModel.viewModelScope.coroutineContext.job
                .cancelAndJoin()
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
        fun `transitions to Finished after all packets`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns
                    flowOf(
                        PingFlowResult.Packet(successPacket),
                        PingFlowResult.Packet(successPacket.copy(sequence = 2)),
                    )
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                val state = viewModel.uiState.value
                assertTrue(state is PingUiState.Finished)
                assertEquals(2, (state as PingUiState.Finished).result.packets.size)
            }

        @Test
        fun `Finished state has null sessionLogFile in normal mode`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                val state = viewModel.uiState.value as PingUiState.Finished
                assertNull(state.sessionLogFile)
            }

        @Test
        fun `lifecycle stop waits for blocked resource cleanup before publishing partial result`() =
            runTest {
                val cleanupStarted = CountDownLatch(1)
                val allowCleanupToFinish = CountDownLatch(1)
                val cleanupFinished = CountDownLatch(1)
                val sessionSlot = slot<OperationSession>()
                coEvery { pingUseCase(any(), capture(sessionSlot)) } answers {
                    val session = secondArg<OperationSession>()
                    flow {
                        session.resources.register(
                            AutoCloseable {
                                cleanupStarted.countDown()
                                check(allowCleanupToFinish.await(5, TimeUnit.SECONDS))
                                cleanupFinished.countDown()
                            },
                        )
                        emit(PingFlowResult.Packet(successPacket))
                        try {
                            awaitCancellation()
                        } finally {
                            session.resources.close()
                        }
                    }
                }
                viewModel.onHostChange("example.com")

                viewModel.startPing()

                val running = viewModel.uiState.value as PingUiState.Running
                assertEquals(1, running.packets.size)
                try {
                    viewModel.onLifecycleStop()
                    assertTrue(withContext(Dispatchers.IO) { cleanupStarted.await(2, TimeUnit.SECONDS) })
                    assertTrue(viewModel.uiState.value is PingUiState.Running)
                    assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)

                    allowCleanupToFinish.countDown()
                    val finished =
                        viewModel.uiState.first { it is PingUiState.Finished }
                            as PingUiState.Finished
                    assertTrue(withContext(Dispatchers.IO) { cleanupFinished.await(2, TimeUnit.SECONDS) })
                    assertEquals(running.packets, finished.result.packets)
                } finally {
                    allowCleanupToFinish.countDown()
                }
            }

        @Test
        fun `old lifecycle cleanup cannot replace a newer ping result`() =
            runTest {
                val cleanupStarted = CountDownLatch(1)
                val allowCleanupToFinish = CountDownLatch(1)
                val cleanupFinished = CountDownLatch(1)
                var invocation = 0
                coEvery { pingUseCase(any(), any()) } answers {
                    val session = secondArg<OperationSession>()
                    invocation++
                    if (invocation == 1) {
                        flow {
                            session.resources.register(
                                AutoCloseable {
                                    cleanupStarted.countDown()
                                    check(allowCleanupToFinish.await(5, TimeUnit.SECONDS))
                                    cleanupFinished.countDown()
                                },
                            )
                            emit(PingFlowResult.Packet(successPacket))
                            try {
                                awaitCancellation()
                            } finally {
                                session.resources.close()
                            }
                        }
                    } else {
                        flowOf(PingFlowResult.Packet(successPacket.copy(host = "new.example.com")))
                    }
                }
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                viewModel.onLifecycleStop()
                assertTrue(withContext(Dispatchers.IO) { cleanupStarted.await(2, TimeUnit.SECONDS) })

                try {
                    viewModel.onHostChange("new.example.com")
                    viewModel.startPing()
                    val newer = viewModel.uiState.value as PingUiState.Finished
                    assertEquals("new.example.com", newer.result.host)

                    allowCleanupToFinish.countDown()
                    assertTrue(withContext(Dispatchers.IO) { cleanupFinished.await(2, TimeUnit.SECONDS) })
                    assertEquals(newer, viewModel.uiState.value)
                } finally {
                    allowCleanupToFinish.countDown()
                }
            }

        @Test
        fun `passes a count of 100 to the ping use case`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("example.com")
                viewModel.onCountChange(100)

                viewModel.startPing()

                coVerify(exactly = 1) { pingUseCase(match { it.count == 100 }, any()) }
            }

        @Test
        fun `passes displayed advanced option values to the ping use case`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("example.com")
                viewModel.onPayloadSizeChange(512)
                viewModel.onTtlChange(128)
                viewModel.onIntervalChange(2_500)

                viewModel.startPing()

                coVerify(exactly = 1) {
                    pingUseCase(
                        match {
                            it.payloadBytes == 512 && it.ttl == 128 && it.intervalMs == 2_500
                        },
                        any(),
                    )
                }
            }

        @Test
        fun `transitions to Error on ValidationError`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.ValidationError("invalid host"))
                viewModel.onHostChange("")
                viewModel.startPing()
                val state = viewModel.uiState.value
                assertTrue(state is PingUiState.Error)
                assertEquals("invalid host", (state as PingUiState.Error).message)
            }

        @Test
        fun `transitions to Error when flow completes with no packets`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf()
                viewModel.onHostChange("unreachable.host")
                viewModel.startPing()
                assertTrue(viewModel.uiState.value is PingUiState.Error)
            }

        @Test
        fun `offline normal ping fails before invoking the probe`() =
            runTest {
                connectivity.value = NetworkStatus()
                viewModel.onHostChange("example.com")

                viewModel.startPing()

                assertEquals(PingUiState.Error("No network connection"), viewModel.uiState.value)
                coVerify(exactly = 0) { pingUseCase(any(), any()) }
            }

        @Test
        fun `unvalidated local literal permits the bounded probe`() =
            runTest {
                connectivity.value =
                    NetworkStatus(
                        hasInternet = true,
                        hasLocalNetwork = true,
                        transport = Transport.WIFI,
                    )
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("192.168.1.1")

                viewModel.startPing()

                coVerify(exactly = 1) { pingUseCase(match { it.host == "192.168.1.1" }, any()) }
                assertTrue(viewModel.uiState.value is PingUiState.Finished)
            }

        @Test
        fun `unvalidated public literal permits the bounded probe`() =
            runTest {
                connectivity.value = NetworkStatus(hasInternet = true, transport = Transport.WIFI)
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("1.1.1.1")

                viewModel.startPing()

                coVerify(exactly = 1) { pingUseCase(match { it.host == "1.1.1.1" }, any()) }
                assertTrue(viewModel.uiState.value is PingUiState.Finished)
            }

        @Test
        fun `unvalidated hostname through system resolver permits the bounded probe`() =
            runTest {
                connectivity.value =
                    NetworkStatus(
                        hasInternet = true,
                        hasLocalNetwork = true,
                        transport = Transport.WIFI,
                    )
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("example.com")

                viewModel.startPing()

                coVerify(exactly = 1) { pingUseCase(match { it.host == "example.com" }, any()) }
                assertTrue(viewModel.uiState.value is PingUiState.Finished)
            }

        @Test
        fun `local literal is denied when local network permission is explicitly unavailable`() =
            runTest {
                localNetworkPermissionAllowed = false
                viewModel.onHostChange("192.168.1.1")

                viewModel.startPing()

                assertEquals(PingUiState.Error("Local network permission denied"), viewModel.uiState.value)
                coVerify(exactly = 0) { pingUseCase(any(), any()) }
            }

        @Test
        fun `local literal is denied when only a cellular Internet route is present`() =
            runTest {
                connectivity.value = NetworkStatus(hasInternet = true, transport = Transport.CELLULAR)
                viewModel.onHostChange("192.168.1.1")

                viewModel.startPing()

                assertEquals(PingUiState.Error("No network connection"), viewModel.uiState.value)
                coVerify(exactly = 0) { pingUseCase(any(), any()) }
            }

        @Test
        fun `probe exception is exposed as an error`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } throws IllegalStateException("socket closed")
                viewModel.onHostChange("example.com")

                viewModel.startPing()

                assertEquals(PingUiState.Error("socket closed"), viewModel.uiState.value)
            }
    }

    // ── Normal ping cancel and clear ──────────────────────────────────────────

    @Nested
    @DisplayName("normal ping cancel and clear")
    inner class NormalPingCancelAndClear {
        @Test
        fun `onStop with collected packets moves to Finished`() =
            runTest {
                val sessionSlot = slot<OperationSession>()
                coEvery { pingUseCase(any(), capture(sessionSlot)) } returns
                    flow {
                        emit(PingFlowResult.Packet(successPacket))
                        suspendCancellableCoroutine<Nothing> { }
                    }
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                viewModel.onStop()
                assertTrue(viewModel.uiState.value is PingUiState.Finished)
                assertEquals(CancellationReason.USER_STOP, sessionSlot.captured.cancellationReason)
            }

        @Test
        fun `onClearResults resets to Idle`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                viewModel.onClearResults()
                assertTrue(viewModel.uiState.value is PingUiState.Idle)
            }

        @Test
        fun `onLifecycleStop cancels normal ping and preserves its partial result`() =
            runTest {
                val sessionSlot = slot<OperationSession>()
                coEvery { pingUseCase(any(), capture(sessionSlot)) } returns
                    flow {
                        emit(PingFlowResult.Packet(successPacket))
                        awaitCancellation()
                    }
                viewModel.onHostChange("example.com")
                viewModel.startPing()

                assertEquals(1, (viewModel.uiState.value as PingUiState.Running).packets.size)
                viewModel.onLifecycleStop()

                val finished = awaitFinished()
                assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
                assertEquals(listOf(successPacket), finished.result.packets)
            }

        @Test
        fun `onLifecycleStop cancels the caller-owned continuous session`() =
            runTest {
                val sessionSlot = slot<OperationSession>()
                coEvery { continuousPingUseCase(any(), capture(sessionSlot)) } returns neverEndingFlow()
                viewModel.onHostChange("example.com")
                viewModel.onToggleContinuous(true)

                viewModel.startPing()
                viewModel.onLifecycleStop()

                assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
            }
    }

    // ── Continuous ping ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("continuous ping")
    inner class ContinuousPing {
        @Test
        fun `bounded log queue backpressures and close drains every packet in order`() =
            runTest {
                val appendStarted = CompletableDeferred<Unit>()
                val releaseAppend = CompletableDeferred<Unit>()
                val logFile = java.io.File.createTempFile("ping_bounded_writer_", ".csv")
                val writer =
                    ContinuousPingLogWriter(
                        scope = backgroundScope,
                        logger = PingSessionLogger(logFile),
                        appendPacket = { logger, sequence, packet ->
                            if (sequence == 1) {
                                appendStarted.complete(Unit)
                                releaseAppend.await()
                            }
                            logger.append(sequence, packet)
                        },
                        dispatcher = UnconfinedTestDispatcher(testScheduler),
                    )

                try {
                    writer.append(1, successPacket)
                    appendStarted.await()
                    repeat(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY) { index ->
                        writer.append(index + 2, successPacket)
                    }

                    val backpressuredSend =
                        launch {
                            writer.append(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 2, successPacket)
                        }
                    runCurrent()
                    assertFalse(backpressuredSend.isCompleted, "send should wait while the bounded queue is full")

                    releaseAppend.complete(Unit)
                    backpressuredSend.join()
                    assertTrue(writer.closeAndJoin())

                    assertEquals(
                        (1..ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 2).map { it.toString() },
                        logFile.readLines().drop(1).map { it.substringBefore(',') },
                    )
                } finally {
                    releaseAppend.complete(Unit)
                    writer.cancel()
                    logFile.delete()
                }
            }

        @Test
        fun `cancelling a backpressured append does not cancel writer or lose accepted packets`() =
            runTest {
                val appendStarted = CompletableDeferred<Unit>()
                val releaseAppend = CompletableDeferred<Unit>()
                val logFile = java.io.File.createTempFile("ping_cancelled_send_", ".csv")
                val writer =
                    ContinuousPingLogWriter(
                        scope = backgroundScope,
                        logger = PingSessionLogger(logFile),
                        appendPacket = { logger, sequence, packet ->
                            if (sequence == 1) {
                                appendStarted.complete(Unit)
                                releaseAppend.await()
                            }
                            logger.append(sequence, packet)
                        },
                        dispatcher = UnconfinedTestDispatcher(testScheduler),
                    )

                try {
                    writer.append(1, successPacket)
                    appendStarted.await()
                    repeat(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY) { index ->
                        writer.append(index + 2, successPacket)
                    }
                    val cancelledSend =
                        launch {
                            writer.append(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 2, successPacket)
                        }
                    runCurrent()
                    assertFalse(cancelledSend.isCompleted, "send should wait while the bounded queue is full")

                    cancelledSend.cancelAndJoin()
                    releaseAppend.complete(Unit)
                    assertTrue(writer.closeAndJoin())
                    assertTrue(cancelledSend.isCancelled)

                    assertEquals(
                        (1..ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 1).map { it.toString() },
                        logFile.readLines().drop(1).map { it.substringBefore(',') },
                    )
                } finally {
                    releaseAppend.complete(Unit)
                    writer.cancel()
                    logFile.delete()
                }
            }

        @Test
        fun `log initialization failure stays best effort and does not block later appends`() =
            runTest {
                val directory =
                    java.nio.file.Files
                        .createTempDirectory("ping_writer_init_failure_")
                        .toFile()
                var appendCount = 0
                val writer =
                    ContinuousPingLogWriter(
                        scope = backgroundScope,
                        logger = PingSessionLogger(directory),
                        appendPacket = { _, _, _ -> appendCount++ },
                        dispatcher = UnconfinedTestDispatcher(testScheduler),
                    )

                try {
                    withTimeout(5_000) {
                        repeat(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 1) { index ->
                            writer.append(index + 1, successPacket)
                        }
                    }
                    assertFalse(writer.closeAndJoin())
                    writer.append(ContinuousPingLogWriter.PACKET_QUEUE_CAPACITY + 2, successPacket)
                    assertEquals(0, appendCount)
                } finally {
                    writer.cancel()
                    directory.deleteRecursively()
                }
            }

        @BeforeEach
        fun enableContinuous() {
            viewModel.onToggleContinuous(true)
            viewModel.onHostChange("example.com")
        }

        @Test
        fun `Running state has isContinuous true`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                val state = viewModel.uiState.value
                assertTrue(state is PingUiState.Running)
                assertTrue((state as PingUiState.Running).isContinuous)
            }

        @Test
        fun `pingsSent increments with each packet`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                val state = viewModel.uiState.value as PingUiState.Running
                assertTrue(state.pingsSent > 0)
            }

        @Test
        fun `continuous statistics cover all packets while the result window stays capped`() =
            runTest {
                val allPacketsEmitted = CompletableDeferred<Unit>()
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flow {
                        repeat(150) { i ->
                            val packet =
                                if (i < 50) {
                                    successPacket.copy(
                                        sequence = i + 1,
                                        status = PingStatus.TIMEOUT,
                                        rtTimeMs = null,
                                    )
                                } else {
                                    successPacket.copy(sequence = i + 1)
                                }
                            emit(PingFlowResult.Packet(packet))
                        }
                        allPacketsEmitted.complete(Unit)
                        suspendCancellableCoroutine<Nothing> { }
                    }
                viewModel.startPing()
                allPacketsEmitted.await()
                val running = viewModel.uiState.value as PingUiState.Running

                assertEquals(150, running.pingsSent)
                assertEquals(100, running.packets.size)
                assertEquals(51, running.packets.first().sequence)
                assertEquals(150, running.packets.last().sequence)
                assertEquals(150, running.stats?.sent)
                assertEquals(100, running.stats?.received)
                assertEquals(33.333336f, running.stats?.lossPercent ?: -1f, 0.001f)

                viewModel.onStop()
                val finished = awaitFinished()
                assertEquals(150, finished.result.stats.sent)
                assertEquals(100, finished.result.stats.received)
                assertEquals(33.333336f, finished.result.stats.lossPercent, 0.001f)
                assertEquals(100, finished.result.packets.size)
                assertEquals(
                    51,
                    finished.result.packets
                        .first()
                        .sequence,
                )
            }

        @Test
        fun `onStop transitions continuous session to Finished`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                viewModel.onStop()
                assertTrue(awaitFinished().result.packets.isNotEmpty())
            }

        @Test
        fun `Finished after continuous has non-null sessionLogFile`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                viewModel.onStop()
                val state = awaitFinished()
                assertNotNull(state.sessionLogFile)
            }

        @Test
        fun `stop waits for the writer to drain every queued CSV row`() =
            runTest {
                val appendStarted = CompletableDeferred<Unit>()
                val releaseAppend = CompletableDeferred<Unit>()
                val allPacketsEmitted = CompletableDeferred<Unit>()
                viewModel.sessionLogAppendHook = { logger, sequence, packet ->
                    if (sequence == 1) {
                        appendStarted.complete(Unit)
                        releaseAppend.await()
                    }
                    logger.append(sequence, packet)
                }
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flow {
                        repeat(3) { i -> emit(PingFlowResult.Packet(successPacket.copy(sequence = i + 1))) }
                        allPacketsEmitted.complete(Unit)
                        suspendCancellableCoroutine<Nothing> { }
                    }

                viewModel.startPing()
                appendStarted.await()
                allPacketsEmitted.await()
                assertEquals(3, (viewModel.uiState.value as PingUiState.Running).pingsSent)

                viewModel.onStop()

                assertTrue(viewModel.uiState.value is PingUiState.Running)
                releaseAppend.complete(Unit)
                val finished = awaitFinished()
                val csvLines = finished.sessionLogFile!!.readLines()
                assertEquals("seq,timestamp_ms,latency_ms,status,ttl,bytes", csvLines.first())
                assertEquals(listOf("1", "2", "3"), csvLines.drop(1).map { it.substringBefore(',') })
            }

        @Test
        fun `natural completion waits for CSV writes before publishing Finished`() =
            runTest {
                val appendStarted = CompletableDeferred<Unit>()
                val releaseAppend = CompletableDeferred<Unit>()
                viewModel.sessionLogAppendHook = { logger, sequence, packet ->
                    if (sequence == 1) {
                        appendStarted.complete(Unit)
                        releaseAppend.await()
                    }
                    logger.append(sequence, packet)
                }
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flow {
                        repeat(3) { i -> emit(PingFlowResult.Packet(successPacket.copy(sequence = i + 1))) }
                    }

                viewModel.startPing()
                appendStarted.await()
                assertTrue(viewModel.uiState.value is PingUiState.Running)

                releaseAppend.complete(Unit)
                val finished = awaitFinished()
                assertEquals(
                    listOf("1", "2", "3"),
                    finished.sessionLogFile!!
                        .readLines()
                        .drop(1)
                        .map { it.substringBefore(',') },
                )
            }

        @Test
        fun `stopping with no packets removes the empty session file`() =
            runTest {
                val logFile = java.io.File.createTempFile("ping_empty_test_", ".csv")
                viewModel.sessionLogFileFactory = { logFile }
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flow {
                        suspendCancellableCoroutine<Nothing> { }
                    }

                viewModel.startPing()
                viewModel.onStop()

                val finished = awaitFinished()
                assertNull(finished.sessionLogFile)
                assertFalse(logFile.exists())
            }

        @Test
        fun `onLifecycleStop stops continuous ping`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                assertTrue(viewModel.uiState.value is PingUiState.Running)
                viewModel.onLifecycleStop()
                assertTrue(awaitFinished().result.packets.isNotEmpty())
            }

        @Test
        fun `onLifecycleStop is idempotent - safe to call twice`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                viewModel.onLifecycleStop()
                viewModel.onLifecycleStop() // second call — must not crash or change state
                val finished = awaitFinished()
                viewModel.onLifecycleStop()
                assertEquals(finished, viewModel.uiState.value)
            }

        @Test
        fun `ValidationError clears session file and shows Error state`() =
            runTest {
                val logFile = java.io.File.createTempFile("ping_validation_test_", ".csv")
                viewModel.sessionLogFileFactory = { logFile }
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flowOf(
                        PingFlowResult.ValidationError("invalid"),
                    )
                viewModel.startPing()
                assertEquals("invalid", awaitError().message)
                assertFalse(logFile.exists())
            }

        @Test
        fun `offline continuous ping fails before creating a session or invoking the probe`() =
            runTest {
                connectivity.value = NetworkStatus()

                viewModel.startPing()

                assertEquals(PingUiState.Error("No network connection"), viewModel.uiState.value)
                coVerify(exactly = 0) { continuousPingUseCase(any(), any()) }
            }

        @Test
        fun `unvalidated public route does not start unbounded continuous ping`() =
            runTest {
                connectivity.value = NetworkStatus(hasInternet = true, transport = Transport.WIFI)
                viewModel.onHostChange("1.1.1.1")
                viewModel.onToggleContinuous(true)

                viewModel.startPing()

                assertEquals(
                    PingUiState.Error("Continuous ping requires validated connectivity"),
                    viewModel.uiState.value,
                )
                coVerify(exactly = 0) { continuousPingUseCase(any(), any()) }
            }

        @Test
        fun `continuous probe exception is exposed as an error`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } throws IllegalStateException("socket closed")

                viewModel.startPing()

                assertEquals("socket closed", awaitError().message)
            }

        @Test
        fun `starting new continuous session deletes previous log file`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                viewModel.onStop()
                val firstFile = awaitFinished().sessionLogFile
                assertNotNull(firstFile)

                // Start a second session
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                // The first file should have been deleted
                assertTrue(firstFile?.exists() == false)
            }

        @Test
        fun `onClearResults resets to Idle and cleans up file`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                viewModel.onStop()
                val logFile = awaitFinished().sessionLogFile
                viewModel.onClearResults()
                assertTrue(viewModel.uiState.value is PingUiState.Idle)
                assertTrue(logFile?.exists() == false)
            }

        @Test
        fun `addRecent is called on first continuous packet`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns neverEndingFlow()
                viewModel.startPing()
                coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com") }
            }

        @Test
        fun `continuous ping normalizes host before displaying and storing it`() =
            runTest {
                coEvery { continuousPingUseCase(any(), any()) } returns
                    flowOf(
                        PingFlowResult.Packet(successPacket),
                    )
                viewModel.onHostChange("  EXAMPLE.COM.  ")

                viewModel.startPing()

                coVerify {
                    continuousPingUseCase(match { it.host == "example.com" }, any())
                }
                coVerify {
                    recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com")
                }
                assertEquals("example.com", awaitFinished().result.host)
            }
    }

    // ── Recent hosts ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LAN host handoff")
    inner class LanHostHandoff {
        private val encodedPing =
            ToolIntentCodec.encode(
                ToolIntent(
                    ToolDestination.HostTarget(HostTool.PING, requireNotNull(ToolHost.parse("192.0.2.8"))),
                    ToolSource.LAN,
                ),
            )

        @Test
        fun `valid typed host pre-fills without starting and edits survive recreation`() {
            val routeState = SavedStateHandle(mapOf("intent" to encodedPing, "host" to "192.0.2.8"))
            val handoff = handoffViewModel(routeState)

            assertEquals("192.0.2.8", handoff.host.value)
            assertEquals(ToolSource.LAN, handoff.sourceContext)
            assertFalse(handoff.hasInvalidHandoff.value)
            assertTrue(handoff.uiState.value is PingUiState.Idle)
            assertEquals(true, routeState.get<Boolean>("pingHandoffConsumed"))
            assertEquals("lan", routeState.get<String>("pingHandoffSource"))
            coVerify(exactly = 0) { pingUseCase(any(), any()) }

            handoff.onHostChange("edited.example")
            assertEquals("edited.example", routeState.get<String>("editedHost"))
            val recreated =
                handoffViewModel(
                    SavedStateHandle(
                        mapOf("intent" to encodedPing, "host" to "192.0.2.8", "editedHost" to "edited.example"),
                    ),
                )
            assertEquals("edited.example", recreated.host.value)
            assertEquals(ToolSource.LAN, recreated.sourceContext)
            assertFalse(recreated.hasInvalidHandoff.value)
            assertTrue(recreated.uiState.value is PingUiState.Idle)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }

        @Test
        fun `cleared prefill and source stay cleared after recreation with original route arguments`() {
            val originalRouteArgs = mapOf("intent" to encodedPing, "host" to "192.0.2.8")
            val routeState = SavedStateHandle(originalRouteArgs)
            val handoff = handoffViewModel(routeState)
            assertEquals("192.0.2.8", handoff.host.value)
            assertEquals(ToolSource.LAN, handoff.sourceContext)

            handoff.clearPrefill()

            assertEquals("", handoff.host.value)
            assertNull(handoff.sourceContext)
            assertEquals("", routeState.get<String>("editedHost"))
            assertNull(routeState.get<String>("pingHandoffSource"))
            assertEquals(true, routeState.get<Boolean>("pingHandoffConsumed"))

            val recreated =
                handoffViewModel(
                    SavedStateHandle(
                        originalRouteArgs +
                            mapOf(
                                "editedHost" to "",
                                "pingHandoffConsumed" to true,
                            ),
                    ),
                )
            assertEquals("", recreated.host.value)
            assertNull(recreated.sourceContext)
            assertTrue(recreated.uiState.value is PingUiState.Idle)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }

        @Test
        fun `malformed consumed handoff does not restore stale source provenance`() {
            val restored =
                handoffViewModel(
                    SavedStateHandle(
                        mapOf(
                            "intent" to "ti1.invalid",
                            "host" to "192.0.2.8",
                            "pingHandoffConsumed" to true,
                            "pingHandoffSource" to "lan",
                            "editedHost" to "",
                        ),
                    ),
                )

            assertEquals("", restored.host.value)
            assertNull(restored.sourceContext)
            assertTrue(restored.hasInvalidHandoff.value)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }

        @Test
        fun `valid mDNS host pre-fills without starting and edits survive recreation`() {
            val mdnsIntent =
                ToolIntentCodec.encode(
                    ToolIntent(
                        ToolDestination.HostTarget(
                            HostTool.PING,
                            requireNotNull(ToolHost.parse("printer.local")),
                        ),
                        ToolSource.MDNS,
                    ),
                )
            val routeState = SavedStateHandle(mapOf("intent" to mdnsIntent, "host" to "printer.local"))
            val handoff = handoffViewModel(routeState)

            assertEquals("printer.local", handoff.host.value)
            assertEquals(ToolSource.MDNS, handoff.sourceContext)
            assertFalse(handoff.hasInvalidHandoff.value)
            assertTrue(handoff.uiState.value is PingUiState.Idle)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }

            handoff.onHostChange("edited.local")
            val recreated =
                handoffViewModel(
                    SavedStateHandle(
                        mapOf("intent" to mdnsIntent, "host" to "printer.local", "editedHost" to "edited.local"),
                    ),
                )
            assertEquals("edited.local", recreated.host.value)
            assertEquals(ToolSource.MDNS, recreated.sourceContext)
            assertTrue(recreated.uiState.value is PingUiState.Idle)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }

        @Test
        fun `malformed or wrong destination typed arguments show blank recovery form`() {
            val portsIntent =
                ToolIntentCodec.encode(
                    ToolIntent(
                        ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                        ToolSource.LAN,
                    ),
                )
            listOf("ti1.invalid", portsIntent).forEach { rawIntent ->
                val invalid =
                    handoffViewModel(
                        SavedStateHandle(mapOf("intent" to rawIntent, "host" to "192.0.2.8")),
                    )
                assertEquals("", invalid.host.value)
                assertNull(invalid.sourceContext)
                assertTrue(invalid.hasInvalidHandoff.value)
                assertTrue(invalid.uiState.value is PingUiState.Idle)
            }
        }

        @Test
        fun `mismatched typed host does not leak payload into form and bare host route remains supported`() {
            val invalid =
                handoffViewModel(
                    SavedStateHandle(mapOf("intent" to encodedPing, "host" to "192.0.2.9")),
                )
            assertEquals("", invalid.host.value)
            assertNull(invalid.sourceContext)
            assertTrue(invalid.hasInvalidHandoff.value)

            val legacy = handoffViewModel(SavedStateHandle(mapOf("host" to "legacy.example")))
            assertEquals("legacy.example", legacy.host.value)
            assertNull(legacy.sourceContext)
            assertFalse(legacy.hasInvalidHandoff.value)
        }

        @Test
        fun `invalid handoff warning clears only after a valid replacement without starting ping`() {
            val recovery =
                handoffViewModel(
                    SavedStateHandle(mapOf("intent" to "ti1.invalid", "host" to "192.0.2.8")),
                )
            assertTrue(recovery.hasInvalidHandoff.value)
            recovery.onHostChange("bad host")
            assertTrue(recovery.hasInvalidHandoff.value)

            recovery.onHostChange("replacement.example")

            assertFalse(recovery.hasInvalidHandoff.value)
            assertEquals("replacement.example", recovery.host.value)
            assertTrue(recovery.uiState.value is PingUiState.Idle)
            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }

        @Test
        fun `valid recovery edit and dismissed warning survive malformed and mismatched route recreation`() {
            val portsIntent =
                ToolIntentCodec.encode(
                    ToolIntent(
                        ToolDestination.HostTarget(HostTool.PORTS, requireNotNull(ToolHost.parse("192.0.2.8"))),
                        ToolSource.LAN,
                    ),
                )
            val invalidRoutes =
                listOf(
                    "ti1.invalid" to "192.0.2.8",
                    portsIntent to "192.0.2.8",
                    encodedPing to "192.0.2.9",
                )

            invalidRoutes.forEach { (rawIntent, routeHost) ->
                val routeState = SavedStateHandle(mapOf("intent" to rawIntent, "host" to routeHost))
                val recovery = handoffViewModel(routeState)
                assertTrue(recovery.hasInvalidHandoff.value)

                recovery.onHostChange("bad host")
                assertTrue(recovery.hasInvalidHandoff.value)
                assertEquals("bad host", routeState.get<String>("editedHost"))
                val invalidEditRestored =
                    handoffViewModel(
                        SavedStateHandle(
                            mapOf("intent" to rawIntent, "host" to routeHost, "editedHost" to "bad host"),
                        ),
                    )
                assertEquals("bad host", invalidEditRestored.host.value)
                assertTrue(invalidEditRestored.hasInvalidHandoff.value)

                recovery.onHostChange("replacement.example")
                assertFalse(recovery.hasInvalidHandoff.value)
                assertEquals(true, routeState.get<Boolean>("handoffRecovered"))
                val recreated =
                    handoffViewModel(
                        SavedStateHandle(
                            mapOf(
                                "intent" to rawIntent,
                                "host" to routeHost,
                                "editedHost" to "replacement.example",
                                "handoffRecovered" to true,
                            ),
                        ),
                    )
                assertEquals("replacement.example", recreated.host.value)
                assertFalse(recreated.hasInvalidHandoff.value)
                assertTrue(recreated.uiState.value is PingUiState.Idle)
            }

            coVerify(exactly = 0) { pingUseCase(any(), any()) }
        }
    }

    @Nested
    @DisplayName("recent hosts")
    inner class RecentHosts {
        @Test
        fun `addRecent is called on first valid packet`() =
            runTest {
                val packet = PingPacketResult(sequence = 1, host = "example.com", status = PingStatus.SUCCESS, rtTimeMs = 10L)
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(packet))
                viewModel.onHostChange("example.com")
                viewModel.startPing()
                coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com") }
            }

        @Test
        fun `normal ping normalizes host before probing displaying and storing it`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.Packet(successPacket))
                viewModel.onHostChange("  EXAMPLE.COM.  ")

                viewModel.startPing()

                coVerify { pingUseCase(match { it.host == "example.com" }, any()) }
                coVerify {
                    recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "example.com")
                }
                assertEquals("example.com", (viewModel.uiState.value as PingUiState.Finished).result.host)
            }

        @Test
        fun `addRecent is NOT called when ValidationError fires`() =
            runTest {
                coEvery { pingUseCase(any(), any()) } returns flowOf(PingFlowResult.ValidationError("bad host"))
                viewModel.onHostChange("bad!!host")
                viewModel.startPing()
                coVerify(exactly = 0) { recentHostsRepository.addRecent(any(), any()) }
            }
    }
}
