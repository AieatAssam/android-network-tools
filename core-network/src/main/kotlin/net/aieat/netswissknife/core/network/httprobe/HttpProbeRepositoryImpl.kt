package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationResourcesContext
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

fun interface HttpProbeConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class HttpProbeRepositoryImpl internal constructor(
    private val connectionFactory: HttpProbeConnectionFactory
) : HttpProbeRepository {

    internal var clock: MonotonicClock = SystemMonotonicClock

    constructor() : this(HttpProbeConnectionFactory { it.openConnection() as HttpURLConnection })

    companion object {
        private const val MAX_REDIRECTS = 10
        private const val MAX_RESPONSE_BODY_BYTES = 10_485_760L
        private val BODY_HEADERS = setOf(
            "content-length",
            "content-type",
            "transfer-encoding"
        )
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val CHARSET_PATTERN = Regex("(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))")
    }

    override suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult> =
        probeInternal(request, null)

    override suspend fun probe(
        request: HttpProbeRequest,
        operationSession: OperationSession,
    ): NetworkResult<HttpProbeResult> = probeInternal(request, operationSession)

    private suspend fun probeInternal(
        request: HttpProbeRequest,
        callerSession: OperationSession?,
    ): NetworkResult<HttpProbeResult> {
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
            val session = callerSession ?: HttpProbeOperation.newSession(request, clock)
            try {
                OperationRunner.run(session) {
                    executeRequest(parsedUrl, request)
                }
            } catch (e: CancellationException) {
                if (e is OperationCancellationException &&
                    e.reason == CancellationReason.DEADLINE_EXCEEDED
                ) return@withContext NetworkResult.Error("HTTP request timed out", e)
                throw e
            } catch (e: OperationDeadlineExceededException) {
                return@withContext NetworkResult.Error("HTTP request timed out", e)
            } catch (e: IOException) {
                session.cancellationReason?.let { reason ->
                    if (reason == CancellationReason.DEADLINE_EXCEEDED) {
                        return@withContext NetworkResult.Error("HTTP request timed out", e)
                    }
                    throw OperationCancellationException(reason, e)
                }
                NetworkResult.Error("Network error: ${e.message}", e)
            } catch (e: Exception) {
                session.cancellationReason?.let { reason ->
                    if (reason == CancellationReason.DEADLINE_EXCEEDED) {
                        return@withContext NetworkResult.Error("HTTP request timed out", e)
                    }
                    throw OperationCancellationException(reason, e)
                }
                NetworkResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }

    private suspend fun executeRequest(
        startUrl: URL,
        request: HttpProbeRequest
    ): NetworkResult<HttpProbeResult> {
        val redirectChain = mutableListOf<String>()
        var currentUrl = startUrl
        var currentMethod = request.method
        var currentBody = request.body.takeIf { request.method.supportsBody }
        var forwardCustomHeaders = true
        val startTimeNs = clock.nowNanos()

        repeat(MAX_REDIRECTS + 1) { attempt ->
            ensureCurrentOperationActive()
            val conn = connectionFactory.open(currentUrl)
            val lease = HttpConnectionLease(conn)
            val resources = currentCoroutineContext()[OperationResourcesContext]?.resources
            resources?.register(lease)
            try {
                ensureCurrentOperationActive()
                val remainingTimeoutMs = currentCoroutineContext()[OperationResourcesContext]
                    ?.session?.budget?.remainingTimeoutMillis()
                    ?.coerceAtMost(request.timeoutMs.toLong())
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
                    ?.coerceAtLeast(1)
                    ?: request.timeoutMs
                conn.instanceFollowRedirects = false
                conn.requestMethod = currentMethod.name
                conn.connectTimeout = remainingTimeoutMs
                conn.readTimeout = remainingTimeoutMs

                // Credentials must not cross an origin boundary during a redirect.
                // Entity headers are also invalid once redirect semantics change the
                // request into a body-less method.
                request.headers.forEach { (key, value) ->
                    val normalizedKey = key.lowercase()
                    if (!forwardCustomHeaders ||
                        (!currentMethod.supportsBody && normalizedKey in BODY_HEADERS)
                    ) return@forEach
                    conn.setRequestProperty(key, value)
                }

                // Write body if applicable
                if (currentMethod.supportsBody && currentBody != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(currentBody!!.toByteArray(Charsets.UTF_8)) }
                }

                ensureCurrentOperationActive()
                conn.connect()
                ensureCurrentOperationActive()

                val statusCode = conn.responseCode
                ensureCurrentOperationActive()
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
                        // Custom headers are user-controlled and may contain credentials
                        // under arbitrary names. Never forward any of them across origins.
                        val redirectedRequest = redirectRequest(currentMethod, currentBody, statusCode)
                        val changesOrigin = !sameOrigin(currentUrl, nextUrl)
                        if (changesOrigin && redirectedRequest.first.supportsBody && redirectedRequest.second != null) {
                            // Do not keep the source response connection open while waiting
                            // for user consent. The finally block disconnects idempotently.
                            lease.close()
                            val approval = request.approveCrossOriginEntityReplay
                                ?: return NetworkResult.Error(
                                    "Cross-origin HTTP $statusCode redirect to $nextUrl requires approval before replaying ${redirectedRequest.first} body"
                                )
                            val approved = approval(
                                CrossOriginEntityReplay(
                                    destinationUrl = nextUrl.toString(),
                                    method = redirectedRequest.first,
                                    statusCode = statusCode
                                )
                            )
                            if (!approved) {
                                return NetworkResult.Error(
                                    "HTTP $statusCode redirect body replay to $nextUrl was not approved"
                                )
                            }
                        }
                        currentMethod = redirectedRequest.first
                        currentBody = redirectedRequest.second
                        if (changesOrigin) forwardCustomHeaders = false
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
                val bodyRead = bodyStream?.use { stream ->
                    readResponseBody(
                        stream = stream,
                        maxBytes = request.maxResponseBodyBytes,
                        charset = responseCharset(conn)
                    )
                } ?: BodyRead(null, 0L, false)
                ensureCurrentOperationActive()
                val declaredBodyBytes = declaredContentLength(responseHeaders)

                val elapsed = clock.elapsedMillisSince(startTimeNs)
                val securityChecks = HttpSecurityAnalyzer.analyze(responseHeaders, isHttps)

                return NetworkResult.Success(
                    HttpProbeResult(
                        request = request,
                        statusCode = statusCode,
                        statusMessage = statusMessage,
                        responseTimeMs = elapsed,
                        responseHeaders = responseHeaders,
                        responseBody = bodyRead.text,
                        responseBodyBytes = bodyRead.bufferedBytes,
                        declaredBodyBytes = declaredBodyBytes,
                        responseBodyTruncated = bodyRead.truncated,
                        finalUrl = currentUrl.toString(),
                        redirectChain = redirectChain.toList(),
                        securityChecks = securityChecks
                    )
                )
            } catch (failure: Exception) {
                // A disconnect caused by cancellation/deadline commonly surfaces as IOException.
                // Re-check here so it cannot be converted into an ordinary network error.
                ensureCurrentOperationActive()
                throw failure
            } finally {
                if (resources == null || resources.release(lease)) lease.close()
            }
        }

        return NetworkResult.Error("Too many redirects (max $MAX_REDIRECTS)")
    }

    private fun readResponseBody(
        stream: java.io.InputStream,
        maxBytes: Long,
        charset: Charset
    ): BodyRead {
        if (maxBytes == 0L) {
            // Read one byte only so callers can distinguish an empty body from a
            // body omitted because the configured safety bound was reached. The
            // probe byte is deliberately not counted as buffered content.
            val hasMore = stream.read() != -1
            return BodyRead("", 0L, hasMore)
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
        // consumption bounded while still proving whether the body continues
        // past the cap. The probe byte is not counted as buffered content: a
        // bounded read cannot know the real total, so callers must fall back to
        // Content-Length for that.
        val truncated = bytesRead == maxBytes && stream.read() != -1

        val decoded = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(java.nio.ByteBuffer.wrap(output.toByteArray()))
            .toString()
        return BodyRead(decoded, bytesRead, truncated)
    }

    private data class BodyRead(
        val text: String?,
        /** Bytes actually buffered. Never exceeds the caller's cap. */
        val bufferedBytes: Long,
        val truncated: Boolean
    )

    private class HttpConnectionLease(
        private val connection: HttpURLConnection,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) connection.disconnect()
        }
    }

    /**
     * Parses `Content-Length` into the full body size. Absent, malformed, or
     * multi-valued headers yield null rather than a guess, so the UI can say
     * "at least N" instead of reporting the buffered prefix as the total.
     */
    private fun declaredContentLength(headers: Map<String, List<String>>): Long? = headers.entries
        .firstOrNull { (key, _) -> key.equals("Content-Length", ignoreCase = true) }
        ?.value
        ?.singleOrNull()
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }

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
