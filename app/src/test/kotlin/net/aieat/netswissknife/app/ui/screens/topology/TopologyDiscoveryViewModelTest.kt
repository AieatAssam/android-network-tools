package net.aieat.netswissknife.app.ui.screens.topology

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
            every { useCase.invoke(params) } returns flowOf(
                TopologyDiscoveryEvent.NodeDiscovered(stubNode),
                TopologyDiscoveryEvent.LinkDiscovered(stubLink),
                TopologyDiscoveryEvent.Progress("probing", 1)
            )

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Discovering
            assertEquals(listOf(stubNode), state.nodes)
            assertEquals(listOf(stubLink), state.links)
            assertEquals("probing", state.progressMessage)
            assertEquals(1, state.nodesDone)
        }

        @Test
        fun `saves the seed only after the first node is discovered`() = runTest {
            every { useCase.invoke(params) } returns flowOf(
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
            every { useCase.invoke(params) } returns flowOf(
                TopologyDiscoveryEvent.NodeDiscovered(stubNode)
            )

            viewModel.startDiscovery(rawParams)

            coVerify(exactly = 1) { useCase.invoke(params) }
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
            every { useCase.invoke(invalidParams) } returns flowOf(
                TopologyDiscoveryEvent.Error("Target IP or hostname must be valid")
            )

            viewModel.startDiscovery(invalidParams)

            assertEquals("Target IP or hostname must be valid", (viewModel.uiState.value as TopologyUiState.Failure).message)
            coVerify(exactly = 1) { useCase.invoke(invalidParams) }
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
            every { useCase.invoke(params) } returns flowOf(
                TopologyDiscoveryEvent.Complete(graph)
            )

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Done
            assertEquals(graph, state.graph)
            assertNull(state.selectedNodeIp)
        }

        @Test
        fun `transitions to Failure on Error`() = runTest {
            every { useCase.invoke(params) } returns flowOf(
                TopologyDiscoveryEvent.Error("SNMP timeout")
            )

            viewModel.startDiscovery(params)

            val state = viewModel.uiState.value as TopologyUiState.Failure
            assertEquals("SNMP timeout", state.message)
        }

        @Test
        fun `preserves permission denial cause and distinguishes generic topology errors`() = runTest {
            every { useCase.invoke(params) } returns flowOf(
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

            every { useCase.invoke(params) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))
            viewModel.startDiscovery(params)
            assertEquals(
                NetworkErrorKind.GENERAL,
                (viewModel.uiState.value as TopologyUiState.Failure).networkErrorKind,
            )
        }

        @Test
        fun `retries with current parameters only when requested`() = runTest {
            every { useCase.invoke(params) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))
            val editedParams = params.copy(
                targetIp = "192.168.1.2",
                communityString = "private",
                maxHops = 5,
            )
            every { useCase.invoke(editedParams) } returns flowOf(TopologyDiscoveryEvent.Error("timeout"))

            viewModel.startDiscovery(params)
            coVerify(exactly = 1) { useCase.invoke(params) }

            viewModel.retryDiscovery(editedParams)

            coVerify(exactly = 1) { useCase.invoke(editedParams) }
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
            every { useCase.invoke(params) } returns flowOf(
                TopologyDiscoveryEvent.NodeDiscovered(stubNode)
            )
            viewModel.startDiscovery(params)

            viewModel.selectNode("192.168.1.1")
            assertEquals(
                "192.168.1.1",
                (viewModel.uiState.value as TopologyUiState.Discovering).selectedNodeIp
            )

            viewModel.deselectNode()
            assertNull((viewModel.uiState.value as TopologyUiState.Discovering).selectedNodeIp)
        }

        @Test
        fun `selectNode and deselectNode update Done state`() = runTest {
            val graph = TopologyGraph(
                nodes = listOf(stubNode), links = emptyList(),
                seedIp = "192.168.1.1", queriedAt = 0L
            )
            every { useCase.invoke(params) } returns flowOf(TopologyDiscoveryEvent.Complete(graph))
            viewModel.startDiscovery(params)

            viewModel.selectNode("192.168.1.1")
            assertEquals("192.168.1.1", (viewModel.uiState.value as TopologyUiState.Done).selectedNodeIp)

            viewModel.deselectNode()
            assertNull((viewModel.uiState.value as TopologyUiState.Done).selectedNodeIp)
        }
    }

    @Test
    fun `starting discovery again cancels the previous scan's collector`() = runTest {
        val nodeA = stubNode.copy(ip = "192.168.1.10")
        val nodeB = stubNode.copy(ip = "192.168.1.20")
        val nodeC = stubNode.copy(ip = "192.168.1.30")

        val firstChannel = Channel<TopologyDiscoveryEvent>(Channel.UNLIMITED)
        val secondChannel = Channel<TopologyDiscoveryEvent>(Channel.UNLIMITED)
        every { useCase.invoke(params) } returnsMany listOf(
            firstChannel.receiveAsFlow(),
            secondChannel.receiveAsFlow()
        )

        viewModel.startDiscovery(params)
        firstChannel.trySend(TopologyDiscoveryEvent.NodeDiscovered(nodeA))
        runCurrent()

        viewModel.startDiscovery(params)
        secondChannel.trySend(TopologyDiscoveryEvent.NodeDiscovered(nodeB))
        runCurrent()

        // The first scan's collector must be cancelled by the second startDiscovery
        // call, so an event arriving late on its (stale) channel must not resurrect
        // it and overwrite the second scan's state with the first scan's node list.
        firstChannel.trySend(TopologyDiscoveryEvent.NodeDiscovered(nodeC))
        runCurrent()

        val finalNodes = (viewModel.uiState.value as TopologyUiState.Discovering).nodes
        assertEquals(listOf(nodeB), finalNodes)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        every { useCase.invoke(params) } returns flowOf(
            TopologyDiscoveryEvent.Error("boom")
        )
        viewModel.startDiscovery(params)

        viewModel.reset()

        assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
    }
}
