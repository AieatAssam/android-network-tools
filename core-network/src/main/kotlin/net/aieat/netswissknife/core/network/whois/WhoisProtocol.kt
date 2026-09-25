package net.aieat.netswissknife.core.network.whois

/** Source selection for registration lookups. */
enum class WhoisProtocol {
    /** Try RDAP first and use WHOIS when RDAP cannot provide a result. */
    AUTO,
    /** Use RDAP only; report an error instead of falling back to WHOIS. */
    RDAP,
    /** Use the legacy port-43 WHOIS chain without an RDAP request. */
    WHOIS,
}
