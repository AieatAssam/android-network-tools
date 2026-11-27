package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.NetworkResult

/** Linux interface address flags (IFA_F_*), as Android reports them on each link address. */
object InterfaceAddressFlags {
    private const val DEPRECATED = 0x20

    /** True when the preferred lifetime has ended: the address still works but is not used for new connections. */
    fun isDeprecated(flags: Int): Boolean = (flags and DEPRECATED) != 0
}

/** Addresses in use first, then deprecated ones, each group in its original order. */
fun List<LocalAddress>.preferredFirst(): List<LocalAddress> = sortedBy { it.deprecated }

/** Approximate location and ASN for one public address. */
data class PublicNetworkDetails(
    val location: String?,
    val asn: String?,
) {
    val isEmpty: Boolean
        get() = location == null && asn == null

    companion object {
        fun of(info: PublicAddressInfo): PublicNetworkDetails {
            val location = NetInfoSummaryValues.locationText(info)
            return PublicNetworkDetails(location, info.geo?.asn)
        }

        /**
         * The details both public addresses share, so they can be shown once. Null when only one
         * family has an address, when the details differ, or when there are none.
         */
        fun shared(report: PublicIpReport): PublicNetworkDetails? {
            val ipv4 = (report.ipv4 as? NetworkResult.Success)?.data
            val ipv6 = (report.ipv6 as? NetworkResult.Success)?.data
            val details = ipv4?.let(::of)
            return details?.takeIf { ipv6 != null && it == of(ipv6) && !it.isEmpty }
        }
    }
}
