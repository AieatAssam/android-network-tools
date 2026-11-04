package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.whois.WhoisQueryTypeDetector

/**
 * True when a non-blank query is not a domain, IP address or ASN. The check runs as the user types
 * (PHB-02), so an invalid query is shown inline and never sent as a lookup. A blank field is not
 * invalid yet; the Look up button is disabled for it.
 */
internal fun isWhoisQueryInvalid(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.isNotEmpty() && WhoisQueryTypeDetector.normalize(trimmed) == null
}
