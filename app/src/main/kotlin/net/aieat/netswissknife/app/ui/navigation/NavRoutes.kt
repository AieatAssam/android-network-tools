package net.aieat.netswissknife.app.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.ui.graphics.vector.ImageVector
import net.aieat.netswissknife.app.R

data class ToolInfo(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    val icon: ImageVector,
    @StringRes val descriptionRes: Int,
)

sealed class NavRoutes(
    val route: String,
) {
    object Home : NavRoutes("home")

    object Ping : NavRoutes("ping?host={host}&intent={intent}") {
        const val baseRoute = "ping"

        /** Keep the established bare Ping route while allowing host-target handoffs. */
        fun createRoute(host: String?): String =
            host
                ?.takeIf { it.isNotBlank() }
                ?.let { "$baseRoute?host=${Uri.encode(it)}" }
                ?: baseRoute

        fun createRoute(intent: ToolIntent): String {
            val target =
                intent.destination as? ToolDestination.HostTarget
                    ?: throw IllegalArgumentException("Ping route requires a host destination")
            require(target.tool == HostTool.PING)
            return "$baseRoute?host=${Uri.encode(target.host.value)}&intent=${Uri.encode(ToolIntentCodec.encode(intent))}"
        }
    }

    object Traceroute : NavRoutes("traceroute")

    object Ports : NavRoutes("ports?host={host}&intent={intent}") {
        const val baseRoute = "ports"

        fun createRoute(host: String?): String =
            host
                ?.takeIf { it.isNotBlank() }
                ?.let { "$baseRoute?host=${Uri.encode(it)}" }
                ?: baseRoute

        /** New typed handoffs use the versioned payload; the legacy host route remains supported. */
        fun createRoute(intent: ToolIntent): String {
            val target =
                intent.destination as? ToolDestination.HostTarget
                    ?: throw IllegalArgumentException("Ports route requires a host destination")
            require(target.tool == HostTool.PORTS)
            return "$baseRoute?host=${Uri.encode(target.host.value)}&intent=${Uri.encode(ToolIntentCodec.encode(intent))}"
        }
    }

    object Lan : NavRoutes("lan")

    object Dns : NavRoutes("dns")

    object DebugLogs : NavRoutes("debug_logs")

    object WifiScan : NavRoutes("wifi_scan")

    object TopologyDiscovery : NavRoutes("topology")

    object TlsInspector : NavRoutes("tls?intent={intent}&host={host}&port={port}") {
        const val baseRoute = "tls"

        fun createRoute(intent: ToolIntent): String {
            val target =
                intent.destination as? ToolDestination.HostTarget
                    ?: throw IllegalArgumentException("TLS Inspector route requires a host destination")
            require(target.tool == HostTool.TLS && target.port != null)
            return "$baseRoute?intent=${Uri.encode(ToolIntentCodec.encode(intent))}" +
                "&host=${Uri.encode(target.host.value)}&port=${target.port.value}"
        }
    }

    object WhoisLookup : NavRoutes("whois")

    object HttpProbe : NavRoutes("httprobe?intent={intent}&host={host}") {
        const val baseRoute = "httprobe"

        /** Typed host handoffs keep HTTP inputs separate from arbitrary URL route arguments. */
        fun createRoute(intent: ToolIntent): String {
            val target =
                intent.destination as? ToolDestination.HostTarget
                    ?: throw IllegalArgumentException("HTTP Probe route requires a host destination")
            require(target.tool == HostTool.HTTP && target.port != null)
            return "$baseRoute?intent=${Uri.encode(ToolIntentCodec.encode(intent))}&host=${Uri.encode(target.host.value)}"
        }
    }

    object SubnetCalculator : NavRoutes("subnet")

    object MdnsDiscovery : NavRoutes("mdns")

    object SpeedTest : NavRoutes("speedtest")

    object WakeOnLan : NavRoutes("wol?intent={intent}&mac={mac}") {
        const val baseRoute = "wol"

        fun createRoute(intent: ToolIntent): String {
            val destination =
                intent.destination as? ToolDestination.WakeOnLan
                    ?: throw IllegalArgumentException("Wake-on-LAN route requires a WOL destination")
            require(intent.source == ToolSource.LAN)
            return "$baseRoute?intent=${Uri.encode(ToolIntentCodec.encode(intent))}" +
                "&mac=${Uri.encode(destination.mac.value)}"
        }
    }

    object NetworkInfo : NavRoutes("netinfo")

    object MacLookup : NavRoutes("oui")

    object Settings : NavRoutes("settings")

    companion object {
        /** All navigable tool screens (excluding Home). */
        val allTools =
            listOf(
                ToolInfo(
                    "ping",
                    R.string.tool_ping_label,
                    R.string.tool_ping_short_label,
                    Icons.Default.NetworkCheck,
                    R.string.tool_ping_description,
                ),
                ToolInfo(
                    "traceroute",
                    R.string.tool_traceroute_label,
                    R.string.tool_traceroute_short_label,
                    Icons.Default.Router,
                    R.string.tool_traceroute_description,
                ),
                ToolInfo(
                    "ports",
                    R.string.tool_ports_label,
                    R.string.tool_ports_short_label,
                    Icons.Default.TravelExplore,
                    R.string.tool_ports_description,
                ),
                ToolInfo(
                    "lan",
                    R.string.tool_lan_label,
                    R.string.tool_lan_short_label,
                    Icons.Default.Devices,
                    R.string.tool_lan_description,
                ),
                ToolInfo(
                    "dns",
                    R.string.tool_dns_label,
                    R.string.tool_dns_short_label,
                    Icons.Default.Language,
                    R.string.tool_dns_description,
                ),
                ToolInfo(
                    "wifi_scan",
                    R.string.tool_wifi_label,
                    R.string.tool_wifi_short_label,
                    Icons.Default.WifiFind,
                    R.string.tool_wifi_description,
                ),
                ToolInfo(
                    "topology",
                    R.string.tool_topology_label,
                    R.string.tool_topology_short_label,
                    Icons.Default.AccountTree,
                    R.string.tool_topology_description,
                ),
                ToolInfo(
                    "tls",
                    R.string.tool_tls_label,
                    R.string.tool_tls_short_label,
                    Icons.Default.Lock,
                    R.string.tool_tls_description,
                ),
                ToolInfo(
                    "whois",
                    R.string.tool_whois_label,
                    R.string.tool_whois_short_label,
                    Icons.AutoMirrored.Filled.ManageSearch,
                    R.string.tool_whois_description,
                ),
                ToolInfo(
                    "httprobe",
                    R.string.tool_httprobe_label,
                    R.string.tool_httprobe_short_label,
                    Icons.Default.Http,
                    R.string.tool_httprobe_description,
                ),
                ToolInfo(
                    "subnet",
                    R.string.tool_subnet_label,
                    R.string.tool_subnet_short_label,
                    Icons.Default.Calculate,
                    R.string.tool_subnet_description,
                ),
                ToolInfo(
                    "mdns",
                    R.string.tool_mdns_label,
                    R.string.tool_mdns_short_label,
                    Icons.Default.CellTower,
                    R.string.tool_mdns_description,
                ),
                ToolInfo(
                    "speedtest",
                    R.string.tool_speedtest_label,
                    R.string.tool_speedtest_short_label,
                    Icons.Default.Speed,
                    R.string.tool_speedtest_description,
                ),
                ToolInfo(
                    "wol",
                    R.string.tool_wol_label,
                    R.string.tool_wol_short_label,
                    Icons.Default.PowerSettingsNew,
                    R.string.tool_wol_description,
                ),
                ToolInfo(
                    "netinfo",
                    R.string.tool_netinfo_label,
                    R.string.tool_netinfo_short_label,
                    Icons.Default.SettingsEthernet,
                    R.string.tool_netinfo_description,
                ),
                ToolInfo(
                    "oui",
                    R.string.tool_oui_label,
                    R.string.tool_oui_short_label,
                    Icons.Default.Fingerprint,
                    R.string.tool_oui_description,
                ),
            )

        /** Default pinned routes shown in the bottom nav (max MAX_PINNED). */
        val defaultPinnedRoutes = listOf("ping", "dns", "ports")
    }
}
