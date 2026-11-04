package net.aieat.netswissknife.core.network.netinfo

/**
 * Turns raw platform values into either a real value or a stated reason it is unavailable.
 * A placeholder is never shown as if it were a device value.
 */
object MacAddressPolicy {
    private const val PLACEHOLDER = "02:00:00:00:00:00"
    private const val ALL_ZERO = "00:00:00:00:00:00"
    private const val ANDROID_10_API = 29
    private const val OCTET_COUNT = 6
    private const val LOCALLY_ADMINISTERED_BIT = 0x02

    /**
     * [raw] is the value read from the interface, [sdkInt] the running API level. Android 10
     * and newer hide the hardware address, so a missing value there is explained as a restriction.
     */
    fun resolve(
        raw: String?,
        sdkInt: Int,
    ): MacAddressState {
        val restricted =
            if (sdkInt >= ANDROID_10_API) MacUnavailableReason.HIDDEN_BY_ANDROID else MacUnavailableReason.NOT_REPORTED
        val canonical = raw?.takeIf { it.isNotBlank() }?.let(::canonicalOrNull)
        return when {
            raw.isNullOrBlank() -> MacAddressState.Unavailable(restricted)
            canonical == null -> MacAddressState.Unavailable(MacUnavailableReason.NOT_REPORTED)
            canonical == PLACEHOLDER || canonical == ALL_ZERO -> MacAddressState.Unavailable(restricted)
            else -> MacAddressState.Known(canonical, randomized = isLocallyAdministered(canonical))
        }
    }

    /** Upper-case colon form of a MAC in one consistent notation, or null. */
    private fun canonicalOrNull(raw: String): String? {
        val text = raw.trim()
        val hasColon = text.contains(':')
        val hasHyphen = text.contains('-')
        val pieces = text.split(':', '-')
        val valid = pieces.size == OCTET_COUNT && pieces.all { it.length == 2 && it.all(::isHexDigit) }
        return if (valid && !(hasColon && hasHyphen)) pieces.joinToString(":") { it.uppercase() } else null
    }

    private fun isLocallyAdministered(canonical: String): Boolean {
        val firstOctet = canonical.substring(0, 2).toInt(HEX)
        return (firstOctet and LOCALLY_ADMINISTERED_BIT) != 0
    }

    private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

    private const val HEX = 16
}

/** Wi-Fi network name. Android redacts it without location permission, so a name is shown only with it. */
object WifiSsidPolicy {
    private const val UNKNOWN_SSID = "<unknown ssid>"

    fun resolve(
        raw: String?,
        locationPermissionGranted: Boolean,
    ): SsidState {
        if (!locationPermissionGranted) return SsidState.Unavailable(SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED)
        val name = raw?.removeSurrounding("\"")?.trim()
        return when {
            name.isNullOrBlank() || name == UNKNOWN_SSID -> SsidState.Unavailable(SsidUnavailableReason.NOT_REPORTED)
            else -> SsidState.Known(name)
        }
    }
}

/** Platform sentinel values (-127 dBm, -1 Mbps, 0 MHz or MTU) that mean "unknown". */
object LinkValueSentinels {
    private const val RSSI_UNKNOWN = -127
    private const val RSSI_WEAKEST = -126
    private const val RSSI_STRONGEST = 0

    fun rssiDbm(raw: Int): Int? = raw.takeIf { it != RSSI_UNKNOWN && it in RSSI_WEAKEST..RSSI_STRONGEST }

    fun linkSpeedMbps(raw: Int): Int? = raw.takeIf { it > 0 }

    fun frequencyMhz(raw: Int): Int? = raw.takeIf { it > 0 }

    fun mtuBytes(raw: Int): Int? = raw.takeIf { it > 0 }
}
