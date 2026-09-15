package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteUiState
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Traceroute's help sheet, host validation, incremental hop accumulation
 * during a run, probe-type/MTU interactions, and the Finished-state result view.
 *
 * [TracerouteScreen] requests `NEARBY_WIFI_DEVICES` on entry on API 36+ (Local
 * Network Protections, see `LocalNetworkPermission.kt`) — pre-granting it avoids
 * a system permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class TracerouteScreenTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        GrantPermissionRule.grant(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        GrantPermissionRule.grant()
    }

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
                TracerouteScreen(viewModel = fakeViewModel(TracerouteUiState.Idle))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_traceroute_concept_heading))
            .assertIsDisplayed()
    }

    @Test
    fun hostWithSpace_showsValidationError() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = fakeViewModel(TracerouteUiState.Idle, host = "bad host"))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_host))
            .assertIsDisplayed()
    }

    @Test
    fun runningState_hopsAccumulateAsTheyArrive() {
        val stateFlow = MutableStateFlow<TracerouteUiState>(
            TracerouteUiState.Running(host = "example.com", hops = listOf(fakeHop(1, "10.0.0.1")))
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("10.0.0.1").assertIsDisplayed()

        stateFlow.value = TracerouteUiState.Running(
            host = "example.com",
            hops = listOf(fakeHop(1, "10.0.0.1"), fakeHop(2, "10.0.0.2"))
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("10.0.0.1").assertIsDisplayed()
        composeRule.onNodeWithText("10.0.0.2").assertIsDisplayed()
    }

    @Test
    fun probeTypeChip_selectingUdp_notifiesViewModel() {
        val viewModel = fakeViewModel(TracerouteUiState.Idle)
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_probe_udp))
            .performClick()

        verify(exactly = 1) { viewModel.onProbeTypeChange(TracerouteProbeType.UDP) }
    }

    @Test
    fun mtuDiscoverySwitch_toggling_notifiesViewModel() {
        val viewModel = fakeViewModel(TracerouteUiState.Idle)
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_mtu_discovery_label))
            .performScrollTo()
        composeRule
            .onNode(isToggleable())
            .performClick()

        verify(exactly = 1) { viewModel.onToggleMtuDiscovery(true) }
    }

    @Test
    fun finishedState_displaysHopResults() {
        // resolvedIp must differ from every hop's IP: TraceStatsSummary renders resolvedIp
        // and HopDetailList renders each hop's IP separately, so a shared value produces two
        // nodes with identical text. onNodeWithText's single-match resolution doesn't fail
        // fast on that ambiguity the way a plain assertIsDisplayed() does — chained through
        // performScrollTo() it was observed to hang instead (reproduced independently both
        // locally and on Firebase Test Lab, where it ran out the full 15-minute timeout).
        val hops = listOf(fakeHop(1, "10.0.0.1"), fakeHop(2, "10.0.0.2"))
        val result = TracerouteResult(
            host = "example.com",
            resolvedIp = "93.184.216.34",
            hops = hops,
            rawOutput = "traceroute output",
            totalTimeMs = 500
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = fakeViewModel(TracerouteUiState.Finished(result)))
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("10.0.0.2").performScrollTo().assertIsDisplayed()
    }

    private fun fakeHop(hopNumber: Int, ip: String) = HopResult(
        hopNumber = hopNumber,
        ip = ip,
        hostname = null,
        rtTimeMs = 20L,
        status = HopStatus.SUCCESS
    )

    private fun fakeViewModel(
        state: TracerouteUiState? = null,
        flow: MutableStateFlow<TracerouteUiState>? = null,
        host: String = ""
    ): TracerouteViewModel {
        val viewModel = mockk<TracerouteViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: TracerouteUiState.Idle))
        every { viewModel.host } returns MutableStateFlow(host)
        every { viewModel.maxHops } returns MutableStateFlow(30)
        every { viewModel.timeoutMs } returns MutableStateFlow(3_000)
        every { viewModel.probesPerHop } returns MutableStateFlow(1)
        every { viewModel.probeType } returns MutableStateFlow(TracerouteProbeType.ICMP)
        every { viewModel.packetSize } returns MutableStateFlow(56)
        every { viewModel.recentHosts } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
