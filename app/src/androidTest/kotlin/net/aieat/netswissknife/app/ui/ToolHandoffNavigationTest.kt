package net.aieat.netswissknife.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import net.aieat.netswissknife.app.ui.navigation.navigateFromToolHandoff
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Protects result-screen handoffs from the top-level pop-to-Home navigation policy. */
@RunWith(AndroidJUnit4::class)
class ToolHandoffNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

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
}
