package net.aieat.netswissknife.app.ui.screens.httprobe

/** Characters of a response body the Body tab renders. Larger bodies are cut before any formatting. */
internal const val BODY_PREVIEW_LIMIT_CHARS = 32_000

/** The text shown for a body. [truncated] is true when the preview cut off part of the body. */
internal data class BodyPreview(
    val text: String?,
    val truncated: Boolean,
)

internal fun previewBody(
    body: String?,
    limit: Int = BODY_PREVIEW_LIMIT_CHARS,
): BodyPreview =
    when {
        body == null -> BodyPreview(null, false)
        body.length <= limit -> BodyPreview(body, false)
        else -> BodyPreview(body.substring(0, limit), true)
    }

/** True for `application/json`, `text/json` and any `+json` media type. Parameters are ignored. */
internal fun isJsonResponse(headers: Map<String, List<String>>): Boolean {
    val mediaType =
        headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return false
    return mediaType == "application/json" || mediaType == "text/json" || mediaType.endsWith("+json")
}

/** A Content-Encoding other than identity means the transferred bytes differ from the decoded body. */
internal fun hasContentEncoding(headers: Map<String, List<String>>): Boolean =
    headers.entries.any { (name, values) ->
        name.equals("Content-Encoding", ignoreCase = true) &&
            values.any { it.isNotBlank() && !it.trim().equals("identity", ignoreCase = true) }
    }

internal enum class ResponseSizeKind { EXACT, DECLARED, AT_LEAST }

/**
 * How the response size should be described. A complete read is exact. A truncated read can use the
 * server's Content-Length only when the body is not compressed, because that header counts wire bytes.
 */
internal fun responseSizeKind(
    truncated: Boolean,
    declaredBytes: Long?,
    contentEncoded: Boolean,
): ResponseSizeKind =
    when {
        !truncated -> ResponseSizeKind.EXACT
        declaredBytes != null && !contentEncoded -> ResponseSizeKind.DECLARED
        else -> ResponseSizeKind.AT_LEAST
    }
