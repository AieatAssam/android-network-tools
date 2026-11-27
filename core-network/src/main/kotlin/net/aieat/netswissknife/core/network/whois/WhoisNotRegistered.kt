package net.aieat.netswissknife.core.network.whois

private val NO_RECORD_MARKERS = listOf("returned 0 objects", "no match for", "not found", "no data found")

/**
 * True when the answers received say the name has no record and no domain name was parsed from them.
 * Only answers that were received are passed in, so a failed query never counts as unregistered.
 */
internal fun isUnregisteredDomain(
    responses: List<String>,
    domainName: String?,
): Boolean =
    domainName == null &&
        responses.any { response -> NO_RECORD_MARKERS.any { response.contains(it, ignoreCase = true) } }
