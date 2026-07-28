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
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            })
        }

        composable(NavRoutes.Ping.route)       { PingScreen() }
        composable(NavRoutes.Traceroute.route) { TracerouteScreen() }
        composable(NavRoutes.Ports.route)      { PortsScreen() }
        composable(NavRoutes.Lan.route)        { LanScreen() }
        composable(NavRoutes.Dns.route)        { DnsScreen() }
        composable(NavRoutes.WifiScan.route)   { WifiScanScreen() }
        if (net.aieat.netswissknife.app.BuildConfig.DEBUG) {
            composable(NavRoutes.DebugLogs.route) { DebugLogScreen() }
        }
        composable(NavRoutes.TopologyDiscovery.route) { TopologyDiscoveryScreen() }
        composable(NavRoutes.TlsInspector.route)      { TlsInspectorScreen() }
        composable(NavRoutes.WhoisLookup.route)       { WhoisScreen() }
        composable(NavRoutes.HttpProbe.route)         { HttpProbeScreen() }
        composable(NavRoutes.SubnetCalculator.route)  { SubnetCalculatorScreen() }
        composable(NavRoutes.MdnsDiscovery.route)     { MdnsDiscoveryScreen() }
        composable(NavRoutes.SpeedTest.route)         { SpeedTestScreen() }
        composable(NavRoutes.WakeOnLan.route)         { WakeOnLanScreen() }
        composable(
            route            = NavRoutes.Settings.route,
            enterTransition  = { fadeIn(AppMotion.enter()) },
            exitTransition   = { fadeOut(AppMotion.exit()) },
            popEnterTransition  = { fadeIn(AppMotion.enter()) },
            popExitTransition   = { fadeOut(AppMotion.exit()) },
        ) { SettingsScreen() }
    }
}
