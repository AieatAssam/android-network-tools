package com.example.netswissknife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolInfo(
    val route: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val description: String
)

sealed class NavRoutes(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : NavRoutes("home", "Home", Icons.Default.Home)
    object Ping : NavRoutes("ping", "Ping", Icons.Default.NetworkCheck)
    object Traceroute : NavRoutes("traceroute", "Traceroute", Icons.Default.Router)
    object Ports : NavRoutes("ports", "Port Scanner", Icons.Default.Search)
    object Lan : NavRoutes("lan", "LAN Scanner", Icons.Default.Wifi)
    object Dns : NavRoutes("dns", "DNS Lookup", Icons.Default.Language)

    companion object {
        /** All navigable tool screens (excluding Home). */
        val allTools = listOf(
            ToolInfo("ping",       "Ping",         "Ping",  Icons.Default.NetworkCheck, "ICMP round-trip latency"),
            ToolInfo("traceroute", "Traceroute",   "Trace", Icons.Default.Router,       "Network path hop analysis"),
            ToolInfo("ports",      "Port Scanner", "Ports", Icons.Default.Search,       "TCP port reachability"),
            ToolInfo("lan",        "LAN Scanner",  "LAN",   Icons.Default.Wifi,         "Local device discovery"),
            ToolInfo("dns",        "DNS Lookup",   "DNS",   Icons.Default.Language,     "Resolve hostnames & records"),
        )

        /** Default pinned routes shown in the bottom nav (max MAX_PINNED). */
        val defaultPinnedRoutes = listOf("ping", "dns", "ports")
    }
}
