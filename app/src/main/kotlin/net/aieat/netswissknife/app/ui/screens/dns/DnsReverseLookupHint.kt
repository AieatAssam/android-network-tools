package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult

/**
 * True when [result] answered nothing for an IP address queried as a forward type: an address has no
 * A or MX records of its own, and its hostname comes from a reverse (PTR) lookup instead.
 */
internal fun dnsSuggestsReverseLookup(result: DnsResult): Boolean {
    val name = result.domain.trim().trimEnd('.')
    val isAddress = HostValidator.isValidIpv4(name) || HostValidator.isValidIpv6(name)
    return isAddress && result.recordType != DnsRecordType.PTR && result.records.isEmpty()
}
