package net.aieat.netswissknife.core.network

/**
 * Utility for validating hostnames and IP addresses (IPv4 and IPv6).
 *
 * Every check here is a pure string parse. In particular [isValidIpv6] must not
 * call `InetAddress.getByName`: that resolver falls back to a blocking DNS query
 * for anything it cannot parse as a literal, and these validators are called
 * from the UI thread while the user is still typing.
 */
object HostValidator {
    private val ipv4Regex = Regex(
        """^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"""
    )
    private val hostnameRegex = Regex(
        """^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*$"""
    )
    private val hextetRegex = Regex("""^[0-9a-fA-F]{1,4}$""")

    /** Number of 16-bit groups in a full IPv6 address. */
    private const val IPV6_GROUPS = 8

    /** A trailing dotted-quad occupies the last two 16-bit groups. */
    private const val IPV4_TAIL_GROUPS = 2

    fun isValidIpv4(address: String): Boolean = ipv4Regex.matches(address)

    /**
     * True for an IPv6 literal, optionally wrapped in brackets and optionally
     * carrying a `%zone` suffix. Supports `::` zero-compression (at most once)
     * and an IPv4-mapped tail such as `::ffff:192.168.0.1`.
     */
    fun isValidIpv6(address: String): Boolean {
        var text = address
        if (text.startsWith("[") && text.endsWith("]")) {
            if (text.length < 3) return false
            text = text.substring(1, text.length - 1)
        }

        val zoneStart = text.indexOf('%')
        if (zoneStart >= 0) {
            // A zone id must qualify a non-empty address and must not be empty.
            if (zoneStart == 0 || zoneStart == text.length - 1) return false
            text = text.substring(0, zoneStart)
        }

        if (!text.contains(':')) return false

        val compressionStart = text.indexOf("::")
        return if (compressionStart >= 0) {
            // Only one run of "::" is allowed.
            if (text.indexOf("::", compressionStart + 1) >= 0) return false
            val head = text.substring(0, compressionStart)
            val tail = text.substring(compressionStart + 2)
            val headGroups = countGroups(head, allowIpv4Tail = false) ?: return false
            val tailGroups = countGroups(tail, allowIpv4Tail = true) ?: return false
            // "::" must stand in for at least one omitted group.
            headGroups + tailGroups < IPV6_GROUPS
        } else {
            countGroups(text, allowIpv4Tail = true) == IPV6_GROUPS
        }
    }

    /**
     * Counts the 16-bit groups in a colon-separated run, or null if any part is
     * not a valid hextet. An empty run contributes zero groups. When
     * [allowIpv4Tail] is set, a trailing dotted-quad counts as two groups.
     */
    private fun countGroups(run: String, allowIpv4Tail: Boolean): Int? {
        if (run.isEmpty()) return 0
        val parts = run.split(':')
        var groups = 0
        parts.forEachIndexed { index, part ->
            val isLast = index == parts.lastIndex
            when {
                isLast && allowIpv4Tail && part.contains('.') -> {
                    if (!isValidIpv4(part)) return null
                    groups += IPV4_TAIL_GROUPS
                }
                hextetRegex.matches(part) -> groups += 1
                else -> return null
            }
        }
        return groups
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
