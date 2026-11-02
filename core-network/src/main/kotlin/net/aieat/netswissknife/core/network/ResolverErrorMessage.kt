package net.aieat.netswissknife.core.network

import java.net.UnknownHostException

/**
 * Plain wording for a host that did not resolve, shared by every tool that resolves a name
 * (V6A-04). Ping shows the same sentence from its string resource; keep the two identical.
 * Returns null for failures that are not about name resolution, so callers keep their own text.
 */
fun resolverErrorMessage(error: Throwable, host: String): String? =
    if (error is UnknownHostException) {
        "Could not find an address for $host. Check the name, then try again."
    } else {
        null
    }
