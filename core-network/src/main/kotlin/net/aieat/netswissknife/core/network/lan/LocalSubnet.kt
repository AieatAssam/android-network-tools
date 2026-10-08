package net.aieat.netswissknife.core.network.lan

import net.aieat.netswissknife.core.network.lan.SubnetUtils.InterfaceAddressCandidate

private typealias InterfaceAddressSource = () -> Sequence<InterfaceAddressCandidate>

/** Locates this device on its IPv4 network and picks a scannable slice of a large subnet. */
object LocalSubnet {
    /** Prefix of the slice offered when a whole network is too large to scan. */
    const val DEFAULT_SLICE_PREFIX = 24
    private const val MAX_PREFIX = 32

    /** IPv4 address of the interface that [SubnetUtils.getCurrentSubnet] reports, or null. */
    fun deviceAddress(): String? = deviceAddress(SubnetUtils::platformInterfaceAddressCandidates)

    internal fun deviceAddress(source: InterfaceAddressSource): String? = currentCandidate(source)?.address

    /** First usable address on an up, physical, non-tunnel interface; shared with subnet detection. */
    internal fun currentCandidate(source: InterfaceAddressSource): InterfaceAddressCandidate? =
        try {
            source()
                .filter { !it.interfaceIsLoopback && it.interfaceIsUp && !it.interfaceIsVirtual }
                .filter { candidate ->
                    val name = candidate.interfaceName.lowercase()
                    !name.contains("dummy") && !name.contains("tun") && !name.contains("p2p")
                }.filter { !it.addressIsLoopback }
                .firstOrNull { SubnetUtils.cidrOf(it.address, it.prefixLength) != null }
        } catch (_: Exception) {
            null
        }

    /**
     * Returns the /[slicePrefix] block of [cidr] that contains [hostIp], or the block at the
     * start of [cidr] when [hostIp] is missing or outside it. A [cidr] that is already
     * /[slicePrefix] or smaller is returned unchanged; malformed input returns null.
     */
    fun hostSlice(
        cidr: String,
        hostIp: String?,
        slicePrefix: Int = DEFAULT_SLICE_PREFIX,
    ): String? {
        val parts = cidr.trim().split("/")
        val prefix = parts.getOrNull(1)?.toIntOrNull()?.takeIf { parts.size == 2 && it in 0..MAX_PREFIX }
        val network = parseIpOrNull(parts.first())
        return when {
            prefix == null || network == null -> null
            prefix >= slicePrefix -> cidr.trim()
            else -> sliceAround(network, prefix, hostIp, slicePrefix)
        }
    }

    private fun sliceAround(
        network: Long,
        prefix: Int,
        hostIp: String?,
        slicePrefix: Int,
    ): String {
        val mask = SubnetUtils.maskForPrefix(prefix)
        val host = hostIp?.let(::parseIpOrNull)
        val anchor = if (host != null && (host and mask) == (network and mask)) host else network and mask
        return "${SubnetUtils.longToIp(anchor and SubnetUtils.maskForPrefix(slicePrefix))}/$slicePrefix"
    }

    private fun parseIpOrNull(ip: String): Long? = runCatching { SubnetUtils.parseIpToLong(ip) }.getOrNull()
}
