package net.aieat.netswissknife.core.domain

/**
 * Rules for the Custom range preset. The use case, the ViewModel and the port form all read
 * [MAX_PORTS] from here so the cap is stated once.
 */
object PortScanRange {
    const val MIN_PORT = 1
    const val MAX_PORT = 65_535

    /** Largest custom range that may be scanned in one run. */
    const val MAX_PORTS = 10_000

    enum class Problem { PORT_OUT_OF_RANGE, INVERTED, TOO_LARGE }

    /** Returns the first problem with a custom range, or null when it can be scanned. */
    fun problemOf(
        start: Int,
        end: Int,
    ): Problem? =
        when {
            start !in MIN_PORT..MAX_PORT || end !in MIN_PORT..MAX_PORT -> Problem.PORT_OUT_OF_RANGE
            start > end -> Problem.INVERTED
            end - start + 1 > MAX_PORTS -> Problem.TOO_LARGE
            else -> null
        }

    /**
     * Applies a range-slider change so the span stays within [MAX_PORTS]. Only the thumb that
     * moved is limited, so dragging one end cannot push the other end away. Returns the
     * (start, end) pair to store.
     */
    fun clampSliderChange(
        previousStart: Int,
        newStart: Int,
        newEnd: Int,
    ): Pair<Int, Int> {
        val maxSpanEnd = MAX_PORTS - 1
        return if (newStart != previousStart) {
            maxOf(newStart, newEnd - maxSpanEnd) to newEnd
        } else {
            newStart to minOf(newEnd, newStart + maxSpanEnd)
        }
    }
}
