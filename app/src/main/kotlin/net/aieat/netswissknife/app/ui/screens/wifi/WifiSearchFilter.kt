package net.aieat.netswissknife.app.ui.screens.wifi

import net.aieat.netswissknife.core.network.wifi.WifiNetwork

/**
 * Networks matching the search box: SSID, or any BSSID or vendor of one of their access
 * points, case-insensitive. A blank query keeps every network.
 */
internal fun wifiNetworksMatching(
    networks: List<WifiNetwork>,
    query: String,
): List<WifiNetwork> {
    val needle = query.trim()
    if (needle.isEmpty()) return networks
    return networks.filter { network ->
        network.displaySsid.contains(needle, ignoreCase = true) ||
            network.accessPoints.any { ap ->
                ap.bssid.contains(needle, ignoreCase = true) || ap.vendor.contains(needle, ignoreCase = true)
            }
    }
}
