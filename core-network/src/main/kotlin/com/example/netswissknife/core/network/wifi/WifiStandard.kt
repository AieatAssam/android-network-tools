package com.example.netswissknife.core.network.wifi

enum class WifiStandard(
    val displayName: String,
    val generationLabel: String,
    val protocolName: String
) {
    LEGACY("Legacy", "Wi-Fi", "802.11 a/b/g"),
    WIFI_4("Wi-Fi 4", "Wi-Fi 4", "802.11n"),
    WIFI_5("Wi-Fi 5", "Wi-Fi 5", "802.11ac"),
    WIFI_6("Wi-Fi 6", "Wi-Fi 6", "802.11ax"),
    WIFI_6E("Wi-Fi 6E", "Wi-Fi 6E", "802.11ax 6 GHz"),
    WIFI_7("Wi-Fi 7", "Wi-Fi 7", "802.11be"),
    UNKNOWN("Unknown", "Unknown", "Unknown");

    /** Maximum theoretical throughput label for UI display. */
    val maxSpeedLabel: String get() = when (this) {
        LEGACY -> "54 Mbps"
        WIFI_4 -> "600 Mbps"
        WIFI_5 -> "3.5 Gbps"
        WIFI_6 -> "9.6 Gbps"
        WIFI_6E -> "9.6 Gbps"
        WIFI_7 -> "46 Gbps"
        UNKNOWN -> "—"
    }
}
