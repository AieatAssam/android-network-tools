package net.aieat.netswissknife.core.network.httprobe

import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import net.aieat.netswissknife.core.network.httprobe.engine.RedirectHop

enum class HttpMethod(
    val supportsBody: Boolean,
) {
    GET(false),
    POST(true),
    PUT(true),
    PATCH(true),
    DELETE(false),
    HEAD(false),
    OPTIONS(false),
}

enum class SecurityRating { PASS, WARN, FAIL, INFO }

data class HttpProbeRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
    val followRedirects: Boolean = true,
    val timeoutMs: Int = 15_000,
    val maxResponseBodyBytes: Long = 512_000L,
    /** Per-run approval callback. It is invoked before an entity is replayed to another origin. */
    val approveCrossOriginEntityReplay: (suspend (CrossOriginEntityReplay) -> Boolean)? = null,
)

data class CrossOriginEntityReplay(
    /** Exact resolved URL displayed to the user for this single redirect hop. */
    val destinationUrl: String,
    val method: HttpMethod,
    val statusCode: Int,
)

data class SecurityHeaderCheck(
    val headerName: String,
    val value: String?,
    val rating: SecurityRating,
    val description: String,
    /** Optional app resource key for a localized, stable explanation. */
    val descriptionKey: String? = null,
)

data class HttpProbeResult(
    val request: HttpProbeRequest,
    val statusCode: Int,
    val statusMessage: String,
    val responseTimeMs: Long,
    val responseHeaders: Map<String, List<String>>,
    val responseBody: String?,
    /** Bytes actually buffered from the response. Never exceeds the request's cap. */
    val responseBodyBytes: Long,
    /**
     * Full body size as advertised by `Content-Length`, or null when the server
     * did not declare one (chunked transfer, HTTP/2 without the header, …).
     * This is the only trustworthy total once [responseBodyTruncated] is set,
     * because a bounded read never observes the bytes past the cap.
     */
    val declaredBodyBytes: Long? = null,
    val responseBodyTruncated: Boolean = false,
    val finalUrl: String,
    val redirectChain: List<String>,
    val securityChecks: List<SecurityHeaderCheck>,
    val timings: HttpTimings = HttpTimings(totalMs = responseTimeMs),
    val protocol: String = "unknown",
    val redirectHops: List<RedirectHop> = emptyList(),
)

/** Structured evidence for a redirect refused before the destination is opened. */
class HttpProbeBlockedRedirectException(
    val sourceUrl: String,
    val destinationUrl: String,
    val statusCode: Int,
    val location: String,
) : Exception("Refusing insecure HTTPS-to-HTTP redirect") {
    companion object {
        const val CODE = "INSECURE_REDIRECT"
    }
}
