package net.aieat.netswissknife.app.ui.screens.wifi

import net.aieat.netswissknife.app.ui.screens.wifiSpectrumLabelLeft
import net.aieat.netswissknife.core.network.wifi.WifiAccessPoint

/** A measured SSID label waiting for a position on the spectrum canvas. */
internal data class SpectrumLabelBox(
    val id: Int,
    val centerX: Float,
    val width: Float,
    val height: Float,
    val peakY: Float,
)

/** Top-left position for the label with [id]; labels that were not placed are absent. */
internal data class SpectrumLabelPlacement(
    val id: Int,
    val left: Float,
    val top: Float,
)

private const val LABEL_GAP_ABOVE_PEAK_PX = 4f
private const val LABEL_COLLISION_PADDING_PX = 2f

/**
 * Places SSID labels in priority order (callers pass the strongest network first).
 *
 * Each label tries the tier directly above its peak first and then stacks upward one label
 * height per tier. A label with no free tier within [maxTiers] is omitted instead of being
 * drawn over another label; the network list acts as the legend for omitted labels.
 */
internal fun placeSpectrumLabels(
    labels: List<SpectrumLabelBox>,
    plotBounds: ClosedFloatingPointRange<Float>,
    maxTiers: Int = 3,
): List<SpectrumLabelPlacement> {
    val placedRects = mutableListOf<LabelRect>()
    val placements = mutableListOf<SpectrumLabelPlacement>()
    labels.forEach { label ->
        val left = wifiSpectrumLabelLeft(label.centerX, label.width, plotBounds)
        val baseTop = label.peakY - LABEL_GAP_ABOVE_PEAK_PX - label.height
        val rect =
            (0 until maxTiers)
                .asSequence()
                .map { tier ->
                    val top = (baseTop - tier * (label.height + LABEL_COLLISION_PADDING_PX)).coerceAtLeast(0f)
                    LabelRect(left, top, left + label.width, top + label.height)
                }.firstOrNull { candidate -> placedRects.none { it.collides(candidate) } }
        if (rect != null) {
            placedRects += rect
            placements += SpectrumLabelPlacement(label.id, rect.left, rect.top)
        }
    }
    return placements
}

/**
 * One label per network (SSID and security, or the BSSID for hidden networks), using the
 * strongest access point of each network, ordered strongest first.
 */
internal fun spectrumLabelCandidates(accessPoints: List<WifiAccessPoint>): List<WifiAccessPoint> =
    accessPoints
        .sortedByDescending { it.rssi }
        .distinctBy { ap ->
            if (ap.ssid.isBlank()) "|hidden|${ap.bssid}" else "${ap.ssid}|${ap.security.name}"
        }

private data class LabelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun collides(other: LabelRect): Boolean =
        left < other.right + LABEL_COLLISION_PADDING_PX &&
            other.left < right + LABEL_COLLISION_PADDING_PX &&
            top < other.bottom + LABEL_COLLISION_PADDING_PX &&
            other.top < bottom + LABEL_COLLISION_PADDING_PX
}
