package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepository
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import net.aieat.netswissknife.core.network.httprobe.CrossOriginEntityReplay
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class HttpProbeParams(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
    val followRedirects: Boolean = true,
    val timeoutMs: Int = 15_000,
    val approveCrossOriginEntityReplay: (suspend (CrossOriginEntityReplay) -> Boolean)? = null
)

class HttpProbeUseCase(private val repository: HttpProbeRepository) {

    suspend operator fun invoke(params: HttpProbeParams): NetworkResult<HttpProbeResult> =
        invokeValidated(params, null)

    /** Caller-owned operation variant; the original entry point remains source-compatible. */
    suspend operator fun invoke(
        params: HttpProbeParams,
        operationSession: OperationSession,
    ): NetworkResult<HttpProbeResult> = invokeValidated(params, operationSession)

    private suspend fun invokeValidated(
        params: HttpProbeParams,
        operationSession: OperationSession?,
    ): NetworkResult<HttpProbeResult> {
        val url = params.url.trim()

        validateHttpProbeUrlInfo(url)?.let { return NetworkResult.error(it.code, it.developerCopy(), args = it.args) }

        if (params.timeoutMs !in 500..60_000)
            return NetworkResult.error(ErrorCode.TIMEOUT_OUT_OF_RANGE, "Timeout must be between 500 ms and 60 000 ms", args = listOf(500, 60_000))

        val effectiveBody = if (params.method.supportsBody) params.body else null

        val request = HttpProbeRequest(
            url = url,
            method = params.method,
            headers = params.headers,
            body = effectiveBody,
            followRedirects = params.followRedirects,
            timeoutMs = params.timeoutMs,
            approveCrossOriginEntityReplay = params.approveCrossOriginEntityReplay
        )
        return if (operationSession == null) repository.probe(request)
        else repository.probe(request, operationSession)
    }
}

/**
 * Performs cheap syntax-only URL validation for the form and use case.
 * It deliberately does not resolve DNS or open a socket.
 */
fun validateHttpProbeUrl(rawUrl: String): String? {
    return validateHttpProbeUrlInfo(rawUrl)?.developerCopy()
}

/** Typed URL validation used by the use case and localization layer. */
fun validateHttpProbeUrlInfo(rawUrl: String): net.aieat.netswissknife.core.network.ErrorInfo? {
    val url = rawUrl.trim()
    if (url.isBlank()) return validationError(ErrorCode.URL_BLANK, "URL must not be blank")

    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return validationError(ErrorCode.URL_MALFORMED, "Malformed URL: ${e.message}")
    } catch (e: IllegalArgumentException) {
        return validationError(ErrorCode.URL_MALFORMED, "Malformed URL: ${e.message}")
    }

    val parsedUrl = try {
        uri.toURL()
    } catch (e: Exception) {
        return validationError(ErrorCode.URL_MALFORMED, "Malformed URL: ${e.message ?: "invalid syntax"}")
    }

    if (parsedUrl.protocol.lowercase(Locale.ROOT) !in setOf("http", "https")) {
        return validationError(ErrorCode.URL_SCHEME_UNSUPPORTED, "Only HTTP and HTTPS URLs are supported")
    }

    val host = uri.host ?: parsedUrl.host.takeIf { it.isNotBlank() }
        ?: return validationError(ErrorCode.URL_INVALID, "URL must include a valid host")
    if (host.endsWith('.')) return validationError(ErrorCode.URL_INVALID, "URL host is incomplete")
    if (!HostValidator.isValidHostname(host)) return validationError(ErrorCode.URL_INVALID, "URL must include a valid host")
    if (uri.port != -1 && uri.port !in 1..65_535) return validationError(ErrorCode.PORT_OUT_OF_RANGE, "URL port must be between 1 and 65535", uri.port, 1, 65_535)

    return null
}
