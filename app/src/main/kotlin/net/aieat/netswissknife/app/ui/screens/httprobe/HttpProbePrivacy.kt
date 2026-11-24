package net.aieat.netswissknife.app.ui.screens.httprobe

import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult

/** Text standing in for the parts of a redirect address that shared output leaves out. */
internal data class RedirectPlaceholders(
    val address: String,
    val path: String,
    val query: String,
) {
    companion object {
        val English = RedirectPlaceholders(
            address = "[redirect address omitted]",
            path = "[path omitted]",
            query = "[query omitted]",
        )
    }
}

/**
 * Keep visible redirect evidence useful without exposing credentials or token-bearing paths.
 * A root address shows "/" because there is no path to hide. Only an existing path or query is
 * marked as omitted. Credentials and fragments are always dropped.
 */
internal fun safeRedirectDisplayValue(
    value: String,
    placeholders: RedirectPlaceholders = RedirectPlaceholders.English,
): String = runCatching {
    val uri = java.net.URI(value)
    if (uri.rawAuthority == null) return@runCatching placeholders.address
    val host = uri.host ?: return@runCatching placeholders.address
    val safeAuthority = buildString {
        append(host)
        if (uri.port >= 0) append(":${uri.port}")
    }
    val pathPart =
        when {
            !uri.rawPath.isNullOrEmpty() && uri.rawPath != "/" -> "/${placeholders.path}"
            uri.rawQuery != null -> "/${placeholders.query}"
            else -> "/"
        }
    if (uri.isAbsolute) {
        "${uri.scheme}://$safeAuthority$pathPart"
    } else {
        "//$safeAuthority$pathPart"
    }
}.getOrElse { placeholders.address }

/** Case-insensitive lookup of the first value of a response header (HTTP/2 names are lowercase). */
internal fun httpHeaderValue(
    headers: Map<String, List<String>>,
    name: String,
): String? {
    val entry = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) } ?: return null
    return entry.value.firstOrNull()
}

private val sensitiveHeaderNames = setOf("cookie", "set-cookie", "authorization", "proxy-authorization")

/**
 * The address as shown on screen: userinfo and fragment removed, while the scheme, host, path and
 * query stay as the server sent them. A password in a URL is never useful on screen.
 */
internal fun displayUrl(value: String): String = runCatching {
    val uri = java.net.URI(value)
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    if (uri.rawAuthority == null) return@runCatching "${uri.rawPath.orEmpty()}$query"
    val host = uri.host ?: return@runCatching RedirectPlaceholders.English.address
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val prefix = if (uri.isAbsolute) "${uri.scheme}://" else "//"
    "$prefix$host$port${uri.rawPath.orEmpty()}$query"
}.getOrElse { RedirectPlaceholders.English.address }

/** Response headers shown on screen. Credential-bearing headers are dropped; Location is shown via [displayUrl]. */
internal fun httpResponseHeadersForDisplay(headers: Map<String, List<String>>): Map<String, List<String>> =
    headers
        .filterKeys { name -> name.lowercase() !in sensitiveHeaderNames }
        .mapValues { (name, values) ->
            if (name.equals("Location", ignoreCase = true)) values.map(::displayUrl) else values
        }

/** Response headers suitable for sharing: [httpResponseHeadersForDisplay] with redirect addresses reduced. */
internal fun visibleHttpResponseHeaders(
    headers: Map<String, List<String>>,
    placeholders: RedirectPlaceholders = RedirectPlaceholders.English,
): Map<String, List<String>> =
    httpResponseHeadersForDisplay(headers).mapValues { (name, values) ->
        if (name.equals("Location", ignoreCase = true)) {
            values.map { safeRedirectDisplayValue(it, placeholders) }
        } else {
            values
        }
    }

internal fun buildHttpShareText(
    result: HttpProbeResult,
    sizeText: String,
    placeholders: RedirectPlaceholders = RedirectPlaceholders.English,
): String = buildString {
    appendLine("HTTP: ${safeRedirectDisplayValue(result.request.url, placeholders)}")
    appendLine("Final URL: ${safeRedirectDisplayValue(result.finalUrl, placeholders)}")
    appendLine("Status: ${result.statusCode} ${result.statusMessage}")
    appendLine("Time: ${result.responseTimeMs}ms")
    appendLine("Size: $sizeText")
    if (result.redirectChain.isNotEmpty()) {
        appendLine()
        appendLine("Redirects:")
        result.redirectChain.forEach { url -> appendLine("  → ${safeRedirectDisplayValue(url, placeholders)}") }
    }
    val safeHeaders = visibleHttpResponseHeaders(result.responseHeaders, placeholders)
    if (safeHeaders.isNotEmpty()) {
        appendLine()
        appendLine("Response Headers:")
        safeHeaders.forEach { (key, values) -> appendLine("  $key: ${values.joinToString(", ")}") }
    }
    if (result.securityChecks.isNotEmpty()) {
        appendLine()
        appendLine("Security Checks:")
        result.securityChecks.forEach { check -> appendLine("  ${check.headerName}: ${check.rating.name}") }
    }
}
