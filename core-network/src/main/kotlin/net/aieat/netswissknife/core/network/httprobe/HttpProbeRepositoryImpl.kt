package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class HttpProbeRepositoryImpl : HttpProbeRepository {

    companion object {
        private const val MAX_REDIRECTS = 10
        private const val MAX_RESPONSE_BODY_BYTES = 10_485_760L
        private val SENSITIVE_REDIRECT_HEADERS = setOf(
            "authorization",
            "cookie",
            "proxy-authorization"
        )
        private val BODY_HEADERS = setOf(
            "content-length",
            "content-type",
            "transfer-encoding"
        )
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val CHARSET_PATTERN = Regex("(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))")
    }

    override suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult> {
        val trimmedUrl = request.url.trim()
        if (trimmedUrl.isBlank()) return NetworkResult.Error("URL must not be blank")
        if (request.timeoutMs !in 500..60_000)
            return NetworkResult.Error("Timeout must be between 500 ms and 60 000 ms")
        if (request.maxResponseBodyBytes !in 0..MAX_RESPONSE_BODY_BYTES)
            return NetworkResult.Error(
                "Maximum response body size must be between 0 and $MAX_RESPONSE_BODY_BYTES bytes"
            )

        val parsedUrl = try {
            URI(trimmedUrl).toURL().also { url ->
                if (url.protocol !in listOf("http", "https"))
                    return NetworkResult.Error("Only HTTP and HTTPS URLs are supported (got: ${url.protocol})")
            }
        } catch (e: MalformedURLException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        } catch (e: URISyntaxException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return NetworkResult.Error("Malformed URL: ${e.message}")
        }

        return withContext(Dispatchers.IO) {
            try {
                executeRequest(parsedUrl, request)
            } catch (e: CancellationException) {
                throw e
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
        var currentMethod = request.method
        var currentBody = request.body.takeIf { request.method.supportsBody }
        val startTimeNs = System.nanoTime()

        repeat(MAX_REDIRECTS + 1) { attempt ->
            val conn = currentUrl.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.requestMethod = currentMethod.name
                conn.connectTimeout = request.timeoutMs
                conn.readTimeout = request.timeoutMs

                // Credentials must not cross an origin boundary during a redirect.
                // Entity headers are also invalid once redirect semantics change the
                // request into a body-less method.
                val sameOrigin = sameOrigin(startUrl, currentUrl)
                request.headers.forEach { (key, value) ->
                    val normalizedKey = key.lowercase()
                    if ((!sameOrigin && normalizedKey in SENSITIVE_REDIRECT_HEADERS) ||
                        (!currentMethod.supportsBody && normalizedKey in BODY_HEADERS)
                    ) return@forEach
                    conn.setRequestProperty(key, value)
                }

                // Write body if applicable
                if (currentMethod.supportsBody && currentBody != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(currentBody!!.toByteArray(Charsets.UTF_8)) }
                }

                conn.connect()

                val statusCode = conn.responseCode
                val statusMessage = conn.responseMessage ?: ""

                // Handle redirects manually
                if (request.followRedirects && statusCode in REDIRECT_STATUS_CODES) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        if (attempt >= MAX_REDIRECTS) {
                            return NetworkResult.Error("Too many redirects (max $MAX_REDIRECTS)")
                        }
                        val nextUrl = try {
                            resolveUrl(currentUrl, location)
                        } catch (e: Exception) {
                            return NetworkResult.Error("Malformed redirect URL: ${e.message}", e)
                        }
                        if (nextUrl.protocol !in listOf("http", "https")) {
                            return NetworkResult.Error(
                                "Redirected to unsupported protocol: ${nextUrl.protocol}"
                            )
                        }
                        if (currentUrl.protocol.equals("https", ignoreCase = true) &&
                            nextUrl.protocol.equals("http", ignoreCase = true)
                        ) {
                            return NetworkResult.Error(
                                "Refusing insecure HTTPS-to-HTTP redirect to $nextUrl"
                            )
                        }
                        redirectChain.add(currentUrl.toString())
                        val redirectedRequest = redirectRequest(currentMethod, currentBody, statusCode)
                        currentMethod = redirectedRequest.first
                        currentBody = redirectedRequest.second
                        currentUrl = nextUrl
                        return@repeat // continue loop
                    }
                }

                // Final response — collect headers and body
                val responseHeaders = buildMap<String, List<String>> {
                    conn.headerFields.forEach { (key, values) ->
                        if (key != null) put(key, values)
                    }
                }

                val isHttps = currentUrl.protocol.equals("https", ignoreCase = true)
                val bodyStream = if (statusCode >= 400) conn.errorStream else conn.inputStream
                val (responseBody, responseBodyBytes) = bodyStream?.use { stream ->
                    readResponseBody(
                        stream = stream,
                        maxBytes = request.maxResponseBodyBytes,
                        charset = responseCharset(conn)
                    )
                } ?: (null to 0L)

                val elapsed = (System.nanoTime() - startTimeNs) / 1_000_000L
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

        return NetworkResult.Error("Too many redirects (max $MAX_REDIRECTS)")
    }

    private fun readResponseBody(
        stream: java.io.InputStream,
        maxBytes: Long,
        charset: Charset
    ): Pair<String, Long> {
        if (maxBytes == 0L) {
            // Read one byte only so callers can distinguish an empty body from a
            // body omitted because the configured safety bound was reached.
            val hasMore = stream.read() != -1
            return "" to if (hasMore) 1L else 0L
        }

        val output = ByteArrayOutputStream(maxBytes.toInt().coerceAtMost(8192))
        val buffer = ByteArray(8192)
        var bytesRead = 0L
        while (bytesRead < maxBytes) {
            val requested = minOf(buffer.size.toLong(), maxBytes - bytesRead).toInt()
            val read = stream.read(buffer, 0, requested)
            if (read == -1) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            bytesRead += read
        }

        // Probe one additional byte, then stop. This keeps memory and network
        // consumption bounded while preserving the existing "bytes > limit"
        // signal used by the UI to display truncation.
        if (bytesRead == maxBytes && stream.read() != -1) bytesRead++

        val decoded = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(java.nio.ByteBuffer.wrap(output.toByteArray()))
            .toString()
        return decoded to bytesRead
    }

    private fun responseCharset(connection: HttpURLConnection): Charset {
        val contentType = connection.headerFields.entries
            .firstOrNull { (key, _) -> key?.equals("Content-Type", ignoreCase = true) == true }
            ?.value
            ?.firstOrNull()
            ?: return Charsets.UTF_8
        val charsetMatch = CHARSET_PATTERN.find(contentType)
        val charsetName = charsetMatch?.groupValues?.getOrNull(1)?.ifBlank { null }
            ?: charsetMatch?.groupValues?.getOrNull(2)?.ifBlank { null }
            ?: return Charsets.UTF_8
        return try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    private fun sameOrigin(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(url: URL): Int = when {
        url.port != -1 -> url.port
        url.protocol.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun redirectRequest(
        method: HttpMethod,
        body: String?,
        statusCode: Int
    ): Pair<HttpMethod, String?> = when (statusCode) {
        303 -> if (method == HttpMethod.HEAD) HttpMethod.HEAD to null else HttpMethod.GET to null
        301, 302 -> if (method == HttpMethod.POST) HttpMethod.GET to null else method to body
        else -> method to body // 307 and 308 preserve method and entity
    }

    private fun resolveUrl(base: URL, location: String): URL =
        URI(base.toString()).resolve(location).toURL()
}
