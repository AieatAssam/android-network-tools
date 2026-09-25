package net.aieat.netswissknife.core.network.traceroute

/** Pure numeric-literal validation and global-reachability filter for GeoIP lookups. */
internal object ReservedRanges {

    /** Returns true only for a syntactically valid, globally reachable IPv4/IPv6 literal. */
    fun isPublicGlobalLiteral(value: String): Boolean {
        parseIpv4(value)?.let { return isPublicIpv4(it) }
        val groups = parseIpv6(value) ?: return false
        mappedIpv4(groups)?.let { return isPublicIpv4(it) }
        return isPublicIpv6(groups)
    }

    private fun isPublicIpv4(address: IntArray): Boolean {
        val a = address[0]
        val b = address[1]
        val c = address[2]
        val d = address[3]

        // 192.0.0.9 and .10 are globally reachable exceptions within 192.0.0.0/24.
        if (a == 192 && b == 0 && c == 0 && d in 9..10) return true

        return when {
            a == 0 -> false                         // 0.0.0.0/8
            a == 10 -> false                        // RFC 1918
            a == 100 && b in 64..127 -> false       // Shared address space /10
            a == 127 -> false                       // Loopback
            a == 169 && b == 254 -> false           // Link-local
            a == 172 && b in 16..31 -> false        // RFC 1918
            a == 192 && b == 0 && c == 0 -> false   // IETF protocol assignments
            a == 192 && b == 0 && c == 2 -> false   // Documentation TEST-NET-1
            a == 192 && b == 88 && c == 99 -> false // Deprecated 6to4 relay anycast
            a == 192 && b == 168 -> false           // RFC 1918
            a == 198 && b in 18..19 -> false        // Benchmarking
            a == 198 && b == 51 && c == 100 -> false // Documentation TEST-NET-2
            a == 203 && b == 0 && c == 113 -> false // Documentation TEST-NET-3
            a in 224..239 -> false                  // Multicast
            a >= 240 -> false                       // Reserved, including limited broadcast
            else -> true
        }
    }

    private fun isPublicIpv6(groups: IntArray): Boolean {
        val first = groups[0]
        val second = groups[1]

        // Unspecified, loopback, and deprecated IPv4-compatible (::/96) literals.
        if (groups.take(6).all { it == 0 }) return false

        if ((first and 0xfe00) == 0xfc00) return false // Unique-local fc00::/7
        if ((first and 0xffc0) == 0xfe80) return false // Link-local fe80::/10
        if ((first and 0xff00) == 0xff00) return false // Multicast ff00::/8
        if ((first and 0xffc0) == 0xfec0) return false // Deprecated site-local fec0::/10
        if (first == 0x0100 && groups[1] == 0 && groups[2] == 0 && groups[3] == 0) {
            return false // Discard-only 100::/64
        }
        if (first == 0x0100 && groups[1] == 0 && groups[2] == 0 && groups[3] == 1) {
            return false // Dummy prefix 100:0:0:1::/64
        }
        if (first == 0x2001 && second == 0x0db8) return false // Documentation 2001:db8::/32
        if (first == 0x3fff && (second and 0xf000) == 0) return false // Documentation 3fff::/20
        if (first == 0x2002) return false // Deprecated 6to4 transition space

        // The well-known NAT64 prefix is globally reachable even though it is outside 2000::/3,
        // but only when the embedded IPv4 destination is itself globally reachable.
        if (first == 0x0064 && second == 0xff9b && groups.slice(2..5).all { it == 0 }) {
            return isPublicIpv4(ipv4FromLast32Bits(groups))
        }

        // Global unicast is 2000::/3. Other unicast-looking prefixes are reserved, future-use,
        // or otherwise not globally reachable and must remain closed to GeoIP requests.
        if (first !in 0x2000..0x3fff) return false

        if (first == 0x2001 && second <= 0x01ff) {
            return isGlobalExceptionIn2001SpecialBlock(groups)
        }

        return true
    }

    private fun isGlobalExceptionIn2001SpecialBlock(groups: IntArray): Boolean {
        val second = groups[1]
        return when {
            // PCP anycast, TURN anycast, and DNS-SD service registration anycast.
            second == 0x0001 && groups[2] == 0 && groups[3] == 0 && groups[4] == 0 &&
                groups[5] == 0 && groups[6] == 0 && groups[7] in 1..3 -> true
            second == 0x0003 -> true // AMT 2001:3::/32
            second == 0x0004 && groups[2] == 0x0112 -> true // AS112-v6 2001:4:112::/48
            second in 0x0020..0x002f -> true // ORCHIDv2 2001:20::/28
            second in 0x0030..0x003f -> true // Drone Remote ID 2001:30::/28
            else -> false
        }
    }

    private fun mappedIpv4(groups: IntArray): IntArray? {
        if (!groups.take(5).all { it == 0 } || groups[5] != 0xffff) return null
        return ipv4FromLast32Bits(groups)
    }

    private fun ipv4FromLast32Bits(groups: IntArray): IntArray =
        intArrayOf(
            groups[6] ushr 8,
            groups[6] and 0xff,
            groups[7] ushr 8,
            groups[7] and 0xff,
        )

    private fun parseIpv4(value: String): IntArray? {
        if (value.isEmpty() || value.any { it !in '0'..'9' && it != '.' }) return null
        val parts = value.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3 || (part.length > 1 && part[0] == '0')) return null
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            octets[index] = octet
        }
        return octets
    }

    /** Parses IPv6 syntax without InetAddress.getByName(), which may perform DNS resolution. */
    private fun parseIpv6(value: String): IntArray? {
        if (value.length < 2 || ':' !in value || '%' in value) return null
        if (value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' && it != '.' }) {
            return null
        }

        var expanded = value
        if ('.' in expanded) {
            val lastColon = expanded.lastIndexOf(':')
            if (lastColon < 0) return null
            val ipv4 = parseIpv4(expanded.substring(lastColon + 1)) ?: return null
            val high = (ipv4[0] shl 8) or ipv4[1]
            val low = (ipv4[2] shl 8) or ipv4[3]
            expanded = expanded.substring(0, lastColon + 1) +
                high.toString(16) + ":" + low.toString(16)
        }

        val compression = expanded.indexOf("::")
        if (compression >= 0 && expanded.indexOf("::", compression + 2) >= 0) return null
        val groups = if (compression >= 0) {
            val leftText = expanded.substring(0, compression)
            val rightText = expanded.substring(compression + 2)
            val left = parseHextets(leftText) ?: return null
            val right = parseHextets(rightText) ?: return null
            val zeroCount = 8 - left.size - right.size
            if (zeroCount < 1) return null
            left + List(zeroCount) { 0 } + right
        } else {
            parseHextets(expanded) ?: return null
        }
        if (groups.size != 8) return null
        return groups.toIntArray()
    }

    private fun parseHextets(side: String): List<Int>? {
        if (side.isEmpty()) return emptyList()
        val parts = side.split(':')
        if (parts.any { it.isEmpty() || it.length > 4 }) return null
        return parts.map { part -> part.toIntOrNull(16)?.takeIf { it in 0..0xffff } ?: return null }
    }
}
