package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.httprobe.HttpMethod
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepository
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRequest
import net.aieat.netswissknife.core.network.httprobe.HttpProbeResult
import java.net.MalformedURLException
import java.net.URI

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

        if (url.isBlank()) return NetworkResult.Error("URL must not be blank")

        try {
            val parsed = URI(url).toURL()
            if (parsed.protocol !in listOf("http", "https"))
                return NetworkResult.Error("Only HTTP and HTTPS URLs are supported")
        } catch (e: MalformedURLException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        }

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
