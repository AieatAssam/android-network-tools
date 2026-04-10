package net.aieat.netswissknife.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
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
import net.aieat.netswissknife.app.ui.screens.whois.WhoisScreen

// ── Transition helpers ────────────────────────────────────────────────────────

private const val ANIM_DURATION = 300

/** Screens slide in from the right on forward navigation. */
private fun enterTransition(): EnterTransition =
    slideInHorizontally(tween(ANIM_DURATION)) { it / 4 } +
    fadeIn(tween(ANIM_DURATION))

/** Screens slide out to the left on forward navigation. */
private fun exitTransition(): ExitTransition =
    slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } +
    fadeOut(tween(ANIM_DURATION))

/** Screens slide in from the left on back navigation. */
private fun popEnterTransition(): EnterTransition =
    slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } +
    fadeIn(tween(ANIM_DURATION))

/** Screens slide out to the right on back navigation. */
private fun popExitTransition(): ExitTransition =
    slideOutHorizontally(tween(ANIM_DURATION)) { it / 4 } +
    fadeOut(tween(ANIM_DURATION))

/** Home screen always fades in from the bottom for a distinct feel. */
private fun homeEnterTransition(): EnterTransition =
    slideInVertically(tween(ANIM_DURATION)) { it / 6 } +
    fadeIn(tween(ANIM_DURATION))

private fun homeExitTransition(): ExitTransition =
    slideOutVertically(tween(ANIM_DURATION)) { -it / 8 } +
    fadeOut(tween(ANIM_DURATION))

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
        composable(NavRoutes.DebugLogs.route)        { DebugLogScreen() }
        composable(NavRoutes.TopologyDiscovery.route) { TopologyDiscoveryScreen() }
        composable(NavRoutes.TlsInspector.route)      { TlsInspectorScreen() }
        composable(NavRoutes.WhoisLookup.route)       { WhoisScreen() }
        composable(NavRoutes.HttpProbe.route)         { HttpProbeScreen() }
    }
}
