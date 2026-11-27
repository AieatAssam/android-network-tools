package net.aieat.netswissknife.app.ui.screens.whois

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.messageRes
import net.aieat.netswissknife.core.network.HostValidator
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

/**
 * Message for an invalid query: the specific reason a host check finds, such as an IPv4 number above 255
 * or a space, else the general hint naming what the field accepts.
 */
@StringRes
internal fun whoisQueryProblemRes(query: String): Int {
    val problem = HostValidator.problemOf(query.trim())
    return problem?.messageRes() ?: R.string.whois_query_invalid
}
