package net.aieat.netswissknife.core.network.httprobe.engine

import java.io.InputStream

/** Small transport boundary: redirect policy and result mapping stay in the repository. */
interface HttpEngine {
    fun newCall(request: HttpEngineRequest): HttpEngineCall
}

interface HttpEngineCall : AutoCloseable {
    suspend fun execute(): HttpEngineResponse
}

data class HttpEngineRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Int,
)

data class HttpEngineResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, List<String>>,
    val body: InputStream?,
    val protocol: String,
    val timings: HttpTimings,
    val timingSnapshot: () -> HttpTimings = { timings },
)

data class HttpTimings(
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val ttfbMs: Long? = null,
    val transferMs: Long? = null,
    val totalMs: Long = 0,
)

/** One redirect response, retaining exactly the evidence supplied by that response. */
data class RedirectHop(
    val url: String,
    val statusCode: Int,
    val location: String,
)
