package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.dns.DnsServer

/** A custom server error shows only after something is typed and it is not an IP address. */
internal fun customServerShowsError(
    server: DnsServer,
    address: String,
): Boolean {
    val trimmed = address.trim()
    return server is DnsServer.Custom && trimmed.isNotEmpty() && !isIpAddress(trimmed)
}

/** Presets always allow a lookup; a custom server needs an IP address first, so a blank one does not. */
internal fun dnsServerAcceptsLookup(
    server: DnsServer,
    address: String,
): Boolean = server !is DnsServer.Custom || isIpAddress(address.trim())

private fun isIpAddress(value: String): Boolean = HostValidator.isValidIpv4(value) || HostValidator.isValidIpv6(value)
