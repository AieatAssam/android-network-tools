package com.example.netswissknife.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.netswissknife.app.ui.screens.DnsScreen
import com.example.netswissknife.app.ui.screens.HomeScreen
import com.example.netswissknife.app.ui.screens.LanScreen
import com.example.netswissknife.app.ui.screens.PingScreen
import com.example.netswissknife.app.ui.screens.PortsScreen
import com.example.netswissknife.app.ui.screens.TracerouteScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route,
        modifier = modifier
    ) {
        composable(NavRoutes.Home.route) { HomeScreen() }
        composable(NavRoutes.Ping.route) { PingScreen() }
        composable(NavRoutes.Traceroute.route) { TracerouteScreen() }
        composable(NavRoutes.Ports.route) { PortsScreen() }
        composable(NavRoutes.Lan.route) { LanScreen() }
        composable(NavRoutes.Dns.route) { DnsScreen() }
    }
}
