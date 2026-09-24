package net.aieat.netswissknife.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.aieat.netswissknife.app.ui.screens.DnsScreen
import net.aieat.netswissknife.app.ui.screens.HomeScreen
import net.aieat.netswissknife.app.ui.screens.LanScreen
import net.aieat.netswissknife.app.ui.screens.PingScreen
import net.aieat.netswissknife.app.ui.screens.PortsScreen
import net.aieat.netswissknife.app.ui.screens.TracerouteScreen
import net.aieat.netswissknife.app.ui.screens.WifiScanScreen
import net.aieat.netswissknife.app.ui.screens.debug.DebugLogScreen
import net.aieat.netswissknife.app.ui.screens.tls.TlsInspectorScreen
import net.aieat.netswissknife.app.ui.screens.topology.TopologyDiscoveryScreen
import net.aieat.netswissknife.app.ui.screens.httprobe.HttpProbeScreen
import net.aieat.netswissknife.app.ui.screens.subnet.SubnetCalculatorScreen
import net.aieat.netswissknife.app.ui.screens.settings.SettingsScreen
import net.aieat.netswissknife.app.ui.screens.mdns.MdnsDiscoveryScreen
import net.aieat.netswissknife.app.ui.screens.speedtest.SpeedTestScreen
import net.aieat.netswissknife.app.ui.screens.whois.WhoisScreen
import net.aieat.netswissknife.app.ui.screens.wol.WakeOnLanScreen
import net.aieat.netswissknife.app.ui.theme.AppMotion

/**
 * Select a tool as a top-level destination.
 *
 * Settings is intentionally not part of this root-level tool stack. Popping
 * back to Home before selecting a tool prevents Settings from remaining above
 * a tool in the back stack, while save/restore keeps each tool's own state.
 */
fun NavHostController.navigateToTool(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Push a child tool from the current result screen so Back returns to that result. */
fun NavHostController.navigateFromToolHandoff(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** Return from Settings to the screen that opened it, with a safe Home fallback. */
fun NavHostController.navigateBackFromSettings() {
    if (!popBackStack()) {
        navigate(NavRoutes.Home.route) {
            popUpTo(graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
}

// ── Transition helpers ────────────────────────────────────────────────────────
// Entering content uses emphasized-decelerate (settles in), exiting content uses
// emphasized-accelerate (leaves quickly) — see AppMotion / m3.material.io motion spec.

/** Screens slide in from the right on forward navigation. */
private fun enterTransition(): EnterTransition =
    slideInHorizontally(AppMotion.enter()) { it / 4 } +
    fadeIn(AppMotion.enter())

/** Screens slide out to the left on forward navigation. */
private fun exitTransition(): ExitTransition =
    slideOutHorizontally(AppMotion.exit()) { -it / 4 } +
    fadeOut(AppMotion.exit())

/** Screens slide in from the left on back navigation. */
private fun popEnterTransition(): EnterTransition =
    slideInHorizontally(AppMotion.enter()) { -it / 4 } +
    fadeIn(AppMotion.enter())

/** Screens slide out to the right on back navigation. */
private fun popExitTransition(): ExitTransition =
    slideOutHorizontally(AppMotion.exit()) { it / 4 } +
    fadeOut(AppMotion.exit())

/** Home screen always fades in from the bottom for a distinct feel. */
private fun homeEnterTransition(): EnterTransition =
    slideInVertically(AppMotion.enter()) { it / 6 } +
    fadeIn(AppMotion.enter())

private fun homeExitTransition(): ExitTransition =
    slideOutVertically(AppMotion.exit()) { -it / 8 } +
    fadeOut(AppMotion.exit())

// ── Navigation host ───────────────────────────────────────────────────────────

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController    = navController,
        startDestination = NavRoutes.Home.route,
        modifier         = modifier,
        enterTransition  = { enterTransition() },
        exitTransition   = { exitTransition() },
        popEnterTransition  = { popEnterTransition() },
        popExitTransition   = { popExitTransition() }
    ) {
        composable(
            route            = NavRoutes.Home.route,
            enterTransition  = { homeEnterTransition() },
            exitTransition   = { homeExitTransition() },
            popEnterTransition  = { homeEnterTransition() },
            popExitTransition   = { homeExitTransition() }
        ) {
            HomeScreen(onNavigate = { route ->
                navController.navigateToTool(route)
            })
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
        ) { PingScreen() }
        composable(NavRoutes.Traceroute.route) { TracerouteScreen() }
        composable(
            route = NavRoutes.Ports.route,
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
        ) { PortsScreen() }
        composable(NavRoutes.Lan.route)        {
            LanScreen(onNavigate = { route -> navController.navigateFromToolHandoff(route) })
        }
        composable(NavRoutes.Dns.route)        { DnsScreen() }
        composable(NavRoutes.WifiScan.route)   { WifiScanScreen() }
        if (net.aieat.netswissknife.app.BuildConfig.DEBUG) {
            composable(NavRoutes.DebugLogs.route) { DebugLogScreen() }
        }
        composable(NavRoutes.TopologyDiscovery.route) { TopologyDiscoveryScreen() }
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
        ) { TlsInspectorScreen() }
        composable(NavRoutes.WhoisLookup.route)       { WhoisScreen() }
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
        ) { HttpProbeScreen() }
        composable(NavRoutes.SubnetCalculator.route)  { SubnetCalculatorScreen() }
        composable(NavRoutes.MdnsDiscovery.route)     {
            MdnsDiscoveryScreen(onNavigate = { route -> navController.navigateFromToolHandoff(route) })
        }
        composable(NavRoutes.SpeedTest.route)         { SpeedTestScreen() }
        composable(NavRoutes.WakeOnLan.route)         { WakeOnLanScreen() }
        composable(
            route            = NavRoutes.Settings.route,
            enterTransition  = { fadeIn(AppMotion.enter()) },
            exitTransition   = { fadeOut(AppMotion.exit()) },
            popEnterTransition  = { fadeIn(AppMotion.enter()) },
            popExitTransition   = { fadeOut(AppMotion.exit()) },
        ) { SettingsScreen(onBack = navController::navigateBackFromSettings) }
    }
}
