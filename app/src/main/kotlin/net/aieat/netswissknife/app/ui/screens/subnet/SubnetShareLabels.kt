package net.aieat.netswissknife.app.ui.screens.subnet

import net.aieat.netswissknife.core.network.subnet.SubnetInfo

/** Localized field names for the share and copy text, resolved by the composable. */
internal data class SubnetShareLabels(
    val cidr: String,
    val networkAddress: String,
    val broadcast: String,
    val firstHost: String,
    val lastHost: String,
    val totalAddresses: String,
    val usableHosts: String,
    val subnetMask: String,
    val wildcardMask: String,
    val scope: String,
)

/**
 * Plain-text summary of a calculated subnet for copy and share. Numbers are plain digits
 * (no separators) so the text pastes cleanly into tickets and configuration tools.
 */
internal fun buildSubnetShareText(
    info: SubnetInfo,
    labels: SubnetShareLabels,
    scopeLabel: String,
): String {
    val fields =
        listOf(
            labels.cidr to info.cidrNotation,
            labels.networkAddress to info.networkAddress,
            labels.broadcast to info.broadcastAddress,
            labels.firstHost to info.firstHost,
            labels.lastHost to info.lastHost,
            labels.totalAddresses to info.totalHosts.toString(),
            labels.usableHosts to info.usableHosts.toString(),
            labels.subnetMask to info.subnetMask,
            labels.wildcardMask to info.wildcardMask,
            labels.scope to scopeLabel,
        )
    return fields.joinToString(separator = "\n") { (label, value) -> "$label: $value" }
}
