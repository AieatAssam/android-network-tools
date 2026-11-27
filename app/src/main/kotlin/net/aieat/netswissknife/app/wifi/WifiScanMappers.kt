package net.aieat.netswissknife.app.wifi

/** Platform-free representation of addresses found on an active link. */
data class WifiLinkAddressMapping(
    val ipv4Address: String,
    val ipv6Addresses: List<String>
)

object WifiConnectionInfoMapper {
    /** Maps CIDR-formatted LinkAddress values without importing Android types. */
    fun mapLinkAddresses(addresses: Iterable<String>): WifiLinkAddressMapping {
        val normalized = addresses.mapNotNull { raw ->
            raw.substringBefore('/').trim().takeIf { it.isNotEmpty() }
        }
        return WifiLinkAddressMapping(
            ipv4Address = normalized.firstOrNull { ':' !in it }.orEmpty(),
            ipv6Addresses = normalized.filter { ':' in it }
        )
    }

    /**
     * Picks one default gateway so the value does not change between refreshes: the lowest IPv4
     * candidate when there is one, otherwise the lowest IPv6 candidate. Input order is ignored.
     */
    fun selectGateway(candidates: Iterable<String>): String? {
        val normalized = candidates.map { it.trim() }.filter { it.isNotEmpty() }
        return normalized.filter { ':' !in it }.minOrNull() ?: normalized.minOrNull()
    }
}

/**
 * Keeps one entry per BSSID. A BSSID names a single radio, so a second entry for it is the same
 * access point seen again, often as a hidden-SSID copy. The named entry wins, then the strongest
 * signal. Entries without a BSSID are kept unchanged.
 */
internal fun <T> keepOnePerBssid(
    items: List<T>,
    bssid: (T) -> String,
    isNamed: (T) -> Boolean,
    rssi: (T) -> Int,
): List<T> {
    val withoutBssid = items.filter { bssid(it).isBlank() }
    val chosen =
        items
            .filter { bssid(it).isNotBlank() }
            .groupBy { bssid(it) }
            .values
            .map { group -> group.maxWith(compareBy<T>({ isNamed(it) }, { rssi(it) })) }
    return chosen + withoutBssid
}
