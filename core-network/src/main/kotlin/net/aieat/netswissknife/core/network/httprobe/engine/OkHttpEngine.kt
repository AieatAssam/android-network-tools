package net.aieat.netswissknife.core.network.httprobe.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseHeaderLimitException
import okhttp3.ResponseHeaderLimitKind
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.FilterInputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

private const val RESPONSE_HEADER_FIELD_LIMIT = ResponseHeaderLimitException.MAX_FIELD_OCCURRENCES
private const val RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT = ResponseHeaderLimitException.MAX_AGGREGATE_METADATA_BYTES
private const val RESPONSE_HEADER_VALUE_BYTE_LIMIT = ResponseHeaderLimitException.MAX_VALUE_UTF8_BYTES
private const val RESPONSE_HEADER_FIELD_OVERHEAD_BYTES = ResponseHeaderLimitException.FIELD_FRAMING_BYTES

/** Default manual-redirect HTTP transport for the HTTP probe. */
class OkHttpEngine(client: OkHttpClient = OkHttpClient()) : HttpEngine {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        // This tool must not inherit ambient application credentials or state.
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cache(null)
        .eventListenerFactory { call -> call.request().tag(TimingRecorder::class.java) ?: EventListener.NONE }
        .build()

    override fun newCall(request: HttpEngineRequest): HttpEngineCall {
        val recorder = TimingRecorder()
        val url = request.url.toHttpUrl()
        val builder = Request.Builder().url(url).tag(TimingRecorder::class.java, recorder)
        var hasAcceptEncoding = false
        request.headers.forEach { (name, value) ->
            if (name.equals("Accept-Encoding", ignoreCase = true)) hasAcceptEncoding = true
            builder.header(name, value)
        }
        if (!hasAcceptEncoding) builder.header("Accept-Encoding", "identity")

        val body = request.body?.toRequestBody(null as okhttp3.MediaType?)
            ?: if (request.method in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody(null as okhttp3.MediaType?) else null
        builder.method(request.method, body)
        // A parser limit violation fails the HPACK connection to preserve compression state. Give
        // each HTTP Probe call its own pool so that one rejected response cannot fail sibling calls.
        val callClient = client.newBuilder().connectionPool(ConnectionPool()).build()
        val call = callClient.newCall(builder.build())
        call.timeout().timeout(request.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return OkHttpEngineCall(call, recorder)
    }
}

private class OkHttpEngineCall(
    private val call: Call,
    private val recorder: TimingRecorder,
) : HttpEngineCall {
    @Volatile private var response: okhttp3.Response? = null

    override suspend fun execute(): HttpEngineResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                this@OkHttpEngineCall.response = response
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    validateResponseHeaderLimits(response.headers)
                } catch (e: ResponseHeaderLimitException) {
                    response.close()
                    continuation.resumeWithException(e)
                    return
                }
                val headers = buildMap<String, List<String>> {
                    for (index in 0 until response.headers.size) {
                        val name = response.headers.name(index)
                        val value = response.headers.value(index)
                        val prior = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.key
                        val key = prior ?: name
                        put(key, get(key).orEmpty() + value)
                    }
                }
                val result = HttpEngineResponse(
                    statusCode = response.code,
                    statusMessage = response.message,
                    headers = headers,
                    body = response.body?.byteStream()?.let { stream ->
                        object : FilterInputStream(stream) {
                            override fun close() {
                                try { super.close() } finally { recorder.bodyClosed() }
                            }
                        }
                    },
                    protocol = response.protocol.toString(),
                    timings = recorder.snapshot(),
                    timingSnapshot = recorder::snapshot,
                )
                continuation.resume(result, onCancellation = { _, _, _ -> response.close() })
            }
        })
    }

    override fun close() {
        call.cancel()
        response?.close()
    }
}

/** Defense in depth for injected clients whose interceptors may synthesize a Response directly. */
internal fun validateResponseHeaderLimits(headers: Headers) {
    var fieldCount = 0
    var metadataByteCount = 0
    for (index in 0 until headers.size) {
        fieldCount++
        if (fieldCount > RESPONSE_HEADER_FIELD_LIMIT) {
            throw ResponseHeaderLimitException(ResponseHeaderLimitKind.FIELD_COUNT, RESPONSE_HEADER_FIELD_LIMIT)
        }
        val name = headers.name(index)
        val value = headers.value(index)
        val valueByteCount = value.toByteArray(Charsets.UTF_8).size
        if (valueByteCount > RESPONSE_HEADER_VALUE_BYTE_LIMIT) {
            throw ResponseHeaderLimitException(ResponseHeaderLimitKind.VALUE_BYTES, RESPONSE_HEADER_VALUE_BYTE_LIMIT)
        }
        val fieldByteCount = name.toByteArray(Charsets.UTF_8).size + valueByteCount + RESPONSE_HEADER_FIELD_OVERHEAD_BYTES
        if (metadataByteCount + fieldByteCount > RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT) {
            throw ResponseHeaderLimitException(ResponseHeaderLimitKind.AGGREGATE_BYTES, RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT)
        }
        metadataByteCount += fieldByteCount
    }
}

/** EventListener-owned per-call monotonic timing snapshots. */
private class TimingRecorder : EventListener() {
    @Volatile private var callStart: Long? = null
    @Volatile private var dnsStart: Long? = null
    @Volatile private var dnsEnd: Long? = null
    @Volatile private var connectStart: Long? = null
    @Volatile private var connectEnd: Long? = null
    @Volatile private var tlsStart: Long? = null
    @Volatile private var tlsEnd: Long? = null
    @Volatile private var headersStart: Long? = null
    @Volatile private var headersEnd: Long? = null
    @Volatile private var bodyEnd: Long? = null

    override fun callStart(call: Call) { callStart = System.nanoTime() }
    override fun dnsStart(call: Call, domainName: String) { dnsStart = System.nanoTime() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { dnsEnd = System.nanoTime() }
    override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { connectStart = System.nanoTime() }
    override fun connectEnd(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) { connectEnd = System.nanoTime() }
    override fun secureConnectStart(call: Call) { tlsStart = System.nanoTime() }
    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) { tlsEnd = System.nanoTime() }
    override fun responseHeadersStart(call: Call) { headersStart = System.nanoTime() }
    override fun responseHeadersEnd(call: Call, response: okhttp3.Response) { headersEnd = System.nanoTime() }
    override fun responseBodyEnd(call: Call, byteCount: Long) { bodyEnd = System.nanoTime() }

    fun bodyClosed() {
        if (bodyEnd == null) bodyEnd = System.nanoTime()
    }

    fun snapshot(): HttpTimings {
        val started = callStart
        val headers = headersStart
        val endedHeaders = headersEnd
        val bodyFinished = bodyEnd
        return HttpTimings(
            dnsMs = elapsed(dnsStart, dnsEnd),
            connectMs = elapsed(connectStart, connectEnd),
            tlsMs = elapsed(tlsStart, tlsEnd),
            ttfbMs = elapsed(started, headers),
            transferMs = elapsed(endedHeaders, bodyFinished),
            totalMs = elapsed(started, bodyFinished ?: endedHeaders) ?: 0L,
        )
    }

    private fun elapsed(start: Long?, end: Long?): Long? =
        if (start == null || end == null) null else ((end - start) / 1_000_000L).coerceAtLeast(0L)
}
