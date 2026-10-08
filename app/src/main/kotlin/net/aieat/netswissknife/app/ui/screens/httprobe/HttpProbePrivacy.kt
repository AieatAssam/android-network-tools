package net.aieat.netswissknife.app.ui.screens.httprobe

import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult

/** Keep visible redirect evidence useful without exposing credentials or token-bearing paths. */
internal fun safeRedirectDisplayValue(value: String): String = runCatching {
    val uri = java.net.URI(value)
    val authority = uri.rawAuthority ?: return@runCatching "[redirect address omitted]"
    val host = uri.host ?: return@runCatching "[redirect address omitted]"
    val safeAuthority = buildString {
        append(host)
        if (uri.port >= 0) append(":${uri.port}")
    }
    when {
        uri.isAbsolute -> "${uri.scheme}://$safeAuthority/[path omitted]"
        value.startsWith("//") -> "//$safeAuthority/[path omitted]"
        else -> "[relative redirect address omitted]"
    }
}.getOrElse { "[redirect address omitted]" }

private val sensitiveHeaderNames = setOf("cookie", "set-cookie", "authorization", "proxy-authorization")

/** Response headers suitable for user-visible display or sharing. */
internal fun visibleHttpResponseHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    headers.mapNotNull { (name, values) ->
        when {
            name.lowercase() in sensitiveHeaderNames -> null
            name.equals("Location", ignoreCase = true) -> name to values.map(::safeRedirectDisplayValue)
            else -> name to values
        }
    }.toMap()

internal fun buildHttpShareText(result: HttpProbeResult, sizeText: String): String = buildString {
    appendLine("HTTP – ${safeRedirectDisplayValue(result.request.url)}")
    appendLine("Final URL: ${safeRedirectDisplayValue(result.finalUrl)}")
    appendLine("Status: ${result.statusCode} ${result.statusMessage}")
    appendLine("Time: ${result.responseTimeMs}ms")
    appendLine("Size: $sizeText")
    if (result.redirectChain.isNotEmpty()) {
        appendLine()
        appendLine("Redirects:")
        result.redirectChain.forEach { url -> appendLine("  → ${safeRedirectDisplayValue(url)}") }
    }
    val safeHeaders = visibleHttpResponseHeaders(result.responseHeaders)
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
