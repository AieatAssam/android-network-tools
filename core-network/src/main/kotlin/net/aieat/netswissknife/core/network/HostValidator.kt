package net.aieat.netswissknife.core.network

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Utility for validating hostnames and IP addresses (IPv4 and IPv6).
 */
object HostValidator {
    private val ipv4Regex = Regex(
        """^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"""
    )
    private val hostnameRegex = Regex(
        """^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*$"""
    )

    fun isValidIpv4(address: String): Boolean = ipv4Regex.matches(address)

    fun isValidIpv6(address: String): Boolean {
        if (!address.contains(':')) return false
        return try {
            InetAddress.getByName(address) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    private val looksLikeIpv4 = Regex("""^\d+\.\d+\.\d+\.\d+$""")

    fun isValidHostname(host: String): Boolean {
        if (host.isBlank()) return false
        // If it looks like an IPv4 address (4 dot-separated groups of digits), require valid IPv4
        if (looksLikeIpv4.matches(host)) return isValidIpv4(host)
        // IPv6 addresses contain colons
        if (host.contains(':')) return isValidIpv6(host)
        return hostnameRegex.matches(host)
    }
}
