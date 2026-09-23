package net.aieat.netswissknife.core.network.wifi

/**
 * Complete result of a Wi-Fi environment scan.
 */
data class WifiScanResult(
    /** All discovered access points, sorted by RSSI descending (strongest first). */
    val accessPoints: List<WifiAccessPoint>,
    /** Per-channel statistics across all bands detected. */
    val channels: List<WifiChannelInfo>,
    /** Live info about the currently associated network; null if not connected to Wi-Fi. */
    val connectedNetwork: WifiConnectionInfo?,
    /** Estimated wall-clock time of the newest scan sample, or 0 when unavailable. */
    val scanTimestampMs: Long,
    /** Whether Wi-Fi is currently enabled on the device. */
    val isWifiEnabled: Boolean,
    /** Whether the newest returned scan result is no more than 15 seconds old. */
    val isFresh: Boolean = true,
    /** Age of the newest scan result, measured from elapsed realtime, or null if unknown. */
    val scanAgeMs: Long? = null,
    /** Elapsed realtime when this cached sample's age was calculated. */
    val cacheReadElapsedRealtimeMs: Long = 0L,
    /** Outcome of the latest request to refresh Android's scan cache. */
    val refreshStatus: WifiScanRefreshStatus = WifiScanRefreshStatus.NOT_REQUESTED,
    /** Whether Location Services were enabled when this result was read. */
    val locationEnabled: Boolean = true
) {
    /** Access points grouped into logical networks by (SSID, security). Computed once at construction. */
    val networks: List<WifiNetwork> = WifiNetworkGrouper.group(accessPoints)

    /** Access points grouped by frequency band. */
    val byBand: Map<WifiBand, List<WifiAccessPoint>> get() =
        accessPoints.groupBy { it.band }

    /** All distinct bands present in the scan results. */
    val detectedBands: List<WifiBand> get() =
        byBand.keys.sortedBy { it.ordinal }

    /**
     * Number of logical network rows: visible SSID/security groups plus each hidden AP.
     * Multiple BSSIDs for one visible SSID/security pair count once; the same SSID with
     * different security counts separately.
     */
    val uniqueNetworkCount: Int get() =
        networks.size

    /** Channel with the highest congestion score, or null if no channels. */
    val busiestChannel: WifiChannelInfo? get() =
        channels.maxByOrNull { it.congestionScore }

    /** Recommended clear channel for 2.4 GHz (one of 1, 6, 11). */
    val bestChannel24GHz: Int? get() {
        val standard24 = listOf(1, 6, 11)
        return standard24.minByOrNull { ch ->
            channels.find { it.channel == ch && it.band == WifiBand.BAND_2_4GHZ }
                ?.congestionScore ?: 0f
        }
    }
}
