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
            if (request.method == HttpMethod.HEAD) {
                // `-X HEAD` changes only the request method. Curl still reads a response body,
                // which can fail or hang when a server advertises Content-Length for a HEAD.
                append(" --head")
            } else {
                append(" -X ").append(quote(request.method.name))
            }
            request.headers.forEach { (name, value) ->
                append(" -H ").append(quote("$name: $value"))
            }
            if (request.method.supportsBody && request.body != null) {
                append(" --data-raw ").append(quote(request.body))
            }
            append(" --proto '=https'")
            if (request.followRedirects && !redirectFollowingSuppressed(request)) {
                append(" --proto-redir '=https'")
                append(" --max-redirs ").append(RedirectPolicy.MAX_REDIRECTS)
                append(" -L")
            }
            append(' ').append(quote(request.url))
        }

    /** Single-quote shell escaping: embedded apostrophes close, escape, and reopen the quote. */
    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
