package net.aieat.netswissknife.core.network.httprobe.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-release fallback for integrations that still require URLConnection semantics.
 * Production repository construction uses [OkHttpEngine]; this adapter is never selected by default.
 */
internal class LegacyHttpEngine(
    private val connectionFactory: LegacyConnectionFactory,
) : HttpEngine {
    override fun newCall(request: HttpEngineRequest): HttpEngineCall =
        LegacyHttpCall(connectionFactory.open(URL(request.url)), request)
}

internal fun interface LegacyConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

private class LegacyHttpCall(
    private val connection: HttpURLConnection,
    private val request: HttpEngineRequest,
) : HttpEngineCall {
    @Volatile private var body: java.io.InputStream? = null

    override suspend fun execute(): HttpEngineResponse = withContext(Dispatchers.IO) {
        connection.instanceFollowRedirects = false
        connection.requestMethod = request.method
        connection.connectTimeout = request.timeoutMs
        connection.readTimeout = request.timeoutMs
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        request.body?.let { bytes ->
            connection.doOutput = true
            connection.outputStream.use { it.write(bytes) }
        }
        val started = System.nanoTime()
        connection.connect()
        val code = connection.responseCode
        val ended = System.nanoTime()
        val headers = buildMap<String, List<String>> {
            connection.headerFields.forEach { (key, values) -> if (key != null) put(key, values) }
        }
        body = if (code >= 400) connection.errorStream else runCatching { connection.inputStream }.getOrNull()
        HttpEngineResponse(
            statusCode = code,
            statusMessage = connection.responseMessage ?: "",
            headers = headers,
            body = body,
            protocol = "http/1.1",
            timings = HttpTimings(ttfbMs = ((ended - started) / 1_000_000L).coerceAtLeast(0L)),
        )
    }

    override fun close() {
        runCatching { body?.close() }
        connection.disconnect()
    }
}
