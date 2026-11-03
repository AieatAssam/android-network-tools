package net.aieat.netswissknife.core.network.subnet

/**
 * Address scope of a whole subnet. The classification uses the network and broadcast
 * addresses together: a subnet inside one special-purpose block takes that block's scope,
 * a subnet that overlaps several blocks (or any block without being inside it) is [MIXED].
 */
enum class Ipv4Scope {
    /** RFC 1918 private space (10/8, 172.16/12, 192.168/16). */
    PRIVATE,

    /** RFC 6598 shared address space for carrier-grade NAT (100.64/10). */
    SHARED,

    /** 127/8. */
    LOOPBACK,

    /** RFC 3927 link-local (169.254/16). */
    LINK_LOCAL,

    /** RFC 1122 "this network" (0/8). */
    THIS_NETWORK,

    /** RFC 5737 documentation ranges. */
    DOCUMENTATION,

    /** RFC 2544 benchmarking (198.18/15). */
    BENCHMARK,

    /** 224/4. */
    MULTICAST,

    /** 240/4, including 255.255.255.255. */
    RESERVED,

    /** Routable internet space with none of the blocks above. */
    PUBLIC,

    /** Spans more than one scope, e.g. 0.0.0.0/0. */
    MIXED,
}

private class ScopeBlock(
    val first: Long,
    val last: Long,
    val scope: Ipv4Scope,
)

/** Builds a block from CIDR text such as "10.0.0.0/8". */
private fun scopeBlock(
    cidr: String,
    scope: Ipv4Scope,
): ScopeBlock {
    val (address, prefix) = cidr.split('/')
    val first = parseIpv4(address)
    val last = first + (1L shl (IPV4_BITS - prefix.toInt())) - 1
    return ScopeBlock(first = first, last = last, scope = scope)
}

// The blocks are disjoint, so at most one can contain a given subnet.
private val SCOPE_BLOCKS: List<ScopeBlock> =
    listOf(
        scopeBlock("0.0.0.0/8", Ipv4Scope.THIS_NETWORK), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("10.0.0.0/8", Ipv4Scope.PRIVATE), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("100.64.0.0/10", Ipv4Scope.SHARED), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("127.0.0.0/8", Ipv4Scope.LOOPBACK), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("169.254.0.0/16", Ipv4Scope.LINK_LOCAL), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("172.16.0.0/12", Ipv4Scope.PRIVATE), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("192.0.2.0/24", Ipv4Scope.DOCUMENTATION), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("192.168.0.0/16", Ipv4Scope.PRIVATE), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("198.18.0.0/15", Ipv4Scope.BENCHMARK), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("198.51.100.0/24", Ipv4Scope.DOCUMENTATION), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("203.0.113.0/24", Ipv4Scope.DOCUMENTATION), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("224.0.0.0/4", Ipv4Scope.MULTICAST), // NOSONAR: IANA special-purpose block (RFC 6890)
        scopeBlock("240.0.0.0/4", Ipv4Scope.RESERVED), // NOSONAR: IANA special-purpose block (RFC 6890)
    )

/** Scope of the inclusive address range [first]..[last]. */
internal fun ipv4ScopeOf(
    first: Long,
    last: Long,
): Ipv4Scope {
    val contained = SCOPE_BLOCKS.firstOrNull { first >= it.first && last <= it.last }
    if (contained != null) return contained.scope
    val overlaps = SCOPE_BLOCKS.any { first <= it.last && last >= it.first }
    return if (overlaps) Ipv4Scope.MIXED else Ipv4Scope.PUBLIC
}
