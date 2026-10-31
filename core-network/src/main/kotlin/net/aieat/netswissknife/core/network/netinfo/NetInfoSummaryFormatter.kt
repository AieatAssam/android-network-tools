package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.NetworkResult

/**
 * Every user-visible word of the summary. The app fills these from strings.xml, so the
 * formatter itself stays free of Android resources.
 */
data class NetInfoSummaryLabels(
    val title: String,
    val transports: String,
    val metered: String,
    val roaming: String,
    val yes: String,
    val no: String,
    val wifi: String,
    val ssid: String,
    val ssidUnavailable: String,
    val signal: String,
    val linkSpeed: String,
    val frequency: String,
    val interfaceName: String,
    val addresses: String,
    val gateways: String,
    val dns: String,
    val mtu: String,
    val mac: String,
    val macUnavailable: String,
    val domains: String,
    val proxy: String,
    val noneValue: String,
    val publicIp: String,
    val publicIpv4: String,
    val publicIpv6: String,
    val hostname: String,
    val location: String,
    val asn: String,
    val notChecked: String,
    val notAvailable: String,
    val noActiveNetwork: String,
    val scopeName: (IpAddressScope) -> String,
    val transportName: (NetworkTransport) -> String,
)

/** Plain-text report for Copy and Share. Sections with no data still appear, with a stated value. */
object NetInfoSummaryFormatter {
    private const val SEPARATOR = ": "

    fun format(
        local: NetInfoSnapshot?,
        publicIp: PublicIpReport?,
        labels: NetInfoSummaryLabels,
    ): String =
        buildString {
            appendLine(labels.title)
            if (local == null || !local.hasActiveNetwork) {
                appendLine(labels.noActiveNetwork)
            } else {
                appendLocal(local, labels)
            }
            appendLine()
            appendPublic(publicIp, labels)
        }.trimEnd()

    private fun StringBuilder.appendLocal(
        local: NetInfoSnapshot,
        labels: NetInfoSummaryLabels,
    ) {
        line(labels.transports, local.transports.joinToString { labels.transportName(it) }.ifEmpty { labels.noneValue })
        line(labels.metered, NetInfoSummaryValues.yesNo(local.metered, labels))
        line(labels.roaming, NetInfoSummaryValues.yesNo(local.roaming, labels))
        local.wifi?.let { appendWifi(it, labels) }
        line(labels.interfaceName, local.interfaceName ?: labels.notAvailable)
        local.addresses.forEach { line(labels.addresses, NetInfoSummaryValues.addressText(it, labels)) }
        line(labels.gateways, local.gateways.joinToString().ifEmpty { labels.notAvailable })
        line(labels.dns, local.dnsServers.joinToString().ifEmpty { labels.notAvailable })
        line(labels.mtu, local.mtuBytes?.toString() ?: labels.notAvailable)
        local.mac?.let { line(labels.mac, NetInfoSummaryValues.macText(it, labels)) }
        line(labels.domains, local.searchDomains.joinToString().ifEmpty { labels.noneValue })
        line(labels.proxy, NetInfoSummaryValues.proxyText(local.proxy, labels))
    }

    private fun StringBuilder.appendWifi(
        wifi: WifiLinkDetails,
        labels: NetInfoSummaryLabels,
    ) {
        appendLine(labels.wifi)
        val ssid =
            when (val state = wifi.ssid) {
                is SsidState.Known -> state.ssid
                is SsidState.Unavailable -> ssidUnavailableText(state.reason, labels)
            }
        line(labels.ssid, ssid)
        line(labels.signal, wifi.rssiDbm?.let { "$it dBm" } ?: labels.notAvailable)
        line(labels.linkSpeed, wifi.linkSpeedMbps?.let { "$it Mbps" } ?: labels.notAvailable)
        line(labels.frequency, NetInfoSummaryValues.frequencyText(wifi, labels))
    }

    private fun ssidUnavailableText(
        reason: SsidUnavailableReason,
        labels: NetInfoSummaryLabels,
    ): String {
        val permissionNeeded = reason == SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED
        return if (permissionNeeded) labels.ssidUnavailable else labels.notAvailable
    }

    private fun StringBuilder.appendPublic(
        report: PublicIpReport?,
        labels: NetInfoSummaryLabels,
    ) {
        if (report == null) {
            line(labels.publicIp, labels.notChecked)
            return
        }
        appendLine(labels.publicIp)
        appendPublicFamily(labels.publicIpv4, report.ipv4, labels)
        appendPublicFamily(labels.publicIpv6, report.ipv6, labels)
    }

    private fun StringBuilder.appendPublicFamily(
        label: String,
        result: NetworkResult<PublicAddressInfo>,
        labels: NetInfoSummaryLabels,
    ) {
        when (result) {
            is NetworkResult.Success -> {
                val info = result.data
                line(label, info.address)
                info.hostname?.let { line(labels.hostname, it) }
                NetInfoSummaryValues.locationText(info)?.let { line(labels.location, it) }
                info.geo?.asn?.let { line(labels.asn, it) }
            }

            is NetworkResult.Error -> {
                line(label, labels.notAvailable)
            }
        }
    }

    private fun StringBuilder.line(
        label: String,
        value: String,
    ) {
        appendLine("$label$SEPARATOR$value")
    }
}
