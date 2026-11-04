package net.aieat.netswissknife.core.network.wifi

import kotlin.math.pow

/** Pure aggregation of access points into per-band channel statistics. */
object WifiChannelAnalyzer {
    private const val DECIBEL_DECADE = 10.0

    /** Interference of [VERY_BUSY_AP_COUNT] APs at [VERY_BUSY_RSSI_DBM] is treated as a score of 1.0. */
    private const val VERY_BUSY_RSSI_DBM = -40.0
    private const val VERY_BUSY_AP_COUNT = 10.0

    /** Linear signal power of an RSSI value in dBm. */
    private fun linearPower(rssiDbm: Int): Double = 10.0.pow(rssiDbm / DECIBEL_DECADE)

    /** Normalize: treat -40 dBm * 10 APs as "very busy" → score ≈ 1.0. */
    private fun normalize(interference: Double): Float {
        val maxInterference = 10.0.pow(VERY_BUSY_RSSI_DBM / DECIBEL_DECADE) * VERY_BUSY_AP_COUNT
        return (interference / maxInterference).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Interference score for a 2.4 GHz [channel], summing every 2.4 GHz access point whose
     * channel overlaps it (±4 rule, see [WifiChannelHelper.overlapping24GHzChannels]).
     * Works for channels that have no access point of their own: such a channel still
     * scores its overlap. Comparisons should use this, not a clamped score, so saturated
     * channels still rank against each other.
     */
    fun congestionScore24GHz(
        channel: Int,
        accessPoints: List<WifiAccessPoint>,
    ): Float {
        val overlapping = WifiChannelHelper.overlapping24GHzChannels(channel)
        return normalize(
            accessPoints
                .filter { it.band == WifiBand.BAND_2_4GHZ && it.channel in overlapping }
                .sumOf { linearPower(it.rssi) },
        )
    }

    /** Raw linear interference for 2.4 GHz [channel]; used to rank channels without clamping. */
    internal fun rawInterference24GHz(
        channel: Int,
        accessPoints: List<WifiAccessPoint>,
    ): Double {
        val overlapping = WifiChannelHelper.overlapping24GHzChannels(channel)
        return accessPoints
            .filter { it.band == WifiBand.BAND_2_4GHZ && it.channel in overlapping }
            .sumOf { linearPower(it.rssi) }
    }

    fun analyze(accessPoints: List<WifiAccessPoint>): List<WifiChannelInfo> {
        val byBandChannelAndFrequency = accessPoints.groupBy { Triple(it.band, it.channel, it.frequency) }

        return byBandChannelAndFrequency
            .map { (key, aps) ->
                val (band, channel, frequencyMhz) = key

                // Effective interference is the sum of linear signal powers for this channel
                // and, for 2.4 GHz only, the channels that overlap it.
                val congestion =
                    when (band) {
                        WifiBand.BAND_2_4GHZ -> congestionScore24GHz(channel, accessPoints)
                        else -> normalize(aps.sumOf { linearPower(it.rssi) })
                    }

                WifiChannelInfo(
                    channel = channel,
                    frequencyMhz = frequencyMhz,
                    band = band,
                    accessPointCount = aps.size,
                    congestionScore = congestion,
                    accessPoints = aps.sortedByDescending { it.rssi },
                )
            }.sortedWith(compareBy({ it.band.ordinal }, { it.channel }, { it.frequencyMhz }))
    }
}
