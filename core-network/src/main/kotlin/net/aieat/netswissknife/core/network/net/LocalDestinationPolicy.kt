package net.aieat.netswissknife.core.network.net

import net.aieat.netswissknife.core.network.HostValidator
import java.net.InetAddress

/**
 * Decides whether a literal destination is local to one explicitly selected CIDR.
 * Missing, malformed, or family-mismatched CIDRs fail closed.
 */
class LocalDestinationPolicy(subnetCidr: String?) {
    private val subnet = subnetCidr?.let(::parseCidr)

    fun isLocal(ip: String): Boolean {
        val destination = parseAddress(ip) ?: return false
        val selectedSubnet = subnet ?: return false
        return destination.isLocalUseAddress() && selectedSubnet.contains(destination)
    }

    private enum class AddressFamily {
        IPV4,
        IPV6
    }

    private data class ParsedAddress(val bytes: ByteArray, val family: AddressFamily) {
        fun isLocalUseAddress(): Boolean = when (family) {
            AddressFamily.IPV4 -> isPrivateOrLinkLocalIpv4(bytes)
            AddressFamily.IPV6 -> if (isIpv4Mapped(bytes)) {
                isPrivateOrLinkLocalIpv4(bytes.copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES))
            } else {
                isUniqueLocalIpv6(bytes) || isLinkLocalIpv6(bytes) || isSiteLocalIpv6(bytes)
            }
        }
    }

    private data class ParsedSubnet(val address: ParsedAddress, val prefixLength: Int) {
        fun contains(candidate: ParsedAddress): Boolean {
            if (candidate.family != address.family) return false

            val wholeBytes = prefixLength / Byte.SIZE_BITS
            for (index in 0 until wholeBytes) {
                if (candidate.bytes[index] != address.bytes[index]) return false
            }

            val remainingBits = prefixLength % Byte.SIZE_BITS
            if (remainingBits == 0) return true

            val mask = (0xff shl (Byte.SIZE_BITS - remainingBits)) and 0xff
            return (candidate.bytes[wholeBytes].toInt() and mask) ==
                (address.bytes[wholeBytes].toInt() and mask)
        }
    }

    private companion object {
        const val IPV4_BYTES = 4
        const val IPV6_BYTES = 16
        const val IPV4_BITS = 32
        const val IPV6_BITS = 128

        fun parseCidr(cidr: String): ParsedSubnet? {
            val parts = cidr.trim().split('/')
            if (parts.size != 2 || parts[0].contains('%')) return null

            val address = parseAddress(parts[0]) ?: return null
            val addressBits = if (address.family == AddressFamily.IPV4) IPV4_BITS else IPV6_BITS
            val prefixLength = parts[1].toIntOrNull() ?: return null
            if (prefixLength !in 0..addressBits) return null

            return ParsedSubnet(address, prefixLength)
        }

        fun parseAddress(input: String): ParsedAddress? {
            val trimmed = input.trim()
            if (HostValidator.isValidIpv4(trimmed)) {
                val bytes = trimmed.split('.').map { it.toInt().toByte() }.toByteArray()
                return ParsedAddress(bytes, AddressFamily.IPV4)
            }

            if (!HostValidator.isValidIpv6(trimmed)) return null
            val unwrapped = trimmed
                .removePrefix("[")
                .removeSuffix("]")
                .substringBefore('%')
            // HostValidator has already established that this is a literal, so this
            // call parses IPv6 rather than triggering hostname resolution.
            val parsedBytes = InetAddress.getByName(unwrapped).address

            // Some JVMs expose an IPv4-mapped IPv6 literal as Inet4Address. Keep the
            // literal's IPv6 family so CIDR matching remains unambiguous.
            val bytes = if (parsedBytes.size == IPV4_BYTES) {
                ByteArray(IPV6_BYTES).also { mapped ->
                    mapped[10] = 0xff.toByte()
                    mapped[11] = 0xff.toByte()
                    parsedBytes.copyInto(mapped, destinationOffset = IPV6_BYTES - IPV4_BYTES)
                }
            } else {
                parsedBytes
            }
            return ParsedAddress(bytes, AddressFamily.IPV6)
        }

        fun isPrivateOrLinkLocalIpv4(bytes: ByteArray): Boolean {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254)
        }

        fun isIpv4Mapped(bytes: ByteArray): Boolean =
            bytes.take(10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() &&
                bytes[11] == 0xff.toByte()

        fun isUniqueLocalIpv6(bytes: ByteArray): Boolean =
            ((bytes[0].toInt() and 0xff) and 0xfe) == 0xfc

        fun isLinkLocalIpv6(bytes: ByteArray): Boolean =
            (bytes[0].toInt() and 0xff) == 0xfe &&
                ((bytes[1].toInt() and 0xff) and 0xc0) == 0x80

        fun isSiteLocalIpv6(bytes: ByteArray): Boolean =
            (bytes[0].toInt() and 0xff) == 0xfe &&
                ((bytes[1].toInt() and 0xff) and 0xc0) == 0xc0
    }
}
