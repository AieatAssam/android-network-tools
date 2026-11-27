package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.whois.WhoisResult

/** Title and secondary line of an IP or ASN result. */
internal data class IpAsnHeading(
    val title: String,
    val subtitle: String?,
)

/** The organisation names the block; the registry handle or name is secondary. */
internal fun ipAsnHeadingOf(result: WhoisResult): IpAsnHeading {
    val title = result.orgName ?: result.netName ?: result.query
    return IpAsnHeading(title = title, subtitle = result.netName?.takeIf { it != title })
}
