package net.aieat.netswissknife.app.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
    object Ping : NavRoutes("ping?host={host}&intent={intent}", "Ping", Icons.Default.NetworkCheck) {
        const val baseRoute = "ping"

        /** Keep the established bare Ping route while allowing host-target handoffs. */
        fun createRoute(host: String?): String = host
            ?.takeIf { it.isNotBlank() }
            ?.let { "$baseRoute?host=${Uri.encode(it)}" }
            ?: baseRoute

        fun createRoute(intent: ToolIntent): String {
            val target = intent.destination as? ToolDestination.HostTarget
                ?: throw IllegalArgumentException("Ping route requires a host destination")
            require(target.tool == HostTool.PING)
            return "$baseRoute?host=${Uri.encode(target.host.value)}&intent=${Uri.encode(ToolIntentCodec.encode(intent))}"
        }
    }
    object Traceroute : NavRoutes("traceroute", "Traceroute", Icons.Default.Router)
    object Ports : NavRoutes("ports?host={host}&intent={intent}", "Port Scanner", Icons.Default.TravelExplore) {
        const val baseRoute = "ports"

        fun createRoute(host: String?): String = host
            ?.takeIf { it.isNotBlank() }
            ?.let { "$baseRoute?host=${Uri.encode(it)}" }
            ?: baseRoute

        /** New typed handoffs use the versioned payload; the legacy host route remains supported. */
        fun createRoute(intent: ToolIntent): String {
            val target = intent.destination as? ToolDestination.HostTarget
                ?: throw IllegalArgumentException("Ports route requires a host destination")
            require(target.tool == HostTool.PORTS)
            return "$baseRoute?host=${Uri.encode(target.host.value)}&intent=${Uri.encode(ToolIntentCodec.encode(intent))}"
        }
    }
    object Lan : NavRoutes("lan", "LAN Scanner", Icons.Default.Devices)
    object Dns : NavRoutes("dns", "DNS Lookup", Icons.Default.Language)
    object DebugLogs : NavRoutes("debug_logs", "Debug Logs", Icons.Default.BugReport)
    object WifiScan : NavRoutes("wifi_scan", "Wi-Fi Scanner", Icons.Default.WifiFind)
    object TopologyDiscovery : NavRoutes("topology", "Network Topology", Icons.Default.AccountTree)
    object TlsInspector : NavRoutes("tls?intent={intent}&host={host}&port={port}", "TLS Inspector", Icons.Default.Lock) {
        const val baseRoute = "tls"

        fun createRoute(intent: ToolIntent): String {
            val target = intent.destination as? ToolDestination.HostTarget
                ?: throw IllegalArgumentException("TLS Inspector route requires a host destination")
            require(target.tool == HostTool.TLS && target.port != null)
            return "$baseRoute?intent=${Uri.encode(ToolIntentCodec.encode(intent))}" +
                "&host=${Uri.encode(target.host.value)}&port=${target.port.value}"
        }
    }
    object WhoisLookup : NavRoutes("whois", "WHOIS Lookup", Icons.AutoMirrored.Filled.ManageSearch)
    object HttpProbe : NavRoutes("httprobe?intent={intent}&host={host}", "HTTP Probe", Icons.Default.Http) {
        const val baseRoute = "httprobe"

        /** Typed host handoffs keep HTTP inputs separate from arbitrary URL route arguments. */
        fun createRoute(intent: ToolIntent): String {
            val target = intent.destination as? ToolDestination.HostTarget
                ?: throw IllegalArgumentException("HTTP Probe route requires a host destination")
            require(target.tool == HostTool.HTTP && target.port != null)
            return "$baseRoute?intent=${Uri.encode(ToolIntentCodec.encode(intent))}&host=${Uri.encode(target.host.value)}"
        }
    }
    object SubnetCalculator : NavRoutes("subnet", "Subnet Calc", Icons.Default.Calculate)
    object MdnsDiscovery : NavRoutes("mdns", "mDNS Browser", Icons.Default.CellTower)
    object SpeedTest : NavRoutes("speedtest", "Speed Test", Icons.Default.Speed)
    object WakeOnLan : NavRoutes("wol", "Wake-on-LAN", Icons.Default.PowerSettingsNew)
    object Settings : NavRoutes("settings", "Settings", Icons.Default.Settings)

    companion object {
        /** All navigable tool screens (excluding Home). */
        val allTools = listOf(
            ToolInfo("ping",       "Ping",          "Ping",  Icons.Default.NetworkCheck, "Reachability and round-trip latency"),
            ToolInfo("traceroute", "Traceroute",    "Trace", Icons.Default.Router,       "Network path hop analysis"),
            ToolInfo("ports",      "Port Scanner",  "Ports", Icons.Default.TravelExplore, "TCP port reachability"),
            ToolInfo("lan",        "LAN Scanner",   "LAN",   Icons.Default.Devices,       "Local device discovery"),
            ToolInfo("dns",        "DNS Lookup",    "DNS",   Icons.Default.Language,     "Resolve hostnames & records"),
            ToolInfo("wifi_scan",  "Wi-Fi Scanner", "Wi-Fi",     Icons.Default.WifiFind,     "Scan channels & access points"),
            ToolInfo("topology",   "Network Topology", "Topology", Icons.Default.AccountTree, "SNMP switch & neighbour discovery"),
            ToolInfo("tls",        "TLS Inspector",    "TLS",      Icons.Default.Lock,         "SSL/TLS certificate chain inspector"),
            ToolInfo("whois",      "WHOIS Lookup",  "WHOIS", Icons.AutoMirrored.Filled.ManageSearch, "Domain and IP registration lookup"),
            ToolInfo("httprobe",   "HTTP Probe",    "HTTP",  Icons.Default.Http,          "HTTP/HTTPS request tester with security header analysis"),
            ToolInfo("subnet",     "Subnet Calc",   "Subnet", Icons.Default.Calculate,     "IPv4 subnet calculator with binary breakdown and notation conversion"),
            ToolInfo("mdns",       "mDNS Browser",  "mDNS",  Icons.Default.CellTower,      "Discover LAN services via multicast DNS"),
            ToolInfo("speedtest",  "Speed Test",    "Speed", Icons.Default.Speed,          "Download, upload speed and latency via Cloudflare"),
            ToolInfo("wol",        "Wake-on-LAN",   "WOL",   Icons.Default.PowerSettingsNew, "Wake sleeping devices with a magic packet"),
        )

        /** Default pinned routes shown in the bottom nav (max MAX_PINNED). */
        val defaultPinnedRoutes = listOf("ping", "dns", "ports")
    }
}
