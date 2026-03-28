package net.aieat.netswissknife.core.network.dns

/**
 * DNS record types supported by the lookup tool, each with a human-readable
 * display name and description explaining what the record does.
 */
enum class DnsRecordType(
    val displayName: String,
    val description: String,
    val dnsTypeInt: Int
) {
    A(
        displayName = "A",
        description = "Maps a hostname to an IPv4 address (32-bit). " +
            "This is the most common record type used to resolve domain names to IP addresses.",
        dnsTypeInt = 1
    ),
    AAAA(
        displayName = "AAAA",
        description = "Maps a hostname to an IPv6 address (128-bit). " +
            "Used for modern dual-stack networks that support IPv6 connectivity.",
        dnsTypeInt = 28
    ),
    MX(
        displayName = "MX",
        description = "Mail Exchange — specifies mail servers responsible for accepting " +
            "email for the domain, along with a priority value (lower = higher priority).",
        dnsTypeInt = 15
    ),
    TXT(
        displayName = "TXT",
        description = "Text record — stores arbitrary human-readable or machine-readable text. " +
            "Commonly used for SPF (email authentication), DKIM keys, DMARC policies, and domain verification.",
        dnsTypeInt = 16
    ),
    CNAME(
        displayName = "CNAME",
        description = "Canonical Name — creates an alias from one hostname to another. " +
            "The resolver will follow the chain until it finds an A or AAAA record.",
        dnsTypeInt = 5
    ),
    NS(
        displayName = "NS",
        description = "Name Server — delegates a DNS zone to the given authoritative name servers. " +
            "These servers are responsible for all records within the zone.",
        dnsTypeInt = 2
    ),
    SOA(
        displayName = "SOA",
        description = "Start of Authority — provides administrative info about the DNS zone: " +
            "primary name server, responsible email, serial number, and refresh/retry/expiry timers.",
        dnsTypeInt = 6
    ),
    PTR(
        displayName = "PTR",
        description = "Pointer — used for reverse DNS lookups to map an IP address back to a hostname. " +
            "Query format: last-octet.third-octet.second-octet.first-octet.in-addr.arpa",
        dnsTypeInt = 12
    ),
    SRV(
        displayName = "SRV",
        description = "Service — specifies the host and port for specific services (e.g. SIP, XMPP). " +
            "Contains priority, weight, port, and target hostname.",
        dnsTypeInt = 33
    ),
    CAA(
        displayName = "CAA",
        description = "Certification Authority Authorization — controls which certificate authorities " +
            "are permitted to issue SSL/TLS certificates for the domain.",
        dnsTypeInt = 257
    );

    companion object {
        fun fromDisplayName(name: String): DnsRecordType? =
            entries.find { it.displayName.equals(name, ignoreCase = true) }
    }
}
