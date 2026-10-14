package net.aieat.netswissknife.core.network.whois

import java.net.IDN
import java.util.Locale

data class NormalizedWhoisQuery(
    val value: String,
    val type: WhoisQueryType,
)

object WhoisQueryTypeDetector {

    private val ASN_REGEX = Regex("""^[Aa][Ss]\d+$""")
    private val IPV4_SHAPE_REGEX = Regex("""^\d+(\.\d+){3}$""")
    private val IPV6_GROUP_REGEX = Regex("""^[0-9a-fA-F]{1,4}$""")

    fun detect(query: String): WhoisQueryType {
        return normalize(query)?.type ?: WhoisQueryType.DOMAIN
    }

    /** Validates a WHOIS lookup key and returns a wire-safe canonical query. */
    fun normalize(query: String): NormalizedWhoisQuery? {
        if (query.isEmpty() || query.any { it.isISOControl() }) return null
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null

        // IDNA maps these Unicode separators to '.', but normalize first so a
        // Unicode root separator is treated exactly like an ASCII final dot.
        val separatorNormalized = trimmed
            .replace('\u3002', '.')
            .replace('\uff0e', '.')
            .replace('\uff61', '.')

        // A single final dot is a DNS root marker, not part of the registry query.
        val candidate = if (separatorNormalized.endsWith('.')) separatorNormalized.dropLast(1)
        else separatorNormalized
        if (candidate.isEmpty() || candidate.endsWith('.')) return null

        if (ASN_REGEX.matches(candidate)) {
            val asn = candidate.substring(2).toLongOrNull() ?: return null
            if (asn < 1L || asn > 4_294_967_295L) return null
            return NormalizedWhoisQuery("AS$asn", WhoisQueryType.ASN)
        }

        if (IPV4_SHAPE_REGEX.matches(candidate)) {
            val octets = parseIpv4(candidate) ?: return null
            return NormalizedWhoisQuery(octets.joinToString("."), WhoisQueryType.IPV4)
        }
        // Numeric dotted strings with any other component count are malformed IP
        // literals, not domains to pass through to a name resolver.
        if (candidate.contains('.') && candidate.all { it.isDigit() || it == '.' }) return null

        // A colon-bearing token must be a literal IPv6 address; never let malformed
        // address-like input fall through to the domain path (and DNS resolution).
        if (candidate.contains(':')) {
            val addressBytes = parseIpv6Literal(candidate) ?: return null
            return NormalizedWhoisQuery(canonicalIpv6(addressBytes), WhoisQueryType.IPV6)
        }

        val ascii = try {
            IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.isEmpty() || ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-')
            }
        ) return null
        return NormalizedWhoisQuery(ascii, WhoisQueryType.DOMAIN)
    }

    /** Parses numeric IPv6 text without invoking name resolution. */
    private fun parseIpv6Literal(value: String): ByteArray? {
        if ('%' in value) return null // scoped addresses are local-interface specific

        var address = value
        if ('.' in address) {
            val lastColon = address.lastIndexOf(':')
            if (lastColon < 0) return null
            val ipv4 = parseIpv4(address.substring(lastColon + 1)) ?: return null
            val high = (ipv4[0] shl 8) or ipv4[1]
            val low = (ipv4[2] shl 8) or ipv4[3]
            address = address.substring(0, lastColon + 1) + high.toString(16) + ":" + low.toString(16)
        }

        val compression = address.indexOf("::")
        if (compression >= 0 && address.indexOf("::", compression + 2) >= 0) return null

        val words = if (compression < 0) {
            if (address.startsWith(':') || address.endsWith(':')) return null
            val explicit = parseIpv6Groups(address) ?: return null
            if (explicit.size != 8) return null
            explicit
        } else {
            val left = parseIpv6Groups(address.substring(0, compression)) ?: return null
            val right = parseIpv6Groups(address.substring(compression + 2)) ?: return null
            val zeroCount = 8 - left.size - right.size
            if (zeroCount < 1) return null
            left + List(zeroCount) { 0 } + right
        }

        return ByteArray(16) { index ->
            val word = words[index / 2]
            if (index % 2 == 0) (word shr 8).toByte() else word.toByte()
        }
    }

    private fun parseIpv6Groups(value: String): List<Int>? {
        if (value.isEmpty()) return emptyList()
        val groups = value.split(':')
        if (groups.any { !IPV6_GROUP_REGEX.matches(it) }) {
            return null
        }
        return groups.map { it.toIntOrNull(16) ?: return null }
    }

    private fun canonicalIpv6(address: ByteArray): String {
        val groups = IntArray(8) { index ->
            ((address[index * 2].toInt() and 0xff) shl 8) or
                (address[index * 2 + 1].toInt() and 0xff)
        }
        var bestStart = -1
        var bestLength = 1
        var index = 0
        while (index < groups.size) {
            if (groups[index] != 0) {
                index++
                continue
            }
            val start = index
            while (index < groups.size && groups[index] == 0) index++
            val length = index - start
            if (length > bestLength) {
                bestStart = start
                bestLength = length
            }
        }
        if (bestStart < 0) return groups.joinToString(":") { it.toString(16) }

        val before = groups.take(bestStart).joinToString(":") { it.toString(16) }
        val after = groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
        return "$before::$after"
    }

    private fun parseIpv4(value: String): List<Int>? {
        if (!IPV4_SHAPE_REGEX.matches(value)) return null
        val octets = value.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return octets
    }
}
