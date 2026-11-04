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
        /**
         * DNS, connect and TLS are sequential setup phases that all happen before the first byte.
         * Each phase is capped by the time left in TTFB so the displayed phases never add up above
         * it. Server wait is what remains of TTFB, so the stacked bar always reconciles to TTFB.
         */
        fun from(timings: HttpTimings): HttpTimingBreakdown {
            val ttfb = timings.ttfbMs?.coerceAtLeast(0L)
            var remaining = ttfb ?: Long.MAX_VALUE

            fun fit(phase: Long?): Long? {
                if (phase == null) return null
                val fitted = phase.coerceAtLeast(0L).coerceAtMost(remaining)
                remaining -= fitted
                return fitted
            }

            val dnsMs = fit(timings.dnsMs)
            val connectMs = fit(timings.connectMs)
            val tlsMs = fit(timings.tlsMs)
            val serverWaitMs = ttfb?.let { remaining.coerceAtLeast(0L) }
            return HttpTimingBreakdown(
                dnsMs = dnsMs,
                connectMs = connectMs,
                tlsMs = tlsMs,
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
