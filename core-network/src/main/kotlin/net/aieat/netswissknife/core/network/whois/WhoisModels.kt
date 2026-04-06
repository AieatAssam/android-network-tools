package net.aieat.netswissknife.core.network.whois

enum class WhoisQueryType { DOMAIN, IPV4, IPV6, ASN }

enum class WhoisServerRole { IANA, REGISTRY, REGISTRAR, RIR }

data class WhoisServer(
    val host: String,
    val role: WhoisServerRole
)

data class WhoisHop(
    val server: WhoisServer,
    val rawResponse: String,
    val queryTimeMs: Long,
    val referral: String?,
    val error: String? = null
)

data class WhoisResult(
    val query: String,
    val queryType: WhoisQueryType,
    val hops: List<WhoisHop>,

    val domainName: String?,
    val registrar: String?,
    val registrarUrl: String?,
    val registeredOn: Long?,
    val expiresOn: Long?,
    val updatedOn: Long?,
    val nameServers: List<String>,
    val statusCodes: List<String>,
    val registrantOrg: String?,
    val registrantCountry: String?,
    val dnssec: String?,

    val netName: String?,
    val netRange: String?,
    val orgName: String?,
    val country: String?,

    val totalQueryTimeMs: Long
)
