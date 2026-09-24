package net.aieat.netswissknife.app.ui.screens.topology

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.domain.TopologyDiscoveryUseCase
import net.aieat.netswissknife.core.network.topology.TopologyDiscoveryEvent
import net.aieat.netswissknife.core.network.topology.TopologyGraph
import net.aieat.netswissknife.core.network.topology.TopologyLink
import net.aieat.netswissknife.core.network.topology.LinkProtocol
import net.aieat.netswissknife.core.network.topology.TopologyNode
import net.aieat.netswissknife.core.network.topology.TopologyParams
import net.aieat.netswissknife.core.network.topology.SnmpClient
import net.aieat.netswissknife.core.network.topology.SnmpTarget
import net.aieat.netswissknife.core.network.topology.SnmpWalkBudget
import net.aieat.netswissknife.core.network.topology.SnmpWalkResult
import net.aieat.netswissknife.core.network.topology.TopologyDiscoveryRepositoryImpl
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TopologyDiscoveryViewModel")
class TopologyDiscoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: TopologyDiscoveryUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: TopologyDiscoveryViewModel

    private val params = TopologyParams(targetIp = "192.168.1.1")

    private val stubNode = TopologyNode(
        ip = "192.168.1.1",
        sysName = "core-switch",
        sysDescr = null,
        vendor = null,
        model = null,
        firmwareVersion = null,
        sysLocation = null,
        uptimeHuman = null,
        capabilities = emptySet(),
        interfaces = emptyList(),
        vlans = emptyList(),
        snmpReachable = true
    )

    private val stubLink = TopologyLink(
        fromIp = "192.168.1.1",
        fromPort = "Gi0/1",
        toIp = "192.168.1.2",
        toPort = "Gi0/2",
        protocol = LinkProtocol.LLDP,
        neighbourSysName = "edge-switch"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        recentHostsRepository = mockk(relaxed = true)
        every { recentHostsRepository.getRecents(any()) } returns flowOf(emptyList())
        viewModel = TopologyDiscoveryViewModel(useCase, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts Idle`() {
            assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
        }
    }

    @Nested
    @DisplayName("startDiscovery")
    inner class StartDiscovery {

        @Test
        fun `accumulates nodes and links while discovering`() = runTest {
            every { useCase.invoke(params, any()) } returns flow {
                emit(TopologyDiscoveryEvent.NodeDiscovered(stubNode))
                emit(TopologyDiscoveryEvent.LinkDiscovered(stubLink))
                emit(TopologyDiscoveryEvent.Progress("probing", 1))
                awaitCancellation()
            }

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Discovering
            assertEquals(listOf(stubNode), state.nodes)
            assertEquals(listOf(stubLink), state.links)
            assertEquals("probing", state.progressMessage)
            assertEquals(1, state.nodesDone)
            viewModel.reset()
            awaitTopologyState { it is TopologyUiState.Canceled }
        }

        @Test
        fun `saves the seed only after the first node is discovered`() = runTest {
            every { useCase.invoke(params, any()) } returns flowOf(
                TopologyDiscoveryEvent.Progress("probing", 0),
                TopologyDiscoveryEvent.NodeDiscovered(stubNode)
            )

            viewModel.startDiscovery(params)

            coVerify(exactly = 1) {
                recentHostsRepository.addRecent(
                    net.aieat.netswissknife.app.data.AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS,
                    "192.168.1.1"
                )
            }
        }

        @Test
        fun `normalizes target before probing and saving seed`() = runTest {
            val rawParams = params.copy(targetIp = " 192.168.1.1 ")
            every { useCase.invoke(params, any()) } returns flowOf(
                TopologyDiscoveryEvent.NodeDiscovered(stubNode)
            )

            viewModel.startDiscovery(rawParams)

            coVerify(exactly = 1) { useCase.invoke(params, any()) }
            coVerify(exactly = 1) {
                recentHostsRepository.addRecent(
                    net.aieat.netswissknife.app.data.AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS,
                    "192.168.1.1"
                )
            }
        }

        @Test
        fun `invalid target is passed to domain error path and not saved`() = runTest {
            val invalidParams = params.copy(targetIp = "bad host")
            every { useCase.invoke(invalidParams, any()) } returns flowOf(
                TopologyDiscoveryEvent.Error("Target IP or hostname must be valid")
            )

            viewModel.startDiscovery(invalidParams)

            assertEquals("Target IP or hostname must be valid", (viewModel.uiState.value as TopologyUiState.Failure).message)
            coVerify(exactly = 1) { useCase.invoke(invalidParams, any()) }
            coVerify(exactly = 0) {
                recentHostsRepository.addRecent(
                    net.aieat.netswissknife.app.data.AppPreferenceKeys.RECENT_TOPOLOGY_SEEDS,
                    any()
                )
            }
        }

        @Test
        fun `transitions to Done on Complete`() = runTest {
            val graph = TopologyGraph(
                nodes = listOf(stubNode), links = listOf(stubLink),
                seedIp = "192.168.1.1", queriedAt = 0L
            )
            every { useCase.invoke(params, any()) } returns flowOf(
                TopologyDiscoveryEvent.Complete(graph)
            )

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Done
            assertEquals(graph, state.graph)
            assertNull(state.selectedNodeIp)
        }

        @Test
        fun `transitions to Failure on Error`() = runTest {
            every { useCase.invoke(params, any()) } returns flowOf(
                TopologyDiscoveryEvent.Error("SNMP timeout")
            )

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Failure
            assertEquals("SNMP timeout", state.message)
        }

        @Test
        fun `preserves permission denial cause and distinguishes generic topology errors`() = runTest {
            every { useCase.invoke(params, any()) } returns flowOf(
                TopologyDiscoveryEvent.Error(
                    "permission denied",
                    LocalNetworkPermissionDeniedException(SecurityException("denied")),
                )
            )
            viewModel.startDiscovery(params)
            assertEquals(
                NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
                (viewModel.uiState.value as TopologyUiState.Failure).networkErrorKind,
            )

            every { useCase.invoke(params, any()) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))
            viewModel.startDiscovery(params)
            assertEquals(
                NetworkErrorKind.GENERAL,
                (viewModel.uiState.value as TopologyUiState.Failure).networkErrorKind,
            )
        }

        @Test
        fun `retries with current parameters only when requested`() = runTest {
            every { useCase.invoke(params, any()) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))
            val editedParams = params.copy(
                targetIp = "192.168.1.2",
                communityString = "private",
                maxHops = 5,
            )
            every { useCase.invoke(editedParams, any()) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))

            viewModel.startDiscovery(params)
            coVerify(exactly = 1) { useCase.invoke(params, any()) }

            viewModel.retryDiscovery(editedParams)

            coVerify(exactly = 1) { useCase.invoke(editedParams, any()) }
        }
    }

    @Nested
    @DisplayName("node selection")
    inner class NodeSelection {

        @Test
        fun `selectNode is a no-op in Idle state`() {
            viewModel.selectNode("192.168.1.1")
            assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
        }

        @Test
        fun `selectNode and deselectNode work during Discovering state`() = runTest {
            every { useCase.invoke(params, any()) } returns flow {
                emit(TopologyDiscoveryEvent.NodeDiscovered(stubNode))
                awaitCancellation()
            }
            viewModel.startDiscovery(params)

            viewModel.selectNode("192.168.1.1")
            assertEquals(
                "192.168.1.1",
                (viewModel.uiState.value as TopologyUiState.Discovering).selectedNodeIp
            )

            viewModel.deselectNode()
            assertNull((viewModel.uiState.value as TopologyUiState.Discovering).selectedNodeIp)
            viewModel.reset()
            awaitTopologyState { it is TopologyUiState.Canceled }
        }

        @Test
        fun `selectNode and deselectNode update Done state`() = runTest {
            val graph = TopologyGraph(
                nodes = listOf(stubNode), links = emptyList(),
                seedIp = "192.168.1.1", queriedAt = 0L
            )
            every { useCase.invoke(params, any()) } returns flowOf(TopologyDiscoveryEvent.Complete(graph))
            viewModel.startDiscovery(params)

            viewModel.selectNode("192.168.1.1")
            assertEquals("192.168.1.1", (viewModel.uiState.value as TopologyUiState.Done).selectedNodeIp)

            viewModel.deselectNode()
            assertNull((viewModel.uiState.value as TopologyUiState.Done).selectedNodeIp)
        }
    }

    @Test
    fun `starting discovery again is ignored while the current scan is active`() = runTest {
        val nodeA = stubNode.copy(ip = "192.168.1.10")
        val nodeB = stubNode.copy(ip = "192.168.1.20")
        val channel = Channel<TopologyDiscoveryEvent>(Channel.UNLIMITED)
        val firstCollectorCancelled = CountDownLatch(1)
        val invocationCount = AtomicInteger()
        every { useCase.invoke(params, any()) } answers {
            invocationCount.incrementAndGet()
            channel.receiveAsFlow().onCompletion { firstCollectorCancelled.countDown() }
        }

        viewModel.startDiscovery(params)
        channel.trySend(TopologyDiscoveryEvent.NodeDiscovered(nodeA))
        runCurrent()

        viewModel.startDiscovery(params)
        assertEquals(1, invocationCount.get(), "an active scan must not start a second use-case flow")
        channel.trySend(TopologyDiscoveryEvent.NodeDiscovered(nodeB))
        runCurrent()

        // The original scan remains the owner and can keep reporting nodes.
        assertEquals(listOf(nodeA, nodeB), (viewModel.uiState.value as TopologyUiState.Discovering).nodes)
        viewModel.reset()
        assertTrue(withContext(Dispatchers.IO) { firstCollectorCancelled.await(2, TimeUnit.SECONDS) })
    }

    @Test
    fun `retry waits until terminal error flow cleanup completes`() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val invocationCount = AtomicInteger()
        val graph = TopologyGraph(emptyList(), emptyList(), params.targetIp, 0L)
        every { useCase.invoke(params, any()) } answers {
            if (invocationCount.incrementAndGet() == 1) {
                flow {
                    emit(TopologyDiscoveryEvent.Error("SNMP request failed"))
                    cleanupStarted.complete(Unit)
                    releaseCleanup.await()
                }
            } else {
                flowOf(TopologyDiscoveryEvent.Complete(graph))
            }
        }

        viewModel.startDiscovery(params)
        runCurrent()
        cleanupStarted.await()
        assertTrue(viewModel.uiState.value is TopologyUiState.Discovering)

        viewModel.retryDiscovery(params)
        runCurrent()
        assertEquals(1, invocationCount.get(), "Retry must remain gated while the failed flow cleans up")
        assertTrue(viewModel.uiState.value is TopologyUiState.Discovering)

        releaseCleanup.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value is TopologyUiState.Failure)

        viewModel.retryDiscovery(params)
        runCurrent()
        assertEquals(2, invocationCount.get())
        assertTrue(viewModel.uiState.value is TopologyUiState.Done)
    }

    @Test
    fun `Stop during post-terminal flow cleanup remains Canceled after the session finished`() = runTest {
        val sessionSlot = slot<OperationSession>()
        val terminalEmitted = CompletableDeferred<Unit>()
        val resourceClosed = CompletableDeferred<Unit>()
        val graph = TopologyGraph(emptyList(), emptyList(), params.targetIp, 0L)
        every { useCase.invoke(params, capture(sessionSlot)) } returns flow {
            val completedGraph = OperationRunner.run(sessionSlot.captured) {
                resources.register(AutoCloseable { resourceClosed.complete(Unit) })
                graph
            }
            emit(TopologyDiscoveryEvent.NodeDiscovered(stubNode))
            emit(TopologyDiscoveryEvent.Complete(completedGraph))
            terminalEmitted.complete(Unit)
            awaitCancellation()
        }

        viewModel.startDiscovery(params)
        runCurrent()
        terminalEmitted.await()
        assertTrue(resourceClosed.isCompleted, "OperationRunner cleanup must finish before Complete")
        assertTrue(viewModel.uiState.value is TopologyUiState.Discovering)

        viewModel.reset()
        assertTrue(viewModel.uiState.value is TopologyUiState.Canceling)
        awaitTopologyState { it is TopologyUiState.Canceled }

        assertNull(sessionSlot.captured.cancellationReason)
        val canceled = viewModel.uiState.value as TopologyUiState.Canceled
        assertEquals(listOf(stubNode), canceled.nodes)
    }

    @Test
    fun `a won deadline remains an error when Stop is tapped during cleanup`() = runTest {
        val sessionSlot = slot<OperationSession>()
        val errorEmitted = CompletableDeferred<Unit>()
        every { useCase.invoke(params, capture(sessionSlot)) } returns flow {
            sessionSlot.captured.cancel(CancellationReason.DEADLINE_EXCEEDED)
            emit(TopologyDiscoveryEvent.Error("Topology discovery timed out"))
            errorEmitted.complete(Unit)
            awaitCancellation()
        }

        viewModel.startDiscovery(params)
        runCurrent()
        errorEmitted.await()
        assertTrue(viewModel.uiState.value is TopologyUiState.Discovering)

        viewModel.reset()
        assertTrue(viewModel.uiState.value is TopologyUiState.Canceling)
        awaitTopologyState { it is TopologyUiState.Failure }

        assertEquals(CancellationReason.DEADLINE_EXCEEDED, sessionSlot.captured.cancellationReason)
        assertEquals("Topology discovery timed out", (viewModel.uiState.value as TopologyUiState.Failure).message)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        every { useCase.invoke(params, any()) } returns flowOf(
            TopologyDiscoveryEvent.Error("boom")
        )
        viewModel.startDiscovery(params)

        viewModel.reset()

        assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
    }

    @Test
    fun `reset stays Canceling through cleanup then retains partial results and ignores late completion`() = runTest {
        val channel = Channel<TopologyDiscoveryEvent>(Channel.UNLIMITED)
        val sessionSlot = slot<OperationSession>()
        val closeStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val closeCount = AtomicInteger()
        every { useCase.invoke(params, capture(sessionSlot)) } returns channel.receiveAsFlow()

        viewModel.startDiscovery(params)
        runCurrent()
        channel.trySend(TopologyDiscoveryEvent.NodeDiscovered(stubNode))
        channel.trySend(TopologyDiscoveryEvent.LinkDiscovered(stubLink))
        channel.trySend(TopologyDiscoveryEvent.Progress("Querying neighbors", 1))
        runCurrent()
        sessionSlot.captured.resources.register(AutoCloseable {
            closeCount.incrementAndGet()
            closeStarted.countDown()
            check(releaseClose.await(2, TimeUnit.SECONDS))
        })

        viewModel.reset()
        val canceling = viewModel.uiState.value as TopologyUiState.Canceling
        assertEquals(listOf(stubNode), canceling.nodes)
        assertEquals(listOf(stubLink), canceling.links)
        assertEquals(1, canceling.nodesDone)

        try {
            assertTrue(withContext(Dispatchers.IO) { closeStarted.await(2, TimeUnit.SECONDS) })
            viewModel.reset()
            assertTrue(viewModel.uiState.value is TopologyUiState.Canceling)
            assertEquals(1, closeCount.get(), "repeated Stop must not close resources twice")
        } finally {
            releaseClose.countDown()
        }
        awaitTopologyState { it is TopologyUiState.Canceled }

        channel.trySend(TopologyDiscoveryEvent.Complete(TopologyGraph(emptyList(), emptyList(), params.targetIp, 0L)))
        runCurrent()

        assertEquals(CancellationReason.USER_STOP, sessionSlot.captured.cancellationReason)
        val canceled = viewModel.uiState.value as TopologyUiState.Canceled
        assertEquals(listOf(stubNode), canceled.nodes)
        assertEquals(listOf(stubLink), canceled.links)
        assertEquals(1, canceled.nodesDone)
    }

    @Test
    fun `reset closes the in-flight SNMP client off the caller thread`() = runTest {
        val requestStarted = CountDownLatch(1)
        val clientClosed = CountDownLatch(1)
        val closeThread = AtomicReference<Thread>()
        val client = object : SnmpClient {
            override suspend fun get(target: SnmpTarget, oid: String): String? =
                suspendCancellableCoroutine { requestStarted.countDown() }

            override suspend fun walk(
                target: SnmpTarget,
                oidPrefix: String,
                budget: SnmpWalkBudget,
            ): SnmpWalkResult = SnmpWalkResult(emptyMap())

            override fun close() {
                closeThread.set(Thread.currentThread())
                clientClosed.countDown()
            }
        }
        val realUseCase = TopologyDiscoveryUseCase(TopologyDiscoveryRepositoryImpl(client))
        viewModel = TopologyDiscoveryViewModel(realUseCase, recentHostsRepository)
        val callerThread = Thread.currentThread()
        viewModel.startDiscovery(params)
        assertTrue(withContext(Dispatchers.IO) { requestStarted.await(2, TimeUnit.SECONDS) })
        viewModel.reset()

        assertTrue(viewModel.uiState.value is TopologyUiState.Canceling)
        assertTrue(withContext(Dispatchers.IO) { clientClosed.await(2, TimeUnit.SECONDS) })
        awaitTopologyState { it is TopologyUiState.Canceled }
        assertNotSame(callerThread, closeThread.get())
    }

    private suspend fun awaitTopologyState(predicate: (TopologyUiState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        withContext(Dispatchers.IO) {
            while (!predicate(viewModel.uiState.value) && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }
        }
        assertTrue(predicate(viewModel.uiState.value), "topology state did not reach the expected terminal state")
    }
}
