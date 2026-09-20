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
}
