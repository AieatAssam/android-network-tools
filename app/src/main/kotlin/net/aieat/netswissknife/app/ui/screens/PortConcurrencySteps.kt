package net.aieat.netswissknife.app.ui.screens

import kotlin.math.abs

/**
 * Concurrency values the slider steps through.
 * A stored value between two steps is shown at the nearest step.
 */
internal val PORT_CONCURRENCY_STEPS = listOf(1, 2, 5, 10, 25, 50, 100, 250, 500)

internal fun concurrencyIndexOf(value: Int): Int {
    val distances = PORT_CONCURRENCY_STEPS.map { abs(it - value) }
    return distances.indices.minBy { distances[it] }
}
