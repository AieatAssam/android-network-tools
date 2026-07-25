package net.aieat.netswissknife.app.ui.screens.topology

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        viewModel = TopologyDiscoveryViewModel(useCase)
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
    }

    @Nested
    @DisplayName("node selection")
    inner class NodeSelection {

        @Test
        fun `selectNode is a no-op outside Done state`() {
            viewModel.selectNode("192.168.1.1")
            assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
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
    fun `reset returns to Idle`() = runTest {
        every { useCase.invoke(params) } returns flowOf(
            TopologyDiscoveryEvent.Error("boom")
        )
        viewModel.startDiscovery(params)

        viewModel.reset()

        assertTrue(viewModel.uiState.value is TopologyUiState.Idle)
    }
}
