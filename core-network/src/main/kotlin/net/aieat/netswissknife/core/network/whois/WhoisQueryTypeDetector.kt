package net.aieat.netswissknife.core.network.whois

object WhoisQueryTypeDetector {

    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val IPV6_REGEX = Regex("""^[0-9a-fA-F:]+:[0-9a-fA-F:]*$""")
    private val ASN_REGEX = Regex("""^[Aa][Ss]\d+$""")

    fun detect(query: String): WhoisQueryType {
        val trimmed = query.trim()
        return when {
            ASN_REGEX.matches(trimmed) -> WhoisQueryType.ASN
            IPV4_REGEX.matches(trimmed) -> WhoisQueryType.IPV4
            isIPv6(trimmed) -> WhoisQueryType.IPV6
            else -> WhoisQueryType.DOMAIN
        }
    }

    private fun isIPv6(value: String): Boolean {
        if (!value.contains(':')) return false
        // Must have at least one colon-separated segment that looks hex
        val parts = value.split(':')
        if (parts.size < 3) return false
        return parts.all { it.isEmpty() || it.matches(Regex("[0-9a-fA-F]{1,4}")) }
    }
}
