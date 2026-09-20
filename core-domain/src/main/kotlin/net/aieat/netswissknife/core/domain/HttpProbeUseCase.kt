package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepository
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class HttpProbeParams(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
    val followRedirects: Boolean = true,
    val timeoutMs: Int = 15_000
)

class HttpProbeUseCase(private val repository: HttpProbeRepository) {

    suspend operator fun invoke(params: HttpProbeParams): NetworkResult<HttpProbeResult> {
        val url = params.url.trim()

        validateHttpProbeUrl(url)?.let { return NetworkResult.Error(it) }

        if (params.timeoutMs !in 500..60_000)
            return NetworkResult.Error("Timeout must be between 500 ms and 60 000 ms")

        val effectiveBody = if (params.method.supportsBody) params.body else null

        return repository.probe(
            HttpProbeRequest(
                url = url,
                method = params.method,
                headers = params.headers,
                body = effectiveBody,
                followRedirects = params.followRedirects,
                timeoutMs = params.timeoutMs
            )
        )
    }
}

/**
 * Performs cheap syntax-only URL validation for the form and use case.
 * It deliberately does not resolve DNS or open a socket.
 */
fun validateHttpProbeUrl(rawUrl: String): String? {
    val url = rawUrl.trim()
    if (url.isBlank()) return "URL must not be blank"

    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return "Malformed URL: ${e.message}"
    } catch (e: IllegalArgumentException) {
        return "Malformed URL: ${e.message}"
    }

    val parsedUrl = try {
        uri.toURL()
    } catch (e: Exception) {
        return "Malformed URL: ${e.message ?: "invalid syntax"}"
    }

    if (parsedUrl.protocol.lowercase(Locale.ROOT) !in setOf("http", "https")) {
        return "Only HTTP and HTTPS URLs are supported"
    }

    val host = uri.host ?: parsedUrl.host.takeIf { it.isNotBlank() }
        ?: return "URL must include a valid host"
    if (host.endsWith('.')) return "URL host is incomplete"
    if (!HostValidator.isValidHostname(host)) return "URL must include a valid host"
    if (uri.port !in -1..65_535) return "URL port must be between 1 and 65535"

    return null
}
