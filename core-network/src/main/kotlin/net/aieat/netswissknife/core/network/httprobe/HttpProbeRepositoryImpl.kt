package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class HttpProbeRepositoryImpl : HttpProbeRepository {

    override suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult> {
        val trimmedUrl = request.url.trim()
        if (trimmedUrl.isBlank()) return NetworkResult.Error("URL must not be blank")
        if (request.timeoutMs !in 500..60_000)
            return NetworkResult.Error("Timeout must be between 500 ms and 60 000 ms")

        val parsedUrl = try {
            URL(trimmedUrl).also { url ->
                if (url.protocol !in listOf("http", "https"))
                    return NetworkResult.Error("Only HTTP and HTTPS URLs are supported (got: ${url.protocol})")
            }
        } catch (e: MalformedURLException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        }

        return withContext(Dispatchers.IO) {
            try {
                executeRequest(parsedUrl, request)
            } catch (e: IOException) {
                NetworkResult.Error("Network error: ${e.message}", e)
            } catch (e: Exception) {
                NetworkResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }

    private fun executeRequest(
        startUrl: URL,
        request: HttpProbeRequest
    ): NetworkResult<HttpProbeResult> {
        val redirectChain = mutableListOf<String>()
        var currentUrl = startUrl
        val startTime = System.currentTimeMillis()
        val maxRedirects = 10

        repeat(maxRedirects + 1) { attempt ->
            val conn = currentUrl.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.requestMethod = request.method.name
                conn.connectTimeout = request.timeoutMs
                conn.readTimeout = request.timeoutMs

                // Apply custom request headers
                request.headers.forEach { (key, value) ->
                    conn.setRequestProperty(key, value)
                }

                // Write body if applicable
                if (request.method.supportsBody && request.body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
                }

                conn.connect()

                val statusCode = conn.responseCode
                val statusMessage = conn.responseMessage ?: ""

                // Handle redirects manually
                if (request.followRedirects && statusCode in 300..399 && attempt < maxRedirects) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        redirectChain.add(currentUrl.toString())
                        currentUrl = resolveUrl(currentUrl, location)
                        return@repeat // continue loop
                    }
                }

                // Final response — collect headers and body
                val responseHeaders = buildMap<String, List<String>> {
                    conn.headerFields.forEach { (key, values) ->
                        if (key != null) put(key, values)
                    }
                }

                val isHttps = currentUrl.protocol == "https"
                val bodyStream = if (statusCode >= 400) conn.errorStream else conn.inputStream
                var responseBodyBytes = 0L
                val responseBody: String? = bodyStream?.use { stream ->
                    val buffer = ByteArray(8192)
                    val sb = StringBuilder()
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        val bytesBufferedSoFar = responseBodyBytes
                        responseBodyBytes += read
                        if (bytesBufferedSoFar < request.maxResponseBodyBytes) {
                            val canAppend = (request.maxResponseBodyBytes - bytesBufferedSoFar)
                                .toInt().coerceAtMost(read)
                            sb.append(String(buffer, 0, canAppend, Charsets.UTF_8))
                        }
                    }
                    sb.toString()
                }

                val elapsed = System.currentTimeMillis() - startTime
                val securityChecks = HttpSecurityAnalyzer.analyze(responseHeaders, isHttps)

                return NetworkResult.Success(
                    HttpProbeResult(
                        request = request,
                        statusCode = statusCode,
                        statusMessage = statusMessage,
                        responseTimeMs = elapsed,
                        responseHeaders = responseHeaders,
                        responseBody = responseBody,
                        responseBodyBytes = responseBodyBytes,
                        finalUrl = currentUrl.toString(),
                        redirectChain = redirectChain.toList(),
                        securityChecks = securityChecks
                    )
                )
            } finally {
                conn.disconnect()
            }
        }

        return NetworkResult.Error("Too many redirects (max $maxRedirects)")
    }

    private fun resolveUrl(base: URL, location: String): URL {
        return if (location.startsWith("http://") || location.startsWith("https://")) {
            URL(location)
        } else {
            URL(base, location)
        }
    }
}
