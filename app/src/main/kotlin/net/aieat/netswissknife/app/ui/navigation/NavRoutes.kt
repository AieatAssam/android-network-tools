package net.aieat.netswissknife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
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
    object DebugLogs : NavRoutes("debug_logs", "Debug Logs", Icons.Default.BugReport)
    object WifiScan : NavRoutes("wifi_scan", "Wi-Fi Scanner", Icons.Default.WifiFind)
    object TopologyDiscovery : NavRoutes("topology", "Network Topology", Icons.Default.AccountTree)
    object TlsInspector : NavRoutes("tls", "TLS Inspector", Icons.Default.Lock)
    object WhoisLookup : NavRoutes("whois", "WHOIS Lookup", Icons.AutoMirrored.Filled.ManageSearch)
    object HttpProbe : NavRoutes("httprobe", "HTTP Probe", Icons.Default.Http)
    object SubnetCalculator : NavRoutes("subnet", "Subnet Calc", Icons.Default.Calculate)

    companion object {
        /** All navigable tool screens (excluding Home). */
        val allTools = listOf(
            ToolInfo("ping",       "Ping",          "Ping",  Icons.Default.NetworkCheck, "ICMP round-trip latency"),
            ToolInfo("traceroute", "Traceroute",    "Trace", Icons.Default.Router,       "Network path hop analysis"),
            ToolInfo("ports",      "Port Scanner",  "Ports", Icons.Default.Search,       "TCP port reachability"),
            ToolInfo("lan",        "LAN Scanner",   "LAN",   Icons.Default.Wifi,         "Local device discovery"),
            ToolInfo("dns",        "DNS Lookup",    "DNS",   Icons.Default.Language,     "Resolve hostnames & records"),
            ToolInfo("wifi_scan",  "Wi-Fi Scanner", "Wi-Fi",     Icons.Default.WifiFind,     "Scan channels & access points"),
            ToolInfo("topology",   "Network Topology", "Topology", Icons.Default.AccountTree, "SNMP switch & neighbour discovery"),
            ToolInfo("tls",        "TLS Inspector",    "TLS",      Icons.Default.Lock,         "SSL/TLS certificate chain inspector"),
            ToolInfo("whois",      "WHOIS Lookup",  "WHOIS", Icons.AutoMirrored.Filled.ManageSearch, "Domain and IP registration lookup"),
            ToolInfo("httprobe",   "HTTP Probe",    "HTTP",  Icons.Default.Http,          "HTTP/HTTPS request tester with security header analysis"),
            ToolInfo("subnet",     "Subnet Calc",   "Subnet", Icons.Default.Calculate,     "IPv4 subnet calculator with binary breakdown and notation conversion"),
        )

        /** Default pinned routes shown in the bottom nav (max MAX_PINNED). */
        val defaultPinnedRoutes = listOf("ping", "dns", "ports")
    }
}
