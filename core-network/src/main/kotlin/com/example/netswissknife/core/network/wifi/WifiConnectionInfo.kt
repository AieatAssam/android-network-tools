package com.example.netswissknife.core.network.wifi

/**
 * Detailed information about the currently connected Wi-Fi network.
 * This is richer than [WifiAccessPoint] because it includes live link
 * statistics only available when actively associated.
 */
data class WifiConnectionInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    /** Negotiated link speed in Mbps (legacy symmetric value). */
    val linkSpeedMbps: Int,
    /** TX (upload) link speed in Mbps. Available on API 29+; −1 if unavailable. */
    val txLinkSpeedMbps: Int,
    /** RX (download) link speed in Mbps. Available on API 29+; −1 if unavailable. */
    val rxLinkSpeedMbps: Int,
    /** Dotted-decimal IPv4 address assigned to the device, or empty string. */
    val ipAddress: String,
    val standard: WifiStandard,
    val security: WifiSecurity,
    /** SSID of the Wi-Fi network (same as ssid; kept for explicit labelling). */
    val networkSsid: String = ssid
) {
    val signalQualityPercent: Int get() = when {
        rssi >= -50 -> 100
        rssi >= -60 -> (100 - ((-50 - rssi) * 2)).coerceIn(0, 100)
        rssi >= -70 -> (80  - ((-60 - rssi) * 2)).coerceIn(0, 100)
        rssi >= -80 -> (60  - ((-70 - rssi) * 2)).coerceIn(0, 100)
        rssi >= -90 -> (40  - ((-80 - rssi) * 2)).coerceIn(0, 100)
        else        -> 0
    }
}
