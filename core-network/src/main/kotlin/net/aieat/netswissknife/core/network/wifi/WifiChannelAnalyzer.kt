package net.aieat.netswissknife.core.network.wifi

import kotlin.math.pow

/** Pure aggregation of access points into per-band channel statistics. */
object WifiChannelAnalyzer {

    fun analyze(accessPoints: List<WifiAccessPoint>): List<WifiChannelInfo> {
        val byBandChannelAndFrequency = accessPoints.groupBy { Triple(it.band, it.channel, it.frequency) }

        return byBandChannelAndFrequency.map { (key, aps) ->
            val (band, channel, frequencyMhz) = key

            // Effective interference is the sum of linear signal powers for this channel
            // and, for 2.4 GHz only, the channels that overlap it.
            val interference = when (band) {
                WifiBand.BAND_2_4GHZ -> {
                    val overlapping = WifiChannelHelper.overlapping24GHzChannels(channel)
                    accessPoints
                        .filter { it.band == WifiBand.BAND_2_4GHZ && it.channel in overlapping }
                        .sumOf { 10.0.pow(it.rssi / 10.0) }
                }
                else -> aps.sumOf { 10.0.pow(it.rssi / 10.0) }
            }

            // Normalize: treat -40 dBm * 10 APs as "very busy" → score ≈ 1.0.
            val maxInterference = 10.0.pow(-40.0 / 10.0) * 10.0
            val congestion = (interference / maxInterference).coerceIn(0.0, 1.0).toFloat()

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
