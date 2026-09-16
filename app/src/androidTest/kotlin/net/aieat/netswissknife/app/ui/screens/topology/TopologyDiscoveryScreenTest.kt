package net.aieat.netswissknife.app.ui.screens.topology

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.topology.TopologyGraph
import net.aieat.netswissknife.core.network.topology.TopologyNode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Network Topology's help sheet, incremental node discovery (graph nodes
 * accumulate rather than reset), config-card interactions, and the Done-state
 * node detail sheet.
 *
 * Node selection itself is done via manual canvas hit-testing in
 * [TopologyDiscoveryScreen] (tap coordinates are matched against node positions
 * computed inside a `Canvas` `pointerInput`, not exposed as clickable semantics
 * nodes), so it isn't reliably simulatable with `performClick()`. Tests instead
 * verify: (a) node labels render and accumulate as the graph grows, and (b) the
 * detail sheet renders correctly when a ViewModel state already has a node
 * selected — both of which exercise the same rendering path a real tap would.
 */
@RunWith(AndroidJUnit4::class)
class TopologyDiscoveryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun pauseAnimationClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun helpSheet_showsConceptHeading() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(viewModel = fakeViewModel(TopologyUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(500L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_topology_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun discoveringState_nodeLabelsAccumulateAsGraphGrows() {
        val stateFlow = MutableStateFlow<TopologyUiState>(
            TopologyUiState.Discovering(
                nodes = listOf(fakeNode("10.0.0.1", "core-switch")),
                links = emptyList(),
                progressMessage = "Discovering...",
                nodesDone = 1
            )
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }
        // Node labels are positioned via absolute canvas offsets computed from screen
        // size, which isn't reliably assertable across devices — assert via the
        // scanning badge's node count instead, which is fixed at TopEnd and reflects
        // the same accumulating `nodes` list.
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText(scanningBadgeText(1)).assertIsDisplayed()

        // A second node arrives; the count must grow (accumulation, not reset).
        stateFlow.value = TopologyUiState.Discovering(
            nodes = listOf(fakeNode("10.0.0.1", "core-switch"), fakeNode("10.0.0.2", "edge-router")),
            links = emptyList(),
            progressMessage = "Discovering...",
            nodesDone = 2
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText(scanningBadgeText(2)).assertIsDisplayed()
    }

    private fun scanningBadgeText(count: Int): String =
        context.resources.getQuantityString(R.plurals.topology_scanning_badge, count, count)

    @Test
    fun communityPasswordToggle_switchesVisibilityIcon() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(viewModel = fakeViewModel(TopologyUiState.Idle))
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_show_password))
            .performClick()
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_hide_password))
            .assertIsDisplayed()
    }

    @Test
    fun targetIpInput_gatesDiscoverButtonEnabled() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(viewModel = fakeViewModel(TopologyUiState.Idle))
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.topology_discover_button))
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText(context.getString(R.string.topology_target_ip_label))
            .performTextInput("192.168.1.1")
        composeRule.mainClock.advanceTimeBy(200L)

        composeRule
            .onNodeWithText(context.getString(R.string.topology_discover_button))
            .assertIsEnabled()
    }

    @Test
    fun doneState_selectedNode_showsDetailSheetWithSystemSection() {
        val node = fakeNode("10.0.0.1", "core-switch")
        val graph = TopologyGraph(nodes = listOf(node), links = emptyList(), seedIp = "10.0.0.1", queriedAt = 0L)
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(
                    viewModel = fakeViewModel(TopologyUiState.Done(graph = graph, selectedNodeIp = "10.0.0.1"))
                )
            }
        }

        // ModalBottomSheet needs more than one short tick to complete its own expand
        // animation before its content is composed/reachable.
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.topology_node_detail_system))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun failureState_showsErrorAndRetryResets() {
        val viewModel = fakeViewModel(TopologyUiState.Failure("SNMP timeout"))
        composeRule.setContent {
            NetSwissKnifeTheme {
                TopologyDiscoveryScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithText("SNMP timeout").assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.topology_error_retry))
            .performClick()

        verify(exactly = 1) { viewModel.reset() }
    }

    private fun fakeNode(ip: String, sysName: String) = TopologyNode(
        ip = ip,
        sysName = sysName,
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

    private fun fakeViewModel(
        state: TopologyUiState? = null,
        flow: MutableStateFlow<TopologyUiState>? = null
    ): TopologyDiscoveryViewModel {
        val viewModel = mockk<TopologyDiscoveryViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: TopologyUiState.Idle))
        return viewModel
    }
}
