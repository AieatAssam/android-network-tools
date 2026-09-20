package net.aieat.netswissknife.core.network.wifi

/** Result of comparing a ScanResult timestamp with elapsed realtime. */
data class WifiScanFreshnessResult(
    val ageMs: Long?,
    val isFresh: Boolean
)

/** Pure timestamp math kept outside the Android WifiManager adapter. */
object WifiScanFreshness {
    const val FRESHNESS_THRESHOLD_MS = 15_000L

    /**
     * [newestTimestampUs] is the microseconds-since-boot value exposed by
     * Android's ScanResult.timestamp. [nowElapsedMs] is elapsed realtime in ms.
     */
    fun compute(newestTimestampUs: Long?, nowElapsedMs: Long): WifiScanFreshnessResult {
        val ageMs = newestTimestampUs?.let { timestampUs ->
            ((nowElapsedMs * 1_000L) - timestampUs)
                .coerceAtLeast(0L) / 1_000L
        }
        return WifiScanFreshnessResult(
            ageMs = ageMs,
            isFresh = ageMs != null && ageMs <= FRESHNESS_THRESHOLD_MS
        )
    }
}
