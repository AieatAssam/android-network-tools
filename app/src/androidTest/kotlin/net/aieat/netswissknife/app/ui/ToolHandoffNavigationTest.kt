package net.aieat.netswissknife.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.aieat.netswissknife.app.ui.navigation.AppNavHostContentOverrides
import net.aieat.netswissknife.app.ui.navigation.AppNavHostWithContentOverrides
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.navigateFromToolHandoff
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.app.ui.navigation.ToolPort
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import org.junit.Rule
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Protects result-screen handoffs from the top-level pop-to-Home navigation policy. */
@RunWith(AndroidJUnit4::class)
class ToolHandoffNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun productionNavHost_deliversPortsArguments_andSystemBackRetainsLanResult() {
        val host = requireNotNull(ToolHost.parse("192.0.2.8"))
        val intent = ToolIntent(
            ToolDestination.HostTarget(HostTool.PORTS, host),
            ToolSource.LAN,
        )
        val destination = NavRoutes.Ports.createRoute(intent)

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                AppNavHostWithContentOverrides(
                    navController = navController,
                    contentOverrides = AppNavHostContentOverrides(
                        lan = { controller ->
                            val scanStarts = rememberSaveable { mutableIntStateOf(0) }
                            Column {
                                Text("LAN result screen")
                                Text("LAN scan starts: ${scanStarts.intValue}")
                                Button(onClick = { scanStarts.intValue++ }) { Text("Start fake LAN scan") }
                                Button(onClick = { controller.navigateFromToolHandoff(destination) }) {
                                    Text("Open ports for host")
                                }
                            }
                        },
                        ports = { entry ->
                            val routeHost = entry.arguments?.getString("host")
                            val decoded = entry.arguments?.getString("intent")?.let(ToolIntentCodec::decode)
                            val target = decoded?.destination as? ToolDestination.HostTarget
                            Column {
                                Text("Ports route host: $routeHost")
                                Text("Ports intent host: ${target?.host?.value}")
                                Text("Ports intent tool: ${target?.tool?.name}")
                                Text("Ports intent source: ${decoded?.source}")
                            }
                        },
                    ),
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithText("LAN Scanner").performClick()
        composeRule.onNodeWithText("Start fake LAN scan").performClick()
        composeRule.onNodeWithText("LAN scan starts: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Open ports for host").performClick()
        composeRule.onNodeWithText("Ports route host: 192.0.2.8").assertIsDisplayed()
        composeRule.onNodeWithText("Ports intent host: 192.0.2.8").assertIsDisplayed()
        composeRule.onNodeWithText("Ports intent tool: PORTS").assertIsDisplayed()
        composeRule.onNodeWithText("Ports intent source: LAN").assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.mainClock.advanceTimeBy(2_000L)

        composeRule.onNodeWithText("LAN result screen").assertIsDisplayed()
        composeRule.onNodeWithText("LAN scan starts: 1").assertIsDisplayed()
    }

    @Test
    fun handoffPushesDestinationAndBackReturnsToOriginatingResult() {
        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        Button(onClick = { navController.navigate("lan") }) {
                            Text("Open LAN results")
                        }
                    }
                    composable("lan") {
                        Column {
                            Text("LAN scan result")
                            Button(onClick = {
                                navController.navigateFromToolHandoff("ports")
                            }) {
                                Text("Scan ports")
                            }
                        }
                    }
                    composable("ports") {
                        Column {
                            Text("Port Scan")
                            Button(onClick = { navController.popBackStack() }) {
                                Text("Back to LAN")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Open LAN results").performClick()
        composeRule.onNodeWithText("LAN scan result").assertIsDisplayed()
        composeRule.onNodeWithText("Scan ports").performClick()
        composeRule.onNodeWithText("Port Scan").assertIsDisplayed()
        composeRule.onNodeWithText("Back to LAN").performClick()
        composeRule.onNodeWithText("LAN scan result").assertIsDisplayed()
    }

    @Test
    fun lanPingHandoffCarriesValidatedHostAndTypedIntentArguments() {
        val host = requireNotNull(ToolHost.parse("192.0.2.8"))
        val intent = ToolIntent(
            ToolDestination.HostTarget(HostTool.PING, host),
            ToolSource.LAN,
        )
        val encodedIntent = ToolIntentCodec.encode(intent)

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "lan") {
                    composable("lan") {
                        Button(onClick = {
                            navController.navigateFromToolHandoff(NavRoutes.Ping.createRoute(intent))
                        }) {
                            Text("Ping selected host")
                        }
                    }
                    composable(
                        route = NavRoutes.Ping.route,
                        arguments = listOf(
                            navArgument("host") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("intent") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        Column {
                            Text("Host: ${entry.arguments?.getString("host")}")
                            Text("Intent: ${entry.arguments?.getString("intent")}")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Ping selected host").performClick()
        composeRule.onNodeWithText("Host: 192.0.2.8").assertIsDisplayed()
        composeRule.onNodeWithText("Intent: $encodedIntent").assertIsDisplayed()
    }

    @Test
    fun lanTlsHandoffCarriesHostPortAndBackReturnsToLanResult() {
        val host = requireNotNull(ToolHost.parse("192.0.2.8"))
        val intent = ToolIntent(
            ToolDestination.HostTarget(HostTool.TLS, host, requireNotNull(ToolPort.parse(8443))),
            ToolSource.LAN,
        )
        val encodedIntent = ToolIntentCodec.encode(intent)

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "lan") {
                    composable("lan") {
                        Column {
                            Text("LAN scan result")
                            Button(onClick = {
                                navController.navigateFromToolHandoff(NavRoutes.TlsInspector.createRoute(intent))
                            }) {
                                Text("Inspect TLS")
                            }
                        }
                    }
                    composable(
                        route = NavRoutes.TlsInspector.route,
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
                            navArgument("port") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        Column {
                            Text("Host: ${entry.arguments?.getString("host")}")
                            Text("Port: ${entry.arguments?.getString("port")}")
                            Text("Intent: ${entry.arguments?.getString("intent")}")
                            Button(onClick = { navController.popBackStack() }) { Text("Back to LAN") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Inspect TLS").performClick()
        composeRule.onNodeWithText("Host: 192.0.2.8").assertIsDisplayed()
        composeRule.onNodeWithText("Port: 8443").assertIsDisplayed()
        composeRule.onNodeWithText("Intent: $encodedIntent").assertIsDisplayed()
        composeRule.onNodeWithText("Back to LAN").performClick()
        composeRule.onNodeWithText("LAN scan result").assertIsDisplayed()
    }

    @Test
    fun lanHttpHandoffCarriesHostPortAndBackReturnsToLanResult() {
        val host = requireNotNull(ToolHost.parse("192.0.2.8"))
        val intent = ToolIntent(
            ToolDestination.HostTarget(HostTool.HTTP, host, requireNotNull(ToolPort.parse(8080))),
            ToolSource.LAN,
        )
        val encodedIntent = ToolIntentCodec.encode(intent)

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "lan") {
                    composable("lan") {
                        Column {
                            Text("LAN scan result")
                            Button(onClick = {
                                navController.navigateFromToolHandoff(NavRoutes.HttpProbe.createRoute(intent))
                            }) { Text("Probe HTTP") }
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
                        val routeHost = entry.arguments?.getString("host")
                        val decoded = entry.arguments?.getString("intent")?.let(ToolIntentCodec::decode)
                        val target = (decoded?.destination as? ToolDestination.HostTarget)
                            ?.takeIf { it.tool == HostTool.HTTP && it.port?.value == 8080 }
                            ?.takeIf { ToolHost.parse(routeHost.orEmpty())?.canonical == it.host.canonical }
                        Column {
                            Text("Host: $routeHost")
                            Text("HTTP target: ${target?.host?.value}:${target?.port?.value}")
                            Text("Source: ${decoded?.source}")
                            Text("Encoded intent: ${entry.arguments?.getString("intent")}")
                            Button(onClick = { navController.popBackStack() }) { Text("Back to LAN") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Probe HTTP").performClick()
        composeRule.onNodeWithText("Host: 192.0.2.8").assertIsDisplayed()
        composeRule.onNodeWithText("HTTP target: 192.0.2.8:8080").assertIsDisplayed()
        composeRule.onNodeWithText("Source: LAN").assertIsDisplayed()
        composeRule.onNodeWithText("Encoded intent: $encodedIntent").assertIsDisplayed()
        composeRule.onNodeWithText("Back to LAN").performClick()
        composeRule.onNodeWithText("LAN scan result").assertIsDisplayed()
    }

    @Test
    fun lanWakeOnLanHandoffCarriesUnicastMacAndBackReturnsToLanResult() {
        val mac = requireNotNull(ToolMacAddress.parse("02-23-45-67-89-ab"))
        val intent = ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.LAN)
        val encodedIntent = ToolIntentCodec.encode(intent)

        composeRule.setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "lan") {
                    composable("lan") {
                        Column {
                            Text("LAN scan result")
                            Button(onClick = {
                                navController.navigateFromToolHandoff(NavRoutes.WakeOnLan.createRoute(intent))
                            }) { Text("Wake device") }
                        }
                    }
                    composable(
                        route = NavRoutes.WakeOnLan.route,
                        arguments = listOf(
                            navArgument("intent") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("mac") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val rawMac = entry.arguments?.getString("mac")
                        val decoded = entry.arguments?.getString("intent")?.let(ToolIntentCodec::decode)
                        val targetMac = (decoded?.destination as? ToolDestination.WakeOnLan)
                            ?.mac
                            ?.takeIf { rawMac?.let(ToolMacAddress::parse) == it }
                        Column {
                            Text("MAC: $rawMac")
                            Text("Typed MAC: ${targetMac?.value}")
                            Text("Source: ${decoded?.source}")
                            Text("Encoded intent: ${entry.arguments?.getString("intent")}")
                            Button(onClick = { navController.popBackStack() }) { Text("Back to LAN") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Wake device").performClick()
        composeRule.onNodeWithText("MAC: 02:23:45:67:89:AB").assertIsDisplayed()
        composeRule.onNodeWithText("Typed MAC: 02:23:45:67:89:AB").assertIsDisplayed()
        composeRule.onNodeWithText("Source: LAN").assertIsDisplayed()
        composeRule.onNodeWithText("Encoded intent: $encodedIntent").assertIsDisplayed()
        composeRule.onNodeWithText("Back to LAN").performClick()
        composeRule.onNodeWithText("LAN scan result").assertIsDisplayed()
    }

    @Test
    fun wakeOnLanRouteBuilderRejectsNonLanSource() {
        val mac = requireNotNull(ToolMacAddress.parse("02:23:45:67:89:AB"))
        assertThrows(IllegalArgumentException::class.java) {
            NavRoutes.WakeOnLan.createRoute(ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.MDNS))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavRoutes.WakeOnLan.createRoute(ToolIntent(ToolDestination.WakeOnLan(mac)))
        }
    }
}
