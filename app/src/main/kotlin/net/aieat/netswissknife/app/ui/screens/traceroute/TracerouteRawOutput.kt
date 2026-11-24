package net.aieat.netswissknife.app.ui.screens.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import kotlin.math.roundToInt

private const val HOP_NUMBER_WIDTH = 3

/**
 * Plain text in the style of the traceroute command: one line per hop, each probe's RTT or `*` for a
 * probe with no reply, and `* * *` for a hop that did not answer.
 */
internal fun tracerouteRawOutput(
    host: String,
    hops: List<HopResult>,
    maxHops: Int,
    probeType: TracerouteProbeType,
    packetSize: Int,
): String =
    buildString {
        val sizeLabel = if (packetSize == 0) "MTU discovery" else "$packetSize bytes"
        appendLine("traceroute to $host, $maxHops hops max, ${probeType.name}, $sizeLabel")
        hops.forEach { hop ->
            val number = "${hop.hopNumber}".padStart(HOP_NUMBER_WIDTH)
            if (hop.ip == null) {
                appendLine("$number  * * *")
                return@forEach
            }
            val name = hop.hostname?.let { " ($it)" }.orEmpty()
            val geo = hop.geoLocation?.let { " [${it.city.ifBlank { it.country }}]" }.orEmpty()
            appendLine("$number  ${hop.ip}$name ${probeText(hop)}$geo")
        }
    }

private fun probeText(hop: HopResult): String =
    if (hop.probeRttsMs.isEmpty()) {
        hop.rtTimeMs?.let { "$it ms" } ?: "*"
    } else {
        hop.probeRttsMs.joinToString(" ") { rtt -> rtt?.let { "$it ms" } ?: "*" }
    }

/** Values a Trace Configuration slider can land on: whole numbers, in [step] increments from the start. */
internal data class SliderScale(
    val range: IntRange,
    val step: Int = 1,
) {
    /** The number of stops between the two ends, as Material's Slider counts them. */
    val steps: Int get() = ((range.last - range.first) / step - 1).coerceAtLeast(0)

    /** The stop nearest to [value]. */
    fun snap(value: Float): Int {
        val offset = ((value - range.first) / step).roundToInt() * step
        return (range.first + offset).coerceIn(range.first, range.last)
    }

    companion object {
        val MAX_HOPS = SliderScale(5..64)
        val TIMEOUT_MS = SliderScale(500..10_000, step = TracerouteViewModel.TIMEOUT_STEP_MS)
        val PROBES = SliderScale(1..5)
        val PACKET_SIZE = SliderScale(28..1472)
    }
}
