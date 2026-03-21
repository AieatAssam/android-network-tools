package com.example.netswissknife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoutes(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : NavRoutes("home", "Home", Icons.Default.Home)
    object Ping : NavRoutes("ping", "Ping", Icons.Default.NetworkCheck)
    object Traceroute : NavRoutes("traceroute", "Trace", Icons.Default.Router)
    object Ports : NavRoutes("ports", "Ports", Icons.Default.Search)
    object Lan : NavRoutes("lan", "LAN", Icons.Default.Wifi)
    object Dns : NavRoutes("dns", "DNS", Icons.Default.Language)

    companion object {
        val bottomNavItems = listOf(Home, Ping, Traceroute, Ports, Lan, Dns)
    }
}
