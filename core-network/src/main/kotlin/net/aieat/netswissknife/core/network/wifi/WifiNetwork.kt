package net.aieat.netswissknife.core.network.wifi

/**
 * A logical Wi-Fi network: one SSID/security pair that may be served by multiple
 * BSSIDs (access points) — e.g. a dual-band or mesh router.
 *
 * Hidden networks (blank SSID) are never grouped: each gets its own WifiNetwork
 * keyed by BSSID so a noisy environment doesn't collapse them into one row.
 */
data class WifiNetwork(
    val ssid: String,
    val security: WifiSecurity,
    /** All BSSIDs for this network, sorted strongest-first. */
    val accessPoints: List<WifiAccessPoint>
) {
    /**
     * Stable identity for list keys and per-row UI state.
     *
     * [WifiNetworkGrouper] emits one network per (SSID, security) pair, so that
     * pair identifies a visible network and stays stable across rescans even as
     * BSSIDs come and go. Hidden networks all share a blank SSID and are never
     * grouped, so they are keyed by their single BSSID instead — without this
     * they would collide, which both merges their expand state and makes them
     * illegal duplicate keys in a lazy list.
     */
    val id: String get() =
        if (ssid.isBlank()) "|hidden|${accessPoints.firstOrNull()?.bssid.orEmpty()}"
        else "$ssid|${security.name}"

    /** Human-readable name. Hidden networks show last 8 chars of their BSSID. */
    val displaySsid: String get() = ssid.ifBlank {
        "<Hidden: ${accessPoints.first().bssid.takeLast(8)}>"
    }

    /** Strongest RSSI across all BSSIDs. */
    val bestRssi: Int get() = accessPoints.maxOf { it.rssi }

    /** Frequency bands served by this network. */
    val bands: Set<WifiBand> get() = accessPoints.map { it.band }.toSet()

    /** Bands sorted from lowest to highest frequency. */
    val sortedBands: List<WifiBand> get() = bands.sortedBy { it.ordinal }

    val bssidCount: Int get() = accessPoints.size
    val isConnected: Boolean get() = accessPoints.any { it.isConnected }

    val signalQualityPercent: Int get() = accessPoints.maxOf { it.signalQualityPercent }

    /** Best (strongest) signal level across all BSSIDs. */
    val signalLevel: SignalLevel get() =
        accessPoints.minByOrNull { it.signalLevel.ordinal }?.signalLevel ?: SignalLevel.POOR

    /** Stable color index derived from SSID + security; same network → same color across scans. */
    val colorIndex: Int get() =
        ((ssid + security.name).hashCode() and Int.MAX_VALUE) % PALETTE_SIZE

    companion object {
        const val PALETTE_SIZE = 12
    }
}
