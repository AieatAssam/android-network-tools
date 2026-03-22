package com.example.netswissknife.core.network.dns

/**
 * Represents a DNS server to use for lookups.
 * Includes well-known public resolvers and support for custom server addresses.
 */
sealed class DnsServer(
    val displayName: String,
    val description: String
) {
    /**
     * Uses the device's configured DNS resolver.
     * [serverAddresses] must be populated by the app layer (via ConnectivityManager / LinkProperties)
     * before performing a lookup. If empty the repository falls back to Cloudflare.
     */
    data class System(val serverAddresses: List<String> = emptyList()) : DnsServer(
        displayName = "System DNS",
        description = "Uses the DNS server configured on your device"
    )

    /** Google Public DNS – 8.8.8.8 / 8.8.4.4 */
    object Google : DnsServer(
        displayName = "Google DNS",
        description = "8.8.8.8 — Google's fast & reliable public resolver"
    ) {
        const val PRIMARY = "8.8.8.8"
        const val SECONDARY = "8.8.4.4"
    }

    /** Cloudflare DNS – 1.1.1.1 / 1.0.0.1 */
    object Cloudflare : DnsServer(
        displayName = "Cloudflare DNS",
        description = "1.1.1.1 — Cloudflare's privacy-focused public resolver"
    ) {
        const val PRIMARY = "1.1.1.1"
        const val SECONDARY = "1.0.0.1"
    }

    /** OpenDNS – 208.67.222.222 / 208.67.220.220 */
    object OpenDns : DnsServer(
        displayName = "OpenDNS",
        description = "208.67.222.222 — Cisco OpenDNS with optional content filtering"
    ) {
        const val PRIMARY = "208.67.222.222"
        const val SECONDARY = "208.67.220.220"
    }

    /** Quad9 – 9.9.9.9 */
    object Quad9 : DnsServer(
        displayName = "Quad9",
        description = "9.9.9.9 — Security-focused resolver that blocks malicious domains"
    ) {
        const val PRIMARY = "9.9.9.9"
    }

    /** A user-specified DNS server IP address. */
    data class Custom(val address: String) : DnsServer(
        displayName = "Custom",
        description = address
    )

    companion object {
        val presets: List<DnsServer> = listOf(System(), Google, Cloudflare, OpenDns, Quad9)
    }
}
