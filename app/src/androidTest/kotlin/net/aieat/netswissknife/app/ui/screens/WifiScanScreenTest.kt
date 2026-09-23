package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.screens.wifi.ApSortOrder
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanUiState
import net.aieat.netswissknife.app.ui.screens.wifi.WifiScanViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint
import net.aieat.netswissknife.core.network.wifi.WifiConnectionInfo
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import net.aieat.netswissknife.core.network.wifi.WifiSecurity
import net.aieat.netswissknife.core.network.wifi.WifiStandard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers Wi-Fi Scanner's help sheet, the permission/support/disabled status
 * screens, sort-order interaction, and Success-state rendering with a frozen
 * network order (the app's live-refresh selection-preservation mechanism).
 *
 * [WifiScanScreen] requests `ACCESS_FINE_LOCATION` always and additionally
 * `NEARBY_WIFI_DEVICES` on API 33+ because Android's scan APIs still require
 * fine location — pre-granting both avoids a system permission dialog.
 */
@RunWith(AndroidJUnit4::class)
class WifiScanScreenTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
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
                WifiScanScreen(viewModel = fakeViewModel(successState(listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50)))))
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_help))
            .performClick()
        // ModalBottomSheet renders in its own semantics root (a separate popup
        // window) that only gets created/attached once real frames are pumped --
        // a paused clock's advanceTimeBy never triggers that. But WifiScanScreen
        // also runs its own infiniteRepeatable refresh-spin animation, so a plain
        // waitForIdle() (which waits for ALL animations to settle) would hang
        // forever. waitUntil polls a bounded condition instead of requiring full
        // idle, so it's safe to run it with the clock auto-advancing.
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.isRoot())
                .fetchSemanticsNodes().size > 1
        }
        composeRule.mainClock.autoAdvance = false

        composeRule
            .onNodeWithText(context.getString(R.string.help_wifi_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noPermissionState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.NoPermission))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_no_permission_title))
            .assertIsDisplayed()
    }

    @Test
    fun notSupportedState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.NotSupported))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_not_supported_title))
            .assertIsDisplayed()
    }

    @Test
    fun wifiDisabledState_showsMessage() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.WifiDisabled))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_disabled_title))
            .assertIsDisplayed()
    }

    @Test
    fun locationDisabledState_showsSettingsAction() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.LocationDisabled))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_location_disabled_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.wifi_open_location_settings))
            .assertIsDisplayed()
    }

    @Test
    fun throttledSuccessState_showsAgeAndThrottleLabel() {
        val result = WifiScanResult(
            accessPoints = listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50)),
            channels = emptyList(),
            connectedNetwork = null,
            scanTimestampMs = 0L,
            isWifiEnabled = true,
            isFresh = false,
            scanAgeMs = 42_000L,
            throttled = true
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.Success(result)))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText(context.getString(R.string.wifi_scan_throttled)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.wifi_results_age, 42, 42)
        ).assertIsDisplayed()
    }

    @Test
    fun refreshIntervalPicker_exposesOptionsAndPersistsSelection() {
        val viewModel = fakeViewModel(successState(listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50))))
        composeRule.setContent {
            NetSwissKnifeTheme { WifiScanScreen(viewModel = viewModel) }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.wifi_refresh_interval_off)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.wifi_refresh_interval_15s)).performClick()

        verify(exactly = 1) { viewModel.setRefreshInterval(15_000L) }
    }

    @Test
    fun connectedCard_showsIpv6GatewayAndDns() {
        val connection = WifiConnectionInfo(
            ssid = "HomeNet",
            bssid = "AA:AA:AA:AA:AA:01",
            rssi = -50,
            frequency = 2437,
            channel = 6,
            band = net.aieat.netswissknife.core.network.wifi.WifiBand.BAND_2_4GHZ,
            linkSpeedMbps = 144,
            txLinkSpeedMbps = 144,
            rxLinkSpeedMbps = 144,
            ipAddress = "192.168.1.5",
            standard = WifiStandard.WIFI_4,
            security = WifiSecurity.WPA2,
            ipv6Addresses = listOf("fe80::1", "2001:db8::5"),
            gateway = "192.168.1.1",
            dnsServers = listOf("192.168.1.1", "2001:4860:4860::8888")
        )
        val result = WifiScanResult(
            accessPoints = listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50)),
            channels = emptyList(),
            connectedNetwork = connection,
            scanTimestampMs = 0L,
            isWifiEnabled = true
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = fakeViewModel(WifiScanUiState.Success(result)))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule
            .onNodeWithText(context.getString(R.string.wifi_connected_network_header))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("fe80::1\n2001:db8::5").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.1", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText("192.168.1.1\n2001:4860:4860::8888", useUnmergedTree = true)
            .fetchSemanticsNode()
    }

    @Test
    fun unknownSecurity_showsUnknownIndicatorInsteadOfOpenLock() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiSecurityIndicator(WifiSecurity.UNKNOWN)
            }
        }

        composeRule.onNodeWithTag(WifiScreenTestTags.UNKNOWN_SECURITY_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(WifiScreenTestTags.UNKNOWN_SECURITY_ICON).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.wifi_security_unknown))
            .assertIsDisplayed()
    }

    @Test
    fun sortChip_tapCallsSetSortOrder() {
        val viewModel = fakeViewModel(successState(listOf(fakeAp("HomeNet", "AA:AA:AA:AA:AA:01", -50))))
        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(ApSortOrder.SSID.label).performClick()

        verify(exactly = 1) { viewModel.setSortOrder(ApSortOrder.SSID) }
    }

    @Test
    fun successState_frozenOrder_rendersAllNetworksInFrozenOrder() {
        // Natural SIGNAL order (descending RSSI) would be Charlie, Alpha, Bravo;
        // frozenOrder pins a different sequence that must still fully render.
        val alpha = fakeAp("Alpha", "AA:AA:AA:AA:AA:01", -70)
        val bravo = fakeAp("Bravo", "BB:BB:BB:BB:BB:01", -80)
        val charlie = fakeAp("Charlie", "CC:CC:CC:CC:CC:01", -50)
        val frozenOrder = listOf(
            "Bravo|${WifiSecurity.WPA2.name}",
            "Alpha|${WifiSecurity.WPA2.name}",
            "Charlie|${WifiSecurity.WPA2.name}"
        )

        composeRule.setContent {
            NetSwissKnifeTheme {
                WifiScanScreen(
                    viewModel = fakeViewModel(
                        successState(listOf(alpha, bravo, charlie), frozenOrder = frozenOrder)
                    )
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()

        // A LazyColumn scroll deep enough to require multiple incremental scroll
        // steps needs real animation frames to settle between each step; a
        // permanently paused clock stalls performScrollTo() partway through
        // (confirmed: scrolling to the 2nd item works, the 3rd -- further down --
        // hangs indefinitely). Auto-advance for the scroll/assert portion; this is
        // safe now that WifiHeader's refresh-spin transition is gated behind
        // autoRefresh (off in this test), so nothing infinite keeps it from idling.
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Bravo").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag(WifiScreenTestTags.CONTENT_LIST)
            .performScrollToIndex(WifiScreenTestTags.NETWORKS_START_INDEX + 2)
        composeRule.onNodeWithText("Charlie").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    private fun fakeAp(
        ssid: String,
        bssid: String,
        rssi: Int,
        security: WifiSecurity = WifiSecurity.WPA2
    ) = WifiAccessPoint(
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        frequency = 2437,
        channelWidthMhz = 20,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        channel = 6,
        band = net.aieat.netswissknife.core.network.wifi.WifiBand.BAND_2_4GHZ,
        standard = WifiStandard.WIFI_5,
        security = security,
        isConnected = false,
        vendor = "",
        centerFrequency0 = 0,
        centerFrequency1 = 0,
        timestampUs = 0L
    )

    private fun successState(
        accessPoints: List<WifiAccessPoint>,
        frozenOrder: List<String>? = null
    ) = WifiScanUiState.Success(
        result = WifiScanResult(
            accessPoints = accessPoints,
            channels = emptyList(),
            connectedNetwork = null,
            scanTimestampMs = 0L,
            isWifiEnabled = true
        ),
        frozenOrder = frozenOrder
    )

    private fun fakeViewModel(state: WifiScanUiState): WifiScanViewModel {
        val viewModel = mockk<WifiScanViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.autoRefresh } returns MutableStateFlow(false)
        every { viewModel.refreshIntervalMs } returns MutableStateFlow(30_000L)
        every { viewModel.expandedNetworks } returns MutableStateFlow(emptySet())
        every { viewModel.apDisappearedEvent } returns MutableStateFlow(null)
        return viewModel
    }
}
