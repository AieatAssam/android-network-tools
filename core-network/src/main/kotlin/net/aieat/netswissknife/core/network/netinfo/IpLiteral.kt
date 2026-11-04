package net.aieat.netswissknife.core.network.netinfo

private const val IPV4_GROUPS = 4
private const val IPV4_MAX_DIGITS = 3
private const val IPV4_MAX_OCTET = 255
private const val IPV6_GROUPS = 8
private const val IPV6_MAX_HEX_DIGITS = 4
private const val HEX_RADIX = 16
private const val OCTET_SHIFT = 8
private const val GROUP_SHIFT = 16
private const val GROUP_MASK = 0xFFFF

/**
 * Strict literal parsers. They never resolve host names: a string that is not a
 * complete IPv4 or IPv6 literal yields null.
 */
internal object IpLiteral {
    /** IPv4 as its unsigned 32-bit value, or null. Leading zeros are rejected as ambiguous. */
    fun ipv4(text: String): Long? {
        val parts = text.split('.')
        if (parts.size != IPV4_GROUPS || !parts.all(::isOctet)) return null
        return parts.fold(0L) { acc, part -> (acc shl OCTET_SHIFT) or part.toLong() }
    }

    /** IPv6 as eight 16-bit groups, or null. An optional `%zone` suffix is validated but not kept. */
    fun ipv6(text: String): IntArray? {
        val zoneStart = text.indexOf('%')
        val zoneValid = zoneStart < 0 || zoneStart < text.length - 1
        val address = if (zoneStart >= 0) text.substring(0, zoneStart) else text
        val expanded = if (zoneValid && address.contains(':')) expandEmbeddedIpv4(address) else null
        return expanded?.let(::parseExpanded)
    }

    private fun parseExpanded(expanded: String): IntArray? {
        val compression = expanded.indexOf("::")
        if (compression != expanded.lastIndexOf("::")) return null
        return if (compression < 0) {
            groups(expanded)?.toIntArray()
        } else {
            compressed(expanded, compression)
        }
    }

    private fun isOctet(part: String): Boolean =
        part.isNotEmpty() &&
            part.length <= IPV4_MAX_DIGITS &&
            part.all { it in '0'..'9' } &&
            (part.length == 1 || part[0] != '0') &&
            part.toInt() <= IPV4_MAX_OCTET

    /** Rewrites a trailing dotted-quad as two hex groups so the rest of the parser sees only hex. */
    private fun expandEmbeddedIpv4(address: String): String? {
        if (!address.contains('.')) return address
        val tailStart = address.lastIndexOf(':') + 1
        val value = if (tailStart == 0) null else ipv4(address.substring(tailStart))
        return value?.let { address.substring(0, tailStart) + hexGroupPair(it) }
    }

    private fun hexGroupPair(value: Long): String {
        val high = (value shr GROUP_SHIFT).toString(HEX_RADIX)
        val low = (value and GROUP_MASK.toLong()).toString(HEX_RADIX)
        return "$high:$low"
    }

    private fun compressed(
        text: String,
        compression: Int,
    ): IntArray? {
        val head = text.substring(0, compression)
        val tail = text.substring(compression + 2)
        val left = if (head.isEmpty()) emptyList() else hexGroups(head)
        val right = if (tail.isEmpty()) emptyList() else hexGroups(tail)
        if (left == null || right == null || left.size + right.size >= IPV6_GROUPS) return null
        val result = IntArray(IPV6_GROUPS)
        left.forEachIndexed { index, value -> result[index] = value }
        right.forEachIndexed { index, value -> result[IPV6_GROUPS - right.size + index] = value }
        return result
    }

    private fun groups(text: String): List<Int>? = hexGroups(text)?.takeIf { it.size == IPV6_GROUPS }

    private fun hexGroups(text: String): List<Int>? {
        val parts = text.split(':')
        val valid = parts.all { it.isNotEmpty() && it.length <= IPV6_MAX_HEX_DIGITS && it.all(::isHex) }
        return if (valid) parts.map { it.toInt(HEX_RADIX) } else null
    }

    private fun isHex(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}
