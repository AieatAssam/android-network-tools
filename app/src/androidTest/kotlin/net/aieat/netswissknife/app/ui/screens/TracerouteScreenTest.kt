package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkStatus
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
        // ModalBottomSheet renders in its own semantics root (a separate popup
        // window) that only gets created/attached once real frames are pumped --
        // a paused clock's advanceTimeBy never triggers that. waitUntil polls a
        // bounded condition instead of requiring full idle, so it stays safe even
        // if the underlying screen has its own infinite (e.g. refresh-spin) animation.
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.isRoot())
                .fetchSemanticsNodes().size > 1
        }
        composeRule.mainClock.autoAdvance = false

        composeRule
            .onNodeWithText(context.getString(R.string.help_traceroute_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun hostWithSpace_showsValidationError() {
        val viewModel = fakeViewModel(TracerouteUiState.Idle, host = "bad host")
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.error_invalid_host))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_start_button))
            .assertIsNotEnabled()

        composeRule.onNodeWithText("bad host").performClick().performImeAction()
        verify(exactly = 0) { viewModel.startTrace() }
    }

    @Test
    fun hostWithOuterWhitespace_remainsStartable() {
        val viewModel = fakeViewModel(TracerouteUiState.Idle, host = "  example.com  ")
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_start_button))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.mainClock.autoAdvance = false

        verify(exactly = 1) { viewModel.startTrace() }
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
        // performScrollTo() drives a scroll animation; let the test clock advance it
        // even though the rest of this class uses a paused clock for deterministic
        // entrance animations.
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("10.0.0.1").performScrollTo().assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false

        stateFlow.value = TracerouteUiState.Running(
            host = "example.com",
            hops = listOf(fakeHop(1, "10.0.0.1"), fakeHop(2, "10.0.0.2"))
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("10.0.0.1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("10.0.0.2").performScrollTo().assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun runningState_withoutResponsesShowsProgressAndStopAction() {
        val viewModel = fakeViewModel(
            TracerouteUiState.Running(host = "example.com", hops = emptyList())
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true

        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_running_title, "example.com"))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.resources.getQuantityString(R.plurals.traceroute_running_subtitle, 0, 0))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_stop_button))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.mainClock.autoAdvance = false

        verify(exactly = 1) { viewModel.onStop() }
    }

    @Test
    fun cancelingState_keepsPartialHopsAndShowsCleanupProgressBeforeCanceledResults() {
        val hop = fakeHop(1, "10.0.0.1")
        val stateFlow = MutableStateFlow<TracerouteUiState>(
            TracerouteUiState.Canceling(
                host = "example.com",
                hops = listOf(hop),
                operationId = 1,
                elapsedMs = 120,
            ),
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = fakeViewModel(flow = stateFlow, host = "example.com"))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_canceling_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_canceling_subtitle))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("10.0.0.1").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_canceling_button))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.clear))
            .assertIsNotEnabled()

        val result = TracerouteResult(
            host = "example.com",
            resolvedIp = hop.ip,
            hops = listOf(hop),
            rawOutput = "traceroute output",
            totalTimeMs = 120,
        )
        stateFlow.value = TracerouteUiState.Canceled(result)
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.resources.getQuantityString(
                R.plurals.traceroute_canceled_partial_status,
                1,
                1,
            ))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("10.0.0.1").performScrollTo().assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun offlineErrorState_showsMessageAndRecoveryActions() {
        val viewModel = fakeViewModel(TracerouteUiState.Error("No network connection"))
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true

        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_error_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("No network connection")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_retry_button))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.traceroute_clear_button))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.mainClock.autoAdvance = false

        verify(exactly = 1) { viewModel.onRetry() }
        verify(exactly = 1) { viewModel.onClear() }
    }

    @Test
    fun offlineNetworkStatus_showsConnectivityBanner() {
        val viewModel = fakeViewModel(networkStatus = NetworkStatus())
        composeRule.setContent {
            NetSwissKnifeTheme {
                TracerouteScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule
            .onNodeWithText(context.getString(R.string.network_banner_no_network))
            .assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
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
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("10.0.0.2").performScrollTo().assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
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
        host: String = "",
        networkStatus: NetworkStatus = NetworkStatus(hasInternet = true, hasLocalNetwork = true),
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
        every { viewModel.networkStatus } returns MutableStateFlow(networkStatus)
        return viewModel
    }
}
