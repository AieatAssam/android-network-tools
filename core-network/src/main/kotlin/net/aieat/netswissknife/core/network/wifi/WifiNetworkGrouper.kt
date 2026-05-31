package net.aieat.netswissknife.core.network.wifi

object WifiNetworkGrouper {
    fun group(accessPoints: List<WifiAccessPoint>): List<WifiNetwork> {
        val (hidden, visible) = accessPoints.partition { it.ssid.isBlank() }

        val visibleNetworks = visible
            .groupBy { it.ssid to it.security }
            .map { (key, aps) ->
                WifiNetwork(
                    ssid = key.first,
                    security = key.second,
                    accessPoints = aps.sortedByDescending { it.rssi }
                )
            }

        val hiddenNetworks = hidden.map { ap ->
            WifiNetwork(ssid = "", security = ap.security, accessPoints = listOf(ap))
        }

        return (visibleNetworks + hiddenNetworks).sortedByDescending { it.bestRssi }
    }
}
