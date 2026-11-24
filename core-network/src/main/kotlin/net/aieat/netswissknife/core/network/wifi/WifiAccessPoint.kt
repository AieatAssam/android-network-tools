package net.aieat.netswissknife.core.network.wifi

/**
 * Represents a single discovered Wi-Fi access point from a scan.
 */
data class WifiAccessPoint(
    /** Network name (SSID). Empty string for hidden networks. */
    val ssid: String,
    /** BSSID (MAC address) of the access point. */
    val bssid: String,
    /** Received signal strength in dBm (negative value; higher = stronger). */
    val rssi: Int,
    /** Center frequency of the primary channel in MHz. */
    val frequency: Int,
    /** Channel width in MHz (20, 40, 80, 160, 320). */
    val channelWidthMhz: Int,
    /** Raw capabilities string from the scan result (e.g. "[WPA2-PSK-CCMP][ESS]"). */
    val capabilities: String,
    /** Logical Wi-Fi channel number. */
    val channel: Int,
    /** Frequency band (2.4 / 5 / 6 GHz). */
    val band: WifiBand,
    /** Wi-Fi generation standard. */
    val standard: WifiStandard,
    /** Parsed security type. */
    val security: WifiSecurity,
    /** True when this AP is the currently associated network. */
    val isConnected: Boolean,
    /** Hardware vendor resolved from the BSSID OUI prefix, or empty if unknown. */
    val vendor: String,
    /** Center frequency of the first segment (used for 80+80 MHz). */
    val centerFrequency0: Int,
    /** Center frequency of the second segment (used for 80+80 MHz; 0 if unused). */
    val centerFrequency1: Int,
    /** Timestamp in microseconds since device boot when the AP was last seen. */
    val timestampUs: Long,
) {
    /** Display name: falls back to "<Hidden Network>" when SSID is blank. */
    val displaySsid: String get() = ssid.ifBlank { "<Hidden Network>" }

    /**
     * Coarse quality bucket: EXCELLENT / GOOD / FAIR / WEAK / POOR.
     * Primarily used for colour-coding in the UI layer.
     */
    val signalLevel: SignalLevel get() = signalLevelForRssi(rssi)

    /** Whether the AP operates in the 80+80 MHz split-channel mode. */
    val is80Plus80: Boolean get() = channelWidthMhz == 160 && centerFrequency1 != 0
}

enum class SignalLevel { EXCELLENT, GOOD, FAIR, WEAK, POOR }

private const val RSSI_EXCELLENT_DBM = -50
private const val RSSI_GOOD_DBM = -60
private const val RSSI_FAIR_DBM = -70
private const val RSSI_WEAK_DBM = -80

/** Level bucket for an RSSI reading in dBm: -50 and above is EXCELLENT, below -80 is POOR. */
fun signalLevelForRssi(rssi: Int): SignalLevel =
    when {
        rssi >= RSSI_EXCELLENT_DBM -> SignalLevel.EXCELLENT
        rssi >= RSSI_GOOD_DBM -> SignalLevel.GOOD
        rssi >= RSSI_FAIR_DBM -> SignalLevel.FAIR
        rssi >= RSSI_WEAK_DBM -> SignalLevel.WEAK
        else -> SignalLevel.POOR
    }

/**
 * Pure-Kotlin helpers for converting raw scan data to channel and band information.
 * These are used by both the Android repository impl and tests.
 */
object WifiChannelHelper {
    fun frequencyToChannel(
        frequencyMhz: Int,
        band: WifiBand,
    ): Int =
        when (band) {
            WifiBand.BAND_2_4GHZ -> {
                when (frequencyMhz) {
                    2484 -> 14
                    else -> ((frequencyMhz - 2407) / 5).coerceIn(1, 13)
                }
            }

            WifiBand.BAND_5GHZ -> {
                if (frequencyMhz in 4900 until 5000) {
                    // 4.9 GHz channel numbering uses a separate 4000 MHz base.
                    ((frequencyMhz - 4000) / 5).coerceIn(180, 199)
                } else {
                    ((frequencyMhz - 5000) / 5).coerceAtMost(177)
                }
            }

            WifiBand.BAND_6GHZ -> {
                ((frequencyMhz - 5950) / 5).coerceIn(1, 233)
            }

            WifiBand.BAND_60GHZ -> {
                ((frequencyMhz - 56160) / 2160).coerceIn(1, 6)
            }

            WifiBand.UNKNOWN -> {
                0
            }
        }

    fun channelWidthMhz(channelWidthConstant: Int): Int =
        when (channelWidthConstant) {
            0 -> 20

            // CHANNEL_WIDTH_20MHZ
            1 -> 40

            // CHANNEL_WIDTH_40MHZ
            2 -> 80

            // CHANNEL_WIDTH_80MHZ
            3 -> 160

            // CHANNEL_WIDTH_160MHZ
            4 -> 160

            // CHANNEL_WIDTH_80MHZ_PLUS_MHZ (80+80)
            5 -> 320

            // CHANNEL_WIDTH_320MHZ (Wi-Fi 7)
            else -> 20
        }

    /**
     * Returns the set of 2.4 GHz channel numbers that overlap with [channel].
     * Channels overlap if they are within ±4 of each other, except channel 14's
     * wider frequency spacing means only channels 12 and 13 overlap it in addition to itself.
     */
    fun overlapping24GHzChannels(channel: Int): Set<Int> =
        ((channel - 4)..(channel + 4))
            .filter { it in 1..14 }
            .filter { candidate ->
                when {
                    channel == 14 -> candidate >= 12
                    candidate == 14 -> channel >= 12
                    else -> true
                }
            }.toSet()
}
