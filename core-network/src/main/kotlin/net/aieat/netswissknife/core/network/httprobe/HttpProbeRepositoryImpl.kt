package net.aieat.netswissknife.core.network.httprobe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.elapsedMillisSince
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngine
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineCall
import net.aieat.netswissknife.core.network.httprobe.engine.HttpEngineRequest
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import net.aieat.netswissknife.core.network.httprobe.engine.OkHttpEngine
import net.aieat.netswissknife.core.network.httprobe.engine.RedirectHop
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationResourcesContext
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

class HttpProbeRepositoryImpl internal constructor(
    private val engine: HttpEngine,
) : HttpProbeRepository {
    internal var clock: MonotonicClock = SystemMonotonicClock

    constructor() : this(OkHttpEngine())

    companion object {
        private const val MAX_RESPONSE_BODY_BYTES = 10_485_760L
        private val BODY_HEADERS = setOf("content-length", "content-type", "transfer-encoding")
        private val CHARSET_PATTERN = Regex("(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))")
    }

    override suspend fun probe(request: HttpProbeRequest): NetworkResult<HttpProbeResult> = probeInternal(request, null)

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
        if (request.timeoutMs !in 500..60_000) {
            return NetworkResult.Error("Timeout must be between 500 ms and 60 000 ms")
        }
        if (request.maxResponseBodyBytes !in 0..MAX_RESPONSE_BODY_BYTES) {
            return NetworkResult.Error("Maximum response body size must be between 0 and $MAX_RESPONSE_BODY_BYTES bytes")
        }

        val parsedUrl =
            try {
                URI(trimmedUrl).toURL().also { url ->
                    if (url.protocol !in listOf("http", "https")) {
                        return NetworkResult.Error("Only HTTP and HTTPS URLs are supported")
                    }
                }
            } catch (_: MalformedURLException) {
                return NetworkResult.Error("Malformed URL")
            } catch (_: URISyntaxException) {
                return NetworkResult.Error("Malformed URL")
            } catch (_: IllegalArgumentException) {
                return NetworkResult.Error("Malformed URL")
            }

        return withContext(Dispatchers.IO) {
            val session = callerSession ?: HttpProbeOperation.newSession(request, clock)
            try {
                OperationRunner.run(session) { executeRequest(parsedUrl, request) }
            } catch (e: CancellationException) {
                if (e is OperationCancellationException && e.reason == CancellationReason.DEADLINE_EXCEEDED) {
                    return@withContext NetworkResult.Error("HTTP request timed out", e)
                }
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
                NetworkResult.Error("Network request failed", e)
            } catch (e: Exception) {
                session.cancellationReason?.let { reason ->
                    if (reason == CancellationReason.DEADLINE_EXCEEDED) {
                        return@withContext NetworkResult.Error("HTTP request timed out", e)
                    }
                    throw OperationCancellationException(reason, e)
                }
                NetworkResult.Error("HTTP request failed", e)
            }
        }
    }

    private suspend fun executeRequest(
        startUrl: URL,
        request: HttpProbeRequest,
    ): NetworkResult<HttpProbeResult> {
        val redirectHops = mutableListOf<RedirectHop>()
        var currentUrl = startUrl
        var currentMethod = request.method
        var currentBody = request.body.takeIf { request.method.supportsBody }
        var forwardCustomHeaders = true
        val startTimeNs = clock.nowNanos()
        var timingTotals = HttpTimings()

        repeat(RedirectPolicy.MAX_REDIRECTS + 1) { attempt ->
            ensureCurrentOperationActive()
            val remainingTimeoutMs =
                currentCoroutineContext()[OperationResourcesContext]
                    ?.session
                    ?.budget
                    ?.remainingTimeoutMillis()
                    ?.coerceAtMost(request.timeoutMs.toLong())
                    ?.coerceAtLeast(1L)
                    ?.toInt() ?: request.timeoutMs
            val headers =
                request.headers.filter { (key, _) ->
                    forwardCustomHeaders && (currentMethod.supportsBody || key.lowercase() !in BODY_HEADERS)
                }
            val exchange =
                engine.newCall(
                    HttpEngineRequest(
                        url = currentUrl.toString(),
                        method = currentMethod.name,
                        headers = headers,
                        body = currentBody?.toByteArray(Charsets.UTF_8),
                        timeoutMs = remainingTimeoutMs,
                    ),
                )
            val lease = HttpCallLease(exchange)
            val resources = currentCoroutineContext()[OperationResourcesContext]?.resources
            resources?.register(lease)
            try {
                ensureCurrentOperationActive()
                val response = exchange.execute()
                timingTotals = timingTotals.plus(response.timings)
                ensureCurrentOperationActive()

                val location = header(response.headers, "Location")
                val redirect =
                    if (request.followRedirects) {
                        RedirectPolicy.evaluate(
                            source = currentUrl,
                            method = currentMethod,
                            body = currentBody,
                            statusCode = response.statusCode,
                            location = location,
                            redirectsAlreadyFollowed = redirectHops.size,
                            maxRedirects = RedirectPolicy.MAX_REDIRECTS,
                        )
                    } else {
                        RedirectPolicy.Decision.NotRedirect
                    }
                when (redirect) {
                    RedirectPolicy.Decision.NotRedirect -> {
                        Unit
                    }

                    RedirectPolicy.Decision.MalformedLocation -> {
                        return NetworkResult.Error("Malformed redirect URL")
                    }

                    RedirectPolicy.Decision.UnsupportedProtocol -> {
                        return NetworkResult.Error("Redirected to unsupported protocol")
                    }

                    RedirectPolicy.Decision.TooManyRedirects -> {
                        return NetworkResult.Error("Too many redirects (max ${RedirectPolicy.MAX_REDIRECTS})")
                    }

                    is RedirectPolicy.Decision.BlockedDowngrade -> {
                        redirectHops += RedirectHop(currentUrl.toString(), response.statusCode, location.orEmpty())
                        val blockedRedirect =
                            HttpProbeBlockedRedirectException(
                                sourceUrl = currentUrl.toString(),
                                destinationUrl = redirect.destination.toString(),
                                statusCode = response.statusCode,
                                location = location.orEmpty(),
                            )
                        return NetworkResult.Error(
                            "Refusing insecure HTTPS-to-HTTP redirect",
                            blockedRedirect,
                            code = HttpProbeBlockedRedirectException.CODE,
                        )
                    }

                    is RedirectPolicy.Decision.Follow -> {
                        redirectHops += RedirectHop(currentUrl.toString(), response.statusCode, location.orEmpty())
                        if (redirect.changesOrigin && redirect.method.supportsBody && redirect.body != null) {
                            lease.close()
                            val approval =
                                request.approveCrossOriginEntityReplay
                                    ?: return NetworkResult.Error("Cross-origin redirect requires approval before entity replay")
                            val approved =
                                approval(
                                    CrossOriginEntityReplay(redirect.destination.toString(), redirect.method, response.statusCode),
                                )
                            if (!approved) return NetworkResult.Error("Cross-origin redirect entity replay was not approved")
                        }
                        currentMethod = redirect.method
                        currentBody = redirect.body
                        if (redirect.changesOrigin) forwardCustomHeaders = false
                        currentUrl = redirect.destination
                        return@repeat
                    }
                }

                val bodyRead =
                    response.body?.use { stream ->
                        readResponseBody(stream, request.maxResponseBodyBytes, responseCharset(response.headers))
                    } ?: BodyRead(null, 0L, false)
                ensureCurrentOperationActive()
                val finalCallTimings = response.timingSnapshot()
                val transferTotal =
                    when {
                        timingTotals.transferMs == null -> finalCallTimings.transferMs
                        finalCallTimings.transferMs == null -> timingTotals.transferMs
                        else -> timingTotals.transferMs + finalCallTimings.transferMs
                    }
                val elapsed = clock.elapsedMillisSince(startTimeNs)
                val declaredBodyBytes = declaredContentLength(response.headers)
                val isHttps = currentUrl.protocol.equals("https", ignoreCase = true)
                return NetworkResult.Success(
                    HttpProbeResult(
                        request = request,
                        statusCode = response.statusCode,
                        statusMessage = response.statusMessage,
                        responseTimeMs = elapsed,
                        responseHeaders = response.headers,
                        responseBody = bodyRead.text,
                        responseBodyBytes = bodyRead.bufferedBytes,
                        declaredBodyBytes = declaredBodyBytes,
                        responseBodyTruncated = bodyRead.truncated,
                        finalUrl = currentUrl.toString(),
                        redirectChain = redirectHops.map { it.url },
                        securityChecks = HttpSecurityAnalyzer.analyze(response.headers, isHttps),
                        timings =
                            timingTotals.copy(
                                transferMs = transferTotal,
                                totalMs = elapsed,
                            ),
                        protocol = response.protocol,
                        redirectHops = redirectHops.toList(),
                    ),
                )
            } catch (failure: Exception) {
                ensureCurrentOperationActive()
                throw failure
            } finally {
                if (resources == null || resources.release(lease)) lease.close()
            }
        }
        return NetworkResult.Error("Too many redirects (max ${RedirectPolicy.MAX_REDIRECTS})")
    }

    private fun readResponseBody(
        stream: java.io.InputStream,
        maxBytes: Long,
        charset: Charset,
    ): BodyRead {
        if (maxBytes == 0L) return BodyRead("", 0L, stream.read() != -1)
        val output = ByteArrayOutputStream(maxBytes.toInt().coerceAtMost(8192))
        val buffer = ByteArray(8192)
        var bytesRead = 0L
        while (bytesRead < maxBytes) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - bytesRead).toInt())
            if (read == -1) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            bytesRead += read
        }
        val truncated = bytesRead == maxBytes && stream.read() != -1
        val decoded =
            charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(java.nio.ByteBuffer.wrap(output.toByteArray()))
                .toString()
        return BodyRead(decoded, bytesRead, truncated)
    }

    private data class BodyRead(
        val text: String?,
        val bufferedBytes: Long,
        val truncated: Boolean,
    )

    private class HttpCallLease(
        private val call: HttpEngineCall,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) call.close()
        }
    }

    private fun declaredContentLength(headers: Map<String, List<String>>): Long? =
        headerValues(headers, "Content-Length")
            ?.singleOrNull()
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }

    private fun responseCharset(headers: Map<String, List<String>>): Charset {
        val contentType = header(headers, "Content-Type") ?: return Charsets.UTF_8
        val match = CHARSET_PATTERN.find(contentType)
        val name =
            match?.groupValues?.getOrNull(1)?.ifBlank { null }
                ?: match?.groupValues?.getOrNull(2)?.ifBlank { null } ?: return Charsets.UTF_8
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    private fun header(
        headers: Map<String, List<String>>,
        name: String,
    ): String? = headerValues(headers, name)?.firstOrNull()

    private fun headerValues(
        headers: Map<String, List<String>>,
        name: String,
    ): List<String>? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun HttpTimings.plus(other: HttpTimings): HttpTimings =
        HttpTimings(
            dnsMs = add(dnsMs, other.dnsMs),
            connectMs = add(connectMs, other.connectMs),
            tlsMs = add(tlsMs, other.tlsMs),
            ttfbMs = add(ttfbMs, other.ttfbMs),
            // Intermediate redirect bodies are deliberately closed unread. Transfer timing is
            // therefore taken only from the final response after its bounded read completes.
            transferMs = transferMs,
            totalMs = totalMs + other.totalMs,
        )

    private fun add(
        first: Long?,
        second: Long?,
    ): Long? =
        when {
            first == null -> second
            second == null -> first
            else -> first + second
        }
}
