package net.aieat.netswissknife.core.network.httprobe

/** Builds a POSIX-shell-quoted curl command from the request the user configured. */
object CurlExporter {
    /** Redirect following is disabled if cURL could replay caller-supplied sensitive data. */
    fun redirectFollowingSuppressed(request: HttpProbeRequest): Boolean =
        request.followRedirects && (
            request.headers.isNotEmpty() ||
                request.body != null ||
                request.method !in setOf(HttpMethod.GET, HttpMethod.HEAD)
        )

    fun build(request: HttpProbeRequest): String =
        buildString {
            append("curl")
            append(" -X ").append(quote(request.method.name))
            request.headers.forEach { (name, value) ->
                append(" -H ").append(quote("$name: $value"))
            }
            if (request.method.supportsBody && request.body != null) {
                append(" --data-raw ").append(quote(request.body))
            }
            if (request.followRedirects && !redirectFollowingSuppressed(request)) {
                val redirectProtocols =
                    if (request.url.startsWith("https://", ignoreCase = true)) "https" else "http,https"
                append(" --proto-redir '").append('=').append(redirectProtocols).append('\'')
                append(" --max-redirs ").append(RedirectPolicy.MAX_REDIRECTS)
                append(" -L")
            }
            append(' ').append(quote(request.url))
        }

    /** Single-quote shell escaping: embedded apostrophes close, escape, and reopen the quote. */
    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
