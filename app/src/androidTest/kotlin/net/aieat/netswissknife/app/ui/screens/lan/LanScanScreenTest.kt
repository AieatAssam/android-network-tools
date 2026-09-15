package net.aieat.netswissknife.app.ui.screens.lan

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers LAN Scanner's help sheet, incremental host discovery, subnet
 * auto-detect interaction, and Finished-summary rendering + rescan.
 *
 * [LanScreen] (in `lan/LanScanScreen.kt`, distinct from the trivial
 * `screens/LanScreen.kt` wrapper) requests `NEARBY_WIFI_DEVICES` on entry on
 * API 36+, so needs the same [GrantPermissionRule] treatment as Ping/Ports.
 */
@RunWith(AndroidJUnit4::class)
class LanScanScreenTest {
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
                LanScreen(viewModel = fakeViewModel())
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onNodeWithText(context.getString(R.string.help_lan_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun scanning_hostsAccumulateAsTheyAreFound() {
        val stateFlow = MutableStateFlow<LanScanUiState>(
            LanScanUiState.Scanning(hosts = listOf(fakeHost("192.168.1.1")), scannedCount = 1, totalCount = 254)
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                LanScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onAllNodesWithText("192.168.1.1", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()

        stateFlow.value = LanScanUiState.Scanning(
            hosts = listOf(fakeHost("192.168.1.1"), fakeHost("192.168.1.2")),
            scannedCount = 2,
            totalCount = 254
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule
            .onAllNodesWithText("192.168.1.1", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText("192.168.1.2", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun detectSubnetIcon_callsRefreshSubnet() {
        val viewModel = fakeViewModel(subnet = "")
        composeRule.setContent {
            NetSwissKnifeTheme {
                LanScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.lan_detect_subnet))
            .performClick()

        verify(exactly = 1) { viewModel.refreshSubnet() }
    }

    @Test
    fun finishedState_showsSummaryHostsAndRescanTriggersStartScan() {
        val summary = LanScanSummary(
            subnet = "192.168.1.0/24",
            totalScanned = 254,
            aliveHosts = 2,
            scanDurationMs = 5_000,
            hosts = listOf(fakeHost("192.168.1.1"), fakeHost("192.168.1.50"))
        )
        val viewModel = fakeViewModel(state = LanScanUiState.Finished(summary = summary))
        composeRule.setContent {
            NetSwissKnifeTheme {
                LanScreen(viewModel = viewModel)
            }
        }

        // The host list is a LazyColumn nested inside the screen's own outer LazyColumn;
        // performScrollTo() only reliably reaches one level of scrollable ancestor, so
        // assert on the summary (rendered before the nested list, reachable without a
        // multi-level scroll) rather than an individual host row.
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onAllNodesWithText("192.168.1.0/24", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(context.getString(R.string.lan_rescan_button))
            .performScrollTo()
            .performClick()

        verify(exactly = 1) { viewModel.startScan() }
    }

    private fun fakeHost(ip: String) = LanHost(
        ip = ip,
        hostname = null,
        macAddress = null,
        vendor = null,
        openPorts = emptyList(),
        pingTimeMs = 10L,
        isGateway = false
    )

    private fun fakeViewModel(
        state: LanScanUiState? = null,
        flow: MutableStateFlow<LanScanUiState>? = null,
        subnet: String = "192.168.1.0/24"
    ): LanScanViewModel {
        val viewModel = mockk<LanScanViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: LanScanUiState.Idle))
        every { viewModel.subnet } returns MutableStateFlow(subnet)
        every { viewModel.timeoutMs } returns MutableStateFlow(500)
        every { viewModel.concurrency } returns MutableStateFlow(32)
        every { viewModel.isSubnetLoading } returns MutableStateFlow(false)
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.recentSubnets } returns MutableStateFlow(emptyList())
        return viewModel
    }
}
