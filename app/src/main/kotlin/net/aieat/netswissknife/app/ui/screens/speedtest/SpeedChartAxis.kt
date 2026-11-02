package net.aieat.netswissknife.app.ui.screens.speedtest

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Step between chart grid lines so that [intervals] steps cover [maxValue] with a round number
 * (1, 2 or 5 times a power of ten). A raw peak such as 29.4 would otherwise give uneven labels (V6B-08).
 */
internal fun niceAxisStep(
    maxValue: Double,
    intervals: Int = 3,
): Double {
    val raw = maxValue / intervals
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice =
        when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
    return nice * magnitude
}
