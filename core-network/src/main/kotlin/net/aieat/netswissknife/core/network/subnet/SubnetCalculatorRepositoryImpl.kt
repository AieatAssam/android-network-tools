package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.NetworkResult

class SubnetCalculatorRepositoryImpl : SubnetCalculatorRepository {

    override fun calculate(input: String): NetworkResult<SubnetInfo> = try {
        val (ipStr, prefix) = parseInput(input.trim())
        val ipLong = parseIpToLong(ipStr)
        val maskLong = prefixToMask(prefix)
        val networkLong = ipLong and maskLong
        val broadcastLong = networkLong or maskLong.inv().and(0xFFFFFFFFL)
        val hostBits = 32 - prefix
        val totalHosts = if (hostBits == 32) 0x1_0000_0000L else 1L shl hostBits
        val usableHosts = when {
            prefix == 32 -> 1L
            prefix == 31 -> 0L
            else -> totalHosts - 2L
        }
        val firstHost = if (prefix >= 31) longToIp(networkLong) else longToIp(networkLong + 1)
        val lastHost = if (prefix >= 31) longToIp(broadcastLong) else longToIp(broadcastLong - 1)
        val maskHex = "0x%08X".format(maskLong)
        val networkAddress = longToIp(networkLong)

        NetworkResult.Success(
            SubnetInfo(
                inputIp = ipStr,
                prefixLength = prefix,
                networkAddress = networkAddress,
                broadcastAddress = longToIp(broadcastLong),
                firstHost = firstHost,
                lastHost = lastHost,
                subnetMask = longToIp(maskLong),
                wildcardMask = longToIp(maskLong.inv().and(0xFFFFFFFFL)),
                hexMask = maskHex,
                binaryMask = longToBinaryDotted(maskLong),
                binaryNetworkAddress = longToBinaryDotted(networkLong),
                binaryIpAddress = longToBinaryDotted(ipLong),
                totalHosts = totalHosts,
                usableHosts = usableHosts,
                hostBits = hostBits,
                ipClass = classOf(ipLong),
                isPrivate = isPrivate(ipLong),
                cidrNotation = "$networkAddress/$prefix",
            )
        )
    } catch (e: IllegalArgumentException) {
        NetworkResult.Error(e.message ?: "Invalid input")
    } catch (e: Exception) {
        NetworkResult.Error("Invalid input: ${e.message}")
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    /**
     * Returns (ipString, prefixLength).
     * Handles:
     *   "192.168.1.0/24"
     *   "192.168.1.0/255.255.255.0"
     *   "192.168.1.0 255.255.255.0"
     *   "192.168.1.0"
     */
    private fun parseInput(input: String): Pair<String, Int> {
        val slashIndex = input.indexOf('/')
        val spaceIndex = input.indexOf(' ')

        return when {
            slashIndex != -1 -> {
                val ip = input.substring(0, slashIndex).trim()
                val suffix = input.substring(slashIndex + 1).trim()
                val prefix = if (suffix.contains('.')) {
                    // dot-decimal mask
                    maskToPrefix(parseIpToLong(suffix))
                } else {
                    suffix.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid prefix: $suffix")
                }
                Pair(ip, prefix)
            }
            spaceIndex != -1 -> {
                val ip = input.substring(0, spaceIndex).trim()
                val mask = input.substring(spaceIndex + 1).trim()
                val prefix = maskToPrefix(parseIpToLong(mask))
                Pair(ip, prefix)
            }
            else -> Pair(input, 32)
        }
    }

    // ── Low-level helpers ──────────────────────────────────────────────────────

    private fun parseIpToLong(ip: String): Long {
        val parts = ip.split('.')
        require(parts.size == 4) { "Invalid IP address: $ip" }
        return parts.fold(0L) { acc, part ->
            val octet = part.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid octet '$part' in: $ip")
            require(octet in 0..255) { "Octet out of range in: $ip" }
            acc * 256L + octet
        }
    }

    private fun longToIp(addr: Long): String =
        "${(addr shr 24) and 0xFF}.${(addr shr 16) and 0xFF}.${(addr shr 8) and 0xFF}.${addr and 0xFF}"

    /** Converts a /prefix-length integer to a 32-bit mask as Long. */
    private fun prefixToMask(prefix: Int): Long {
        require(prefix in 0..32) { "Prefix length must be 0–32, got $prefix" }
        return if (prefix == 0) 0L
        else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }

    /** Converts a dotted-decimal mask Long to its prefix length. Validates contiguous bits. */
    private fun maskToPrefix(mask: Long): Int {
        require(mask and 0xFFFFFFFFL == mask) { "Mask out of range" }
        // Verify it's a valid (contiguous) subnet mask
        val inverted = mask.inv() and 0xFFFFFFFFL
        // inverted + 1 must be a power of two (or zero for /0)
        require(inverted == 0L || (inverted and (inverted + 1)) == 0L) {
            "Non-contiguous subnet mask"
        }
        var count = 0
        var m = mask
        while (m and 0x8000_0000L != 0L) {
            count++
            m = (m shl 1) and 0xFFFFFFFFL
        }
        return count
    }

    private fun longToBinaryDotted(addr: Long): String =
        (3 downTo 0).joinToString(".") { octetIdx ->
            val octet = ((addr shr (octetIdx * 8)) and 0xFF).toInt()
            octet.toString(2).padStart(8, '0')
        }

    private fun classOf(ip: Long): String = when {
        ip shr 28 == 0xFL -> "E"
        ip shr 28 >= 0xEL -> "D"
        ip shr 30 == 0x3L -> "C"
        ip shr 31 == 0x1L -> "B"
        else -> "A"
    }

    private fun isPrivate(ip: Long): Boolean {
        // 10.0.0.0/8
        if (ip shr 24 == 10L) return true
        // 172.16.0.0/12
        if (ip shr 20 == (172L shl 4) + 1) return true
        // 192.168.0.0/16
        if (ip shr 16 == (192L shl 8) + 168) return true
        // 127.0.0.0/8 (loopback)
        if (ip shr 24 == 127L) return true
        // 169.254.0.0/16 (link-local)
        if (ip shr 16 == (169L shl 8) + 254) return true
        return false
    }
}
