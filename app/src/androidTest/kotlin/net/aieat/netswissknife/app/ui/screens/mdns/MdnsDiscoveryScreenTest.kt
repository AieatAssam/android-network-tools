package net.aieat.netswissknife.app.ui.screens.mdns

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.navigation.navigateFromToolHandoff
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue


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
    fun socketSetupFailure_showsErrorAndDismissResets() {
        val viewModel = fakeViewModel(
            state = MdnsDiscoveryUiState(error = "setsockopt failed: ENODEV")
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = viewModel)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText("setsockopt failed: ENODEV").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.mdns_error_dismiss)).performClick()

        verify(exactly = 1) { viewModel.reset() }
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

    @Test
    fun pingHostnameAction_navigatesWithValidatedMdnsIntentWithoutStartingProbe() {
        val service = fakeService("_http._tcp", "printer")
        val navigatedRoutes = mutableListOf<String>()
        val viewModel = fakeViewModel(
            state = MdnsDiscoveryUiState(
                services = listOf(service),
                servicesByType = mapOf(service.serviceType to listOf(service)),
            ),
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = viewModel, onNavigate = navigatedRoutes::add)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        assertTrue(navigatedRoutes.isEmpty())
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.mdns_ping_hostname_description, "printer.local"),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.mdns_ping_hostname))
            .performScrollTo().performClick()

        val uri = Uri.parse(navigatedRoutes.single())
        val intent = ToolIntentCodec.decode(requireNotNull(uri.getQueryParameter("intent")))
        val target = intent?.destination as? ToolDestination.HostTarget
        assertEquals(HostTool.PING, target?.tool)
        assertEquals(requireNotNull(ToolHost.parse("printer.local")), target?.host)
        assertEquals(ToolSource.MDNS, intent?.source)
    }

    @Test
    fun httpServiceAction_navigatesWithTypedHostPortSourceAndDoesNotSend() {
        val service = fakeService("_http._tcp", "printer")
        val navigatedRoutes = mutableListOf<String>()
        val viewModel = fakeViewModel(
            state = MdnsDiscoveryUiState(
                services = listOf(service),
                servicesByType = mapOf(service.serviceType to listOf(service)),
            ),
        )
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(viewModel = viewModel, onNavigate = navigatedRoutes::add)
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.mdns_http_probe_description, "printer"),
        ).performScrollTo().performClick()

        val route = navigatedRoutes.single()
        assertTrue(route.startsWith("httprobe?"))
        val parsedRoute = Uri.parse(route)
        val encoded = requireNotNull(parsedRoute.getQueryParameter("intent"))
        assertEquals("printer.local", parsedRoute.getQueryParameter("host"))
        val intent = ToolIntentCodec.decode(encoded)
        val target = intent?.destination as? ToolDestination.HostTarget
        assertEquals(HostTool.HTTP, target?.tool)
        assertEquals(requireNotNull(ToolHost.parse("printer.local")), target?.host)
        assertEquals(ToolPort.parse(8080), target?.port)
        assertEquals(ToolSource.MDNS, intent?.source)
    }

    @Test
    fun httpAction_isSuppressedForOtherServiceTypesAndInvalidHostsOrPorts() {
        val eligibleTypeButBadHost = fakeService("_http._tcp", "bad-host").copy(hostname = "bad host")
        val eligibleTypeButBadPort = fakeService("_http._tcp", "bad-port").copy(port = 65_536)
        val notHttp = fakeService("_https._tcp", "https-only")
        val services = listOf(eligibleTypeButBadHost, eligibleTypeButBadPort, notHttp)
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(
                    viewModel = fakeViewModel(
                        state = MdnsDiscoveryUiState(
                            services = services,
                            servicesByType = services.groupBy { it.serviceType },
                        ),
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithText(context.getString(R.string.mdns_http_probe)).assertDoesNotExist()
    }

    @Test
    fun httpHandoff_keepsMdnsBelowPrefilledProbeAndBackReturnsWithoutSending() {
        val service = fakeService("_http._tcp", "printer")
        val mdnsViewModel = fakeViewModel(
            state = MdnsDiscoveryUiState(
                services = listOf(service),
                servicesByType = mapOf(service.serviceType to listOf(service)),
                totalFound = 1,
            ),
        )
        every { mdnsViewModel.networkStatus } returns MutableStateFlow(NetworkStatus(hasLocalNetwork = true))
        var sends = 0

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = NavRoutes.MdnsDiscovery.route) {
                    composable(NavRoutes.MdnsDiscovery.route) {
                        MdnsDiscoveryScreen(viewModel = mdnsViewModel) { route ->
                            navController.navigateFromToolHandoff(route)
                        }
                    }
                    composable(
                        route = NavRoutes.HttpProbe.route,
                        arguments = listOf(
                            navArgument("intent") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("host") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val host = entry.arguments?.getString("host")
                        val intent = entry.arguments?.getString("intent")?.let(ToolIntentCodec::decode)
                        val target = (intent?.destination as? ToolDestination.HostTarget)
                            ?.takeIf { it.tool == HostTool.HTTP && it.port != null }
                            ?.takeIf { ToolHost.parse(host.orEmpty())?.canonical == it.host.canonical }
                        Column {
                            Text("HTTP Probe route host: $host")
                            Text("Decoded target: ${target?.host?.value}:${target?.port?.value}")
                            Text("Prefilled URL: ${target?.let { "http://${it.host.value}:${it.port!!.value}/" }}")
                            Text("Source: ${intent?.source}")
                            Button(onClick = { sends++ }) { Text("Send") }
                            Button(onClick = { navController.popBackStack() }) { Text("Back to mDNS") }
                            Text("Requests sent: $sends")
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithText("printer").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.mdns_http_probe_description, "printer"),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText("HTTP Probe route host: printer.local").assertIsDisplayed()
        composeRule.onNodeWithText("Decoded target: printer.local:8080").assertIsDisplayed()
        composeRule.onNodeWithText("Prefilled URL: http://printer.local:8080/").assertIsDisplayed()
        composeRule.onNodeWithText("Source: MDNS").assertIsDisplayed()
        composeRule.onNodeWithText("Requests sent: 0").assertIsDisplayed()

        composeRule.onNodeWithText("Back to mDNS").performClick()
        composeRule.onNodeWithText("printer").assertIsDisplayed()
        assertEquals(0, sends)
    }

    @Test
    fun pingResolvedAddressAction_ignoresInvalidServiceHostAndCarriesSelectedIp() {
        val service = fakeService("_http._tcp", "printer").copy(
            hostname = "192.168.1.52",
            ipAddresses = listOf("invalid address", "example.com", "192.168.1.51"),
        )
        val navigatedRoutes = mutableListOf<String>()
        composeRule.setContent {
            NetSwissKnifeTheme {
                MdnsDiscoveryScreen(
                    viewModel = fakeViewModel(
                        state = MdnsDiscoveryUiState(
                            services = listOf(service),
                            servicesByType = mapOf(service.serviceType to listOf(service)),
                        ),
                    ),
                    onNavigate = navigatedRoutes::add,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)

        assertTrue(navigatedRoutes.isEmpty())
        composeRule.onNodeWithText(context.getString(R.string.mdns_ping_hostname))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.mdns_ping_address, "192.168.1.51"))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.mdns_ping_address, "example.com"))
            .assertDoesNotExist()

        val uri = Uri.parse(navigatedRoutes.single())
        val intent = ToolIntentCodec.decode(requireNotNull(uri.getQueryParameter("intent")))
        val target = intent?.destination as? ToolDestination.HostTarget
        assertEquals(requireNotNull(ToolHost.parse("192.168.1.51")), target?.host)
        assertEquals(ToolSource.MDNS, intent?.source)
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
