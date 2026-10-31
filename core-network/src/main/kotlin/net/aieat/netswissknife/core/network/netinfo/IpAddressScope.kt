package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.subnet.IPV4_BITS
import net.aieat.netswissknife.core.network.subnet.OCTET_BITS
import net.aieat.netswissknife.core.network.subnet.OCTET_COUNT
import net.aieat.netswissknife.core.network.subnet.prefixToMask

enum class IpFamily { IPV4, IPV6 }

/** Where an address is used. Only addresses that are routable beyond the local network are GLOBAL. */
enum class IpAddressScope {
    /** 127.0.0.0/8 and ::1. */
    LOOPBACK,

    /** 169.254.0.0/16 and fe80::/10. */
    LINK_LOCAL,

    /** RFC 1918 private IPv4 space. */
    PRIVATE,

    /** RFC 6598 carrier-grade NAT space, 100.64.0.0/10. */
    SHARED,

    /** IPv6 unique local addresses, fc00::/7. */
    UNIQUE_LOCAL,

    /** Routable unicast space: public IPv4, and IPv6 2000::/3. */
    GLOBAL,

    /** 224.0.0.0/4 and ff00::/8. */
    MULTICAST,

    /** 0.0.0.0 and ::. */
    UNSPECIFIED,

    /** Reserved, documentation and other special-purpose blocks. */
    OTHER,
}

private data class Ipv4Block(
    val first: Long,
    val last: Long,
    val scope: IpAddressScope,
)

private fun ipv4Block(
    cidr: String,
    scope: IpAddressScope,
): Ipv4Block {
    val network = IpLiteral.ipv4(cidr.substringBefore('/')) ?: error("Bad block $cidr")
    val prefix = cidr.substringAfter('/').toInt()
    val size = 1L shl (IPV4_BITS - prefix)
    return Ipv4Block(first = network, last = network + size - 1, scope = scope)
}

// Ordered most specific first only where blocks overlap; the table below is disjoint.
private val IPV4_BLOCKS: List<Ipv4Block> =
    listOf(
        ipv4Block("0.0.0.0/8", IpAddressScope.OTHER),
        ipv4Block("10.0.0.0/8", IpAddressScope.PRIVATE),
        ipv4Block("100.64.0.0/10", IpAddressScope.SHARED),
        ipv4Block("127.0.0.0/8", IpAddressScope.LOOPBACK),
        ipv4Block("169.254.0.0/16", IpAddressScope.LINK_LOCAL),
        ipv4Block("172.16.0.0/12", IpAddressScope.PRIVATE),
        ipv4Block("192.168.0.0/16", IpAddressScope.PRIVATE),
        ipv4Block("224.0.0.0/4", IpAddressScope.MULTICAST),
        ipv4Block("240.0.0.0/4", IpAddressScope.OTHER),
    )

private const val IPV4_UNSPECIFIED = 0L
private const val IPV6_GROUP_COUNT = 8
private const val IPV6_LINK_LOCAL_MASK = 0xFFC0
private const val IPV6_LINK_LOCAL_PREFIX = 0xFE80
private const val IPV6_UNIQUE_LOCAL_MASK = 0xFE00
private const val IPV6_UNIQUE_LOCAL_PREFIX = 0xFC00
private const val IPV6_MULTICAST_MASK = 0xFF00
private const val IPV6_GLOBAL_MASK = 0xE000
private const val IPV6_GLOBAL_PREFIX = 0x2000
private const val OCTET_MASK = 0xFF

/** Classifies literal addresses. Names are never resolved, so this is safe on any thread. */
object IpAddressClassifier {
    /** Family of a complete literal, or null for anything else. */
    fun familyOf(text: String): IpFamily? =
        when {
            IpLiteral.ipv4(text) != null -> IpFamily.IPV4
            IpLiteral.ipv6(text) != null -> IpFamily.IPV6
            else -> null
        }

    /** Scope of a complete literal, or null for anything else. */
    fun scopeOf(text: String): IpAddressScope? {
        val v4 = IpLiteral.ipv4(text)
        val v6 = if (v4 == null) IpLiteral.ipv6(text) else null
        return when {
            v4 != null -> ipv4Scope(v4)
            v6 != null -> ipv6Scope(v6)
            else -> null
        }
    }

    /** The address without its IPv6 zone identifier, for display and copying. */
    fun displayAddress(address: String): String = address.substringBefore('%')

    /** Dotted subnet mask for an IPv4 prefix length, or null when the prefix is out of range. */
    fun ipv4MaskFromPrefix(prefix: Int): String? {
        if (prefix !in 0..IPV4_BITS) return null
        val mask = prefixToMask(prefix)
        return (OCTET_COUNT - 1 downTo 0).joinToString(".") { shift ->
            ((mask shr (shift * OCTET_BITS)) and OCTET_MASK.toLong()).toString()
        }
    }

    private fun ipv4Scope(value: Long): IpAddressScope =
        when {
            value == IPV4_UNSPECIFIED -> IpAddressScope.UNSPECIFIED
            else -> IPV4_BLOCKS.firstOrNull { value in it.first..it.last }?.scope ?: IpAddressScope.GLOBAL
        }

    private fun isLoopback(groups: IntArray): Boolean {
        val leadingZeros = groups.take(IPV6_GROUP_COUNT - 1).all { it == 0 }
        return leadingZeros && groups[IPV6_GROUP_COUNT - 1] == 1
    }

    private fun ipv6Scope(groups: IntArray): IpAddressScope {
        val first = groups[0]
        return when {
            groups.all { it == 0 } -> IpAddressScope.UNSPECIFIED
            isLoopback(groups) -> IpAddressScope.LOOPBACK
            (first and IPV6_LINK_LOCAL_MASK) == IPV6_LINK_LOCAL_PREFIX -> IpAddressScope.LINK_LOCAL
            (first and IPV6_UNIQUE_LOCAL_MASK) == IPV6_UNIQUE_LOCAL_PREFIX -> IpAddressScope.UNIQUE_LOCAL
            (first and IPV6_MULTICAST_MASK) == IPV6_MULTICAST_MASK -> IpAddressScope.MULTICAST
            (first and IPV6_GLOBAL_MASK) == IPV6_GLOBAL_PREFIX -> IpAddressScope.GLOBAL
            else -> IpAddressScope.OTHER
        }
    }
}

/** One address on a local interface, with its prefix and scope. */
data class LocalAddress(
    val address: String,
    val family: IpFamily,
    val prefixLength: Int,
    val scope: IpAddressScope,
) {
    /** Dotted mask for IPv4 only. IPv6 is described by its prefix length. */
    val subnetMask: String?
        get() = if (family == IpFamily.IPV4) IpAddressClassifier.ipv4MaskFromPrefix(prefixLength) else null
}
