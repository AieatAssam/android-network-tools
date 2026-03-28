package net.aieat.netswissknife.core.network.wifi

/**
 * Aggregated statistics for a single Wi-Fi channel, computed from scan results.
 */
data class WifiChannelInfo(
    /** Logical channel number (e.g. 1, 6, 11 for 2.4 GHz; 36, 40 … for 5 GHz). */
    val channel: Int,
    /** Primary center frequency of this channel in MHz. */
    val frequencyMhz: Int,
    val band: WifiBand,
    /** Number of access points detected on this channel. */
    val accessPointCount: Int,
    /**
     * Congestion score in [0.0, 1.0].
     * Derived from the number and signal strength of APs using this channel
     * (including overlapping channels for 2.4 GHz).
     */
    val congestionScore: Float,
    /** Access points operating on this exact channel, sorted by RSSI descending. */
    val accessPoints: List<WifiAccessPoint>
) {
    /** Human-readable congestion label for the UI. */
    val congestionLabel: String get() = when {
        congestionScore >= 0.75f -> "Very Busy"
        congestionScore >= 0.50f -> "Busy"
        congestionScore >= 0.25f -> "Moderate"
        else                     -> "Clear"
    }
}
