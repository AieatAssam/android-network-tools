package net.aieat.netswissknife.core.network.wifi

enum class WifiBand(
    val displayName: String,
    val ghzLabel: String,
    val minFrequencyMhz: Int,
    val maxFrequencyMhz: Int
) {
    BAND_2_4GHZ("2.4 GHz", "2.4", 2400, 2500),
    BAND_5GHZ("5 GHz", "5", 4900, 5925),
    BAND_6GHZ("6 GHz", "6", 5925, 7125),
    BAND_60GHZ("60 GHz", "60", 57000, 71000),
    UNKNOWN("Unknown", "?", 0, 0);

    companion object {
        fun fromFrequency(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
            in 2400..2500 -> BAND_2_4GHZ
            in 4900..5925 -> BAND_5GHZ
            in 5925..7125 -> BAND_6GHZ
            in 57000..71000 -> BAND_60GHZ
            else -> UNKNOWN
        }
    }
}
