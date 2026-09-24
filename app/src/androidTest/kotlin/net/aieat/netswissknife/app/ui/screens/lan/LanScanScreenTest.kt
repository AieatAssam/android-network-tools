package net.aieat.netswissknife.app.ui.screens.lan

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.Assert.assertEquals
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

    @Test
    fun cancelingState_disablesActionsThenCanceledStateRetainsResultsAndActions() {
        val summary = LanScanSummary(
            subnet = "192.168.1.0/24",
            totalScanned = 12,
            aliveHosts = 1,
            scanDurationMs = 900,
            hosts = listOf(fakeHost("192.168.1.1")),
        )
        val stateFlow = MutableStateFlow<LanScanUiState>(LanScanUiState.Canceling(summary))
        val recentSubnet = "10.0.0.0/24"
        val viewModel = fakeViewModel(flow = stateFlow, recentSubnets = listOf(recentSubnet))
        composeRule.setContent {
            NetSwissKnifeTheme { LanScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        val cancelingCopy = composeRule.onAllNodesWithText(context.getString(R.string.lan_canceling_title))
        cancelingCopy.assertCountEquals(2)
        cancelingCopy.onFirst().assertIsNotEnabled()
        composeRule.onAllNodesWithText(context.getString(R.string.lan_rescan_button)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.lan_clear_button)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.lan_stop_button)).assertCountEquals(0)
        composeRule.onNodeWithText(recentSubnet).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_recent)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_clear_recents)).assertIsNotEnabled()

        stateFlow.value = LanScanUiState.Canceled(summary)
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithText(context.getString(R.string.lan_scan_canceled_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lan_scan_canceled_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lan_rescan_button)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lan_clear_button)).assertIsDisplayed()
        composeRule.onNodeWithText(recentSubnet).assertIsEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_recent)).assertIsEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_clear_recents)).assertIsEnabled()
        composeRule.onAllNodesWithText("192.168.1.0/24", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun expandedHost_offersPingActionForSelectedHost() {
        val ip = "192.168.1.50"
        val stateFlow = MutableStateFlow<LanScanUiState>(
            LanScanUiState.Finished(
                LanScanSummary(
                    subnet = "192.168.1.0/24",
                    totalScanned = 254,
                    aliveHosts = 1,
                    scanDurationMs = 500,
                    hosts = listOf(fakeHost(ip)),
                ),
            ),
        )
        val viewModel = fakeViewModel(flow = stateFlow)
        every { viewModel.onToggleHostExpanded(ip) } answers {
            val current = stateFlow.value as LanScanUiState.Finished
            stateFlow.value = current.copy(expandedHostIp = if (current.expandedHostIp == ip) null else ip)
        }

        composeRule.setContent {
            NetSwissKnifeTheme { LanScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onAllNodesWithText(ip, substring = true).onFirst().performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("lan_action_ping").performScrollTo().assertIsDisplayed().performClick()

        verify(exactly = 1) { viewModel.onPingHost(ip) }
    }

    @Test
    fun expandedHost_offersTlsActionForEachKnownOpenPort() {
        val ip = "192.168.1.51"
        val stateFlow = MutableStateFlow<LanScanUiState>(
            LanScanUiState.Finished(
                LanScanSummary(
                    subnet = "192.168.1.0/24",
                    totalScanned = 254,
                    aliveHosts = 1,
                    scanDurationMs = 500,
                    hosts = listOf(fakeHost(ip).copy(openPorts = listOf(443, 8000))),
                ),
            ),
        )
        val viewModel = fakeViewModel(flow = stateFlow)
        every { viewModel.onToggleHostExpanded(ip) } answers {
            val current = stateFlow.value as LanScanUiState.Finished
            stateFlow.value = current.copy(expandedHostIp = if (current.expandedHostIp == ip) null else ip)
        }

        composeRule.setContent {
            NetSwissKnifeTheme { LanScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onAllNodesWithText(ip, substring = true).onFirst().performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("lan_action_tls_443")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("lan_action_tls_8000").assertIsDisplayed()
        composeRule.onNodeWithTag("lan_action_tls_8443").assertDoesNotExist()

        verify(exactly = 1) { viewModel.onInspectTls(ip, 443) }
        verify(exactly = 0) { viewModel.onInspectTls(ip, 8000) }
    }

    @Test
    fun expandedHost_offersEditableHttpProbeUsingKnownPortOrPort80Fallback() {
        val ip = "192.168.1.52"
        val stateFlow = MutableStateFlow<LanScanUiState>(
            LanScanUiState.Finished(
                LanScanSummary(
                    subnet = "192.168.1.0/24",
                    totalScanned = 254,
                    aliveHosts = 1,
                    scanDurationMs = 500,
                    hosts = listOf(fakeHost(ip).copy(openPorts = listOf(443, 8080))),
                ),
            ),
        )
        val viewModel = fakeViewModel(flow = stateFlow)
        every { viewModel.onToggleHostExpanded(ip) } answers {
            val current = stateFlow.value as LanScanUiState.Finished
            stateFlow.value = current.copy(expandedHostIp = if (current.expandedHostIp == ip) null else ip)
        }

        composeRule.setContent { NetSwissKnifeTheme { LanScreen(viewModel = viewModel) } }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onAllNodesWithText(ip, substring = true).onFirst().performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("lan_action_http")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        verify(exactly = 1) { viewModel.onProbeHttp(ip, 8080) }

        val fallbackIp = "192.168.1.53"
        stateFlow.value = LanScanUiState.Finished(
            LanScanSummary(
                subnet = "192.168.1.0/24",
                totalScanned = 254,
                aliveHosts = 1,
                scanDurationMs = 500,
                hosts = listOf(fakeHost(fallbackIp).copy(openPorts = listOf(443, 8443))),
            ),
            expandedHostIp = fallbackIp,
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("Probe HTTP (port 80)").performScrollTo().assertIsDisplayed().performClick()
        verify(exactly = 1) { viewModel.onProbeHttp(fallbackIp, 80) }

        val unknownIp = "192.168.1.54"
        stateFlow.value = LanScanUiState.Finished(
            LanScanSummary(
                subnet = "192.168.1.0/24",
                totalScanned = 254,
                aliveHosts = 1,
                scanDurationMs = 500L,
                hosts = listOf(fakeHost(unknownIp)),
            ),
            expandedHostIp = unknownIp,
        )
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("Probe HTTP (port 80)").performScrollTo().assertIsDisplayed().performClick()
        verify(exactly = 1) { viewModel.onProbeHttp(unknownIp, 80) }
    }

    @Test
    fun finishedState_hidesUncertainDiagnosticsUntilOptedIn_withoutChangingConfirmedSummary() {
        val uncertainIp = "192.168.1.77"
        val summary = LanScanSummary(
            subnet = "192.168.1.0/24",
            totalScanned = 2,
            aliveHosts = 1,
            scanDurationMs = 500,
            hosts = listOf(fakeHost("192.168.1.1")),
            uncertainHosts = listOf(
                LanScanDiagnostic(
                    ip = uncertainIp,
                    reason = LanScanDiagnosticReason.TCP_REFUSED,
                    port = 80,
                )
            ),
            uncertainCount = 1,
        )
        val stateFlow = MutableStateFlow<LanScanUiState>(LanScanUiState.Finished(summary))
        val viewModel = fakeViewModel(flow = stateFlow)
        every { viewModel.onToggleDiagnostics() } answers {
            val current = stateFlow.value as LanScanUiState.Finished
            stateFlow.value = current.copy(showDiagnostics = !current.showDiagnostics)
        }

        composeRule.setContent {
            NetSwissKnifeTheme {
                LanScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)

        // Uncertain/refused probes are excluded from the normal host list.
        composeRule.onAllNodesWithText(uncertainIp, substring = true).assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.lan_hosts_header, 1), substring = true)
            .assertCountEquals(1)
        val numericSummaryBefore = listOf("1", "2").associateWith { text ->
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size
        }

        composeRule.mainClock.autoAdvance = true
        composeRule
            .onNodeWithText(context.getString(R.string.lan_show_diagnostics, 1))
            .performScrollTo()
            .performClick()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule
            .onAllNodesWithText(uncertainIp, substring = true)
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithText(context.getString(R.string.lan_diagnostic_tcp_refused), substring = true)
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithText(context.getString(R.string.lan_hide_diagnostics))
            .assertCountEquals(1)

        // Revealing diagnostics does not add to confirmed hosts or alter scan totals.
        composeRule
            .onAllNodesWithText(context.getString(R.string.lan_hosts_header, 1), substring = true)
            .assertCountEquals(1)
        val numericSummaryAfter = listOf("1", "2").associateWith { text ->
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size
        }
        assertEquals(numericSummaryBefore, numericSummaryAfter)
        verify(exactly = 1) { viewModel.onToggleDiagnostics() }
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
        subnet: String = "192.168.1.0/24",
        recentSubnets: List<String> = emptyList(),
    ): LanScanViewModel {
        val viewModel = mockk<LanScanViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: LanScanUiState.Idle))
        every { viewModel.subnet } returns MutableStateFlow(subnet)
        every { viewModel.timeoutMs } returns MutableStateFlow(500)
        every { viewModel.concurrency } returns MutableStateFlow(32)
        every { viewModel.isSubnetLoading } returns MutableStateFlow(false)
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.recentSubnets } returns MutableStateFlow(recentSubnets)
        return viewModel
    }
}
