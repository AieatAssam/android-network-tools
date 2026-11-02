package net.aieat.netswissknife.app.ui.screens.ping

/** The Interval slider runs from 100 ms to 10 s in 100 ms steps (V5-06). */
internal const val PING_INTERVAL_MIN_MS = 100
internal const val PING_INTERVAL_MAX_MS = 10_000
internal const val PING_INTERVAL_STEP_MS = 100

/** Snaps a slider value to the nearest step inside the range, so stored values are round. */
internal fun snapPingIntervalMs(raw: Int): Int {
    val rounded = (raw + PING_INTERVAL_STEP_MS / 2) / PING_INTERVAL_STEP_MS * PING_INTERVAL_STEP_MS
    return rounded.coerceIn(PING_INTERVAL_MIN_MS, PING_INTERVAL_MAX_MS)
}
