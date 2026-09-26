package net.aieat.netswissknife.core.network.whois

import java.net.URI

/** Shared allowlist for registry-provided links that can be opened outside the app. */
internal object SafeWebUrl {
    fun isSafe(value: String): Boolean = runCatching {
        val uri = URI(value)
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)
}
