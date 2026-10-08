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

/** Display-ready phases; TTFB is a total from call start, so only its residual is stacked. */
data class HttpTimingBreakdown(
    val dnsMs: Long?,
    val connectMs: Long?,
    val tlsMs: Long?,
    val serverWaitMs: Long?,
    val ttfbTotalMs: Long?,
    val transferMs: Long?,
) {
    companion object {
        fun from(timings: HttpTimings): HttpTimingBreakdown {
            val setupMs =
                listOfNotNull(timings.dnsMs, timings.connectMs, timings.tlsMs)
                    .fold(0L) { sum, phase ->
                        val value = phase.coerceAtLeast(0L)
                        if (Long.MAX_VALUE - sum < value) Long.MAX_VALUE else sum + value
                    }
            val serverWaitMs =
                timings.ttfbMs?.let { ttfb ->
                    (ttfb.coerceAtLeast(0L) - setupMs).coerceAtLeast(0L)
                }
            return HttpTimingBreakdown(
                dnsMs = timings.dnsMs,
                connectMs = timings.connectMs,
                tlsMs = timings.tlsMs,
                serverWaitMs = serverWaitMs,
                ttfbTotalMs = timings.ttfbMs,
                transferMs = timings.transferMs,
            )
        }
    }
}

/** One redirect response, retaining exactly the evidence supplied by that response. */
data class RedirectHop(
    val url: String,
    val statusCode: Int,
    val location: String,
)
