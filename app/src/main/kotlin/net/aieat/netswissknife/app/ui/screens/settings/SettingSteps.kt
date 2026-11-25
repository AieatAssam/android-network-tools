package net.aieat.netswissknife.app.ui.screens.settings

import net.aieat.netswissknife.app.ui.screens.PORT_CONCURRENCY_STEPS
import net.aieat.netswissknife.app.ui.screens.ping.PING_TIMEOUT_PRESETS_MS
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The values a Settings default slider steps through, smallest first. Every value is one the tools that
 * read the default accept, so a default can never be set beyond what a tool runs with.
 */
internal class SettingSteps(
    val values: List<Int>,
) {
    init {
        require(values.isNotEmpty() && values == values.sorted().distinct()) { "Steps must be ascending" }
    }

    val lastIndex: Int get() = values.lastIndex

    /** Stops between the two ends, as Material's Slider counts them. */
    val sliderSteps: Int get() = (values.size - 2).coerceAtLeast(0)

    val min: Int get() = values.first()
    val max: Int get() = values.last()

    /** Index of the step nearest [value]; a tie goes to the smaller step. */
    fun indexOf(value: Int): Int = values.indices.minBy { abs(values[it] - value) }

    /** The step nearest [value]. */
    fun nearest(value: Int): Int = values[indexOf(value)]

    /** The step at slider [position]. */
    fun valueAt(position: Float): Int = values[position.roundToInt().coerceIn(0, lastIndex)]

    /**
     * The value to show with the thumb at [position]: the [stored] value while the thumb rests on the step
     * nearest it, so a value saved off the steps is shown as it is until it is changed.
     */
    fun shownValue(
        stored: Int,
        position: Int,
    ): Int = if (position == indexOf(stored)) stored else values[position.coerceIn(0, lastIndex)]

    companion object {
        /** Ping runs 1 to 100 probes. */
        val PING_COUNT = SettingSteps(listOf(1, 3, 5, 10, 20, 50, 100))

        /** Ping's timeout chips; Port Scanner and LAN Scanner accept each of them too. */
        val TIMEOUT_MS = SettingSteps(PING_TIMEOUT_PRESETS_MS)

        /** Port Scanner's concurrency steps; LAN Scanner accepts the same 1 to 500 range. */
        val CONCURRENCY = SettingSteps(PORT_CONCURRENCY_STEPS)
    }
}
