package net.aieat.netswissknife.app.ui.screens.mdns

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
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
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers mDNS Browser's help sheet, incremental service discovery, Scan/Stop
 * button wiring, and grouped-by-type display.
 *
 * [MdnsDiscoveryScreen] requests `NEARBY_WIFI_DEVICES` on entry on API 36+
 * (Local Network Protections, see `LocalNetworkPermission.kt`) — pre-granting
 * it avoids a system permission dialog interrupting the test.
 */
@RunWith(AndroidJUnit4::class)
class MdnsDiscoveryScreenTest {
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
                MdnsDiscoveryScreen(viewModel = fakeViewModel())
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
            .onNodeWithText(context.getString(R.string.help_mdns_concept_heading))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun discovery_servicesAccumulateAsTheyArrive() {
        val first = fakeService("_http._tcp", "printer")
        val second = fakeService("_http._tcp", "nas")

        val stateFlow = MutableStateFlow(
            MdnsDiscoveryUiState(
                isScanning = true,
                services = listOf(first),
                servicesByType = mapOf("_http._tcp" to listOf(first))
            )
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = fakeViewModel(flow = stateFlow))
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("printer").assertIsDisplayed()

        stateFlow.value = stateFlow.value.copy(
            services = listOf(first, second),
            servicesByType = mapOf("_http._tcp" to listOf(first, second))
        )
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithText("printer").assertIsDisplayed()
        composeRule.onNodeWithText("nas").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun scanButton_startsScan() {
        val viewModel = fakeViewModel()
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.mdns_scan_button)).performClick()

        verify(exactly = 1) { viewModel.startScan(8_000L) }
    }

    @Test
    fun stopButton_whileScanning_stopsScan() {
        val viewModel = fakeViewModel(state = MdnsDiscoveryUiState(isScanning = true))
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.mdns_stop_button)).performClick()

        verify(exactly = 1) { viewModel.stopScan() }
    }

    @Test
    fun groupedResults_showServiceTypeHeader() {
        val service = fakeService("_http._tcp", "printer")
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(
                    viewModel = fakeViewModel(
                        state = MdnsDiscoveryUiState(
                            services = listOf(service),
                            servicesByType = mapOf("_http._tcp" to listOf(service))
                        )
                    )
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText("_http._tcp").assertIsDisplayed()
    }

    private fun fakeService(serviceType: String, name: String) = DiscoveredService(
        serviceType = serviceType,
        instanceName = "$name.$serviceType.local.",
        displayName = name,
        hostname = "$name.local",
        port = 8080,
        ipAddresses = listOf("192.168.1.50"),
        txtRecords = emptyMap()
    )

    private fun fakeViewModel(
        state: MdnsDiscoveryUiState? = null,
        flow: MutableStateFlow<MdnsDiscoveryUiState>? = null
    ): MdnsDiscoveryViewModel {
        val viewModel = mockk<MdnsDiscoveryViewModel>(relaxed = true)
        every { viewModel.uiState } returns (flow ?: MutableStateFlow(state ?: MdnsDiscoveryUiState()))
        return viewModel
    }
}
