package com.example.netswissknife.core.network.wifi

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
    /** Wall-clock time when the scan completed (System.currentTimeMillis()). */
    val scanTimestampMs: Long,
    /** Whether Wi-Fi is currently enabled on the device. */
    val isWifiEnabled: Boolean
) {
    /** Access points grouped by frequency band. */
    val byBand: Map<WifiBand, List<WifiAccessPoint>> get() =
        accessPoints.groupBy { it.band }

    /** All distinct bands present in the scan results. */
    val detectedBands: List<WifiBand> get() =
        byBand.keys.sortedBy { it.ordinal }

    /** Total number of unique SSIDs (networks, not BSSIDs). */
    val uniqueNetworkCount: Int get() =
        accessPoints.map { it.ssid }.toSet().size

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
