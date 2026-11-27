package net.aieat.netswissknife.app.ui.screens.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import kotlin.math.abs
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

/**
 * Values a Trace Configuration slider can land on: whole numbers, in [step] increments from the start,
 * or only [presets] when it has them. A slider with presets moves in equal steps from one preset to the
 * next, so each preset is as easy to reach as any other.
 */
internal data class SliderScale(
    val range: IntRange,
    val step: Int = 1,
    val presets: List<Int> = emptyList(),
) {
    /** The number of stops between the two ends, as Material's Slider counts them. */
    val steps: Int
        get() =
            if (presets.isEmpty()) {
                ((range.last - range.first) / step - 1).coerceAtLeast(0)
            } else {
                (presets.size - 2).coerceAtLeast(0)
            }

    /** The slider's range: the values themselves, or one position per preset. */
    val positions: ClosedFloatingPointRange<Float>
        get() = if (presets.isEmpty()) range.first.toFloat()..range.last.toFloat() else 0f..presets.lastIndex.toFloat()

    /** The stop nearest to [value]. */
    fun snap(value: Float): Int {
        val offset = ((value - range.first) / step).roundToInt() * step
        return (range.first + offset).coerceIn(range.first, range.last)
    }

    /** The value at slider [position]. */
    fun valueAt(position: Float): Int {
        if (presets.isEmpty()) return snap(position)
        return presets[position.roundToInt().coerceIn(0, presets.lastIndex)]
    }

    /** Where the thumb rests for [value]: at the nearest preset, the smaller one on a tie. */
    fun positionOf(value: Int): Float =
        if (presets.isEmpty()) {
            snap(value.toFloat()).toFloat()
        } else {
            presets.indices.minBy { abs(presets[it] - value) }.toFloat()
        }

    companion object {
        val MAX_HOPS = SliderScale(5..64)
        val TIMEOUT_MS = SliderScale(500..10_000, step = TracerouteViewModel.TIMEOUT_STEP_MS)
        val PROBES = SliderScale(1..5)

        /** From the IPv4 minimum to a full 1500-byte Ethernet frame, through the usual traceroute and MTU sizes. */
        val PACKET_SIZE = SliderScale(28..1472, presets = listOf(28, 56, 64, 128, 256, 512, 576, 1024, 1280, 1472))
    }
}
