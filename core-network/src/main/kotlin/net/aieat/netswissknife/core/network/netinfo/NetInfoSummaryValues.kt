package net.aieat.netswissknife.core.network.netinfo

/** Value text for the summary: one line per fact, with explicit stated values for absent data. */
internal object NetInfoSummaryValues {
    private const val MHZ = "MHz"

    fun addressText(
        address: LocalAddress,
        labels: NetInfoSummaryLabels,
    ): String {
        val scope = labels.scopeName(address.scope)
        val mask = address.subnetMask?.let { ", mask $it" } ?: ""
        return "${address.address}/${address.prefixLength} ($scope$mask)"
    }

    fun macText(
        state: MacAddressState,
        labels: NetInfoSummaryLabels,
    ): String =
        when (state) {
            is MacAddressState.Known -> if (state.randomized) "${state.mac} (randomized)" else state.mac
            is MacAddressState.Unavailable -> labels.macUnavailable
        }

    fun frequencyText(
        wifi: WifiLinkDetails,
        labels: NetInfoSummaryLabels,
    ): String {
        val frequency = wifi.frequencyMhz ?: return labels.notAvailable
        val band = wifi.band?.let { " (${it.displayName})" } ?: ""
        val channel = wifi.channel?.let { ", channel $it" } ?: ""
        return "$frequency $MHZ$band$channel"
    }

    fun proxyText(
        proxy: ProxySettings?,
        labels: NetInfoSummaryLabels,
    ): String {
        if (proxy == null || !proxy.isSet) return labels.noneValue
        val pac = proxy.pacUrl?.takeIf { it.isNotBlank() }
        return pac ?: listOfNotNull(proxy.host, proxy.port?.toString()).joinToString(":")
    }

    fun locationText(info: PublicAddressInfo): String? {
        val geo = info.geo ?: return null
        val parts = listOf(geo.city, geo.country).filter { it.isNotBlank() }
        return parts.joinToString(", ").ifEmpty { null }
    }

    fun yesNo(
        value: Boolean,
        labels: NetInfoSummaryLabels,
    ): String = if (value) labels.yes else labels.no
}
