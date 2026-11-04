package net.aieat.netswissknife.app.ui.screens.dns

/**
 * How a DNS answer is presented. A NOERROR answer is a success. NXDOMAIN is a definitive "no such
 * name" answer, which is information rather than a success, so it is neutral. Anything else
 * (server failure, refused, unknown codes) is a failure.
 */
enum class DnsResponseTone { SUCCESS, NOT_FOUND, FAILURE }

fun dnsResponseTone(rcode: String): DnsResponseTone =
    when (rcode) {
        "NOERROR" -> DnsResponseTone.SUCCESS
        "NXDOMAIN" -> DnsResponseTone.NOT_FOUND
        else -> DnsResponseTone.FAILURE
    }
