package net.aieat.netswissknife.core.network.whois

/** The RDAP fields used by the WHOIS result view, normalized from RFC 9083 JSON. */
internal data class RdapResponse(
    val objectClassName: String,
    val handle: String? = null,
    val name: String? = null,
    val ldhName: String? = null,
    val unicodeName: String? = null,
    val ipVersion: String? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val startAutnum: Long? = null,
    val endAutnum: Long? = null,
    val country: String? = null,
    val events: List<RdapEvent> = emptyList(),
    val nameServers: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val entities: List<RdapEntity> = emptyList(),
    val delegationSigned: Boolean? = null,
)

internal data class RdapEvent(
    val action: String,
    val date: String,
)

internal data class RdapEntity(
    val roles: Set<String>,
    val fullName: String? = null,
    val organization: String? = null,
    val country: String? = null,
    val url: String? = null,
)
