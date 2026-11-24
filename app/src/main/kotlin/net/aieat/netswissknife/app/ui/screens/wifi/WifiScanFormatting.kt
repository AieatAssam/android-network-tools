package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.wifi.SignalLevel

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Age of the cached scan in whole seconds, advanced by the time elapsed since the cache was read.
 * Returns null when the platform did not report a timestamp for the newest result.
 */
internal fun wifiScanAgeSeconds(
    scanAgeMs: Long?,
    cacheReadElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
): Long? {
    val baseMs = scanAgeMs ?: return null
    val sinceReadMs =
        if (cacheReadElapsedRealtimeMs <= 0L) {
            0L
        } else {
            (nowElapsedRealtimeMs - cacheReadElapsedRealtimeMs).coerceAtLeast(0L)
        }
    return ((baseMs + sinceReadMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
}

@StringRes
internal fun signalLevelLabelRes(level: SignalLevel): Int =
    when (level) {
        SignalLevel.EXCELLENT -> R.string.wifi_signal_level_excellent
        SignalLevel.GOOD -> R.string.wifi_signal_level_good
        SignalLevel.FAIR -> R.string.wifi_signal_level_fair
        SignalLevel.WEAK -> R.string.wifi_signal_level_weak
        SignalLevel.POOR -> R.string.wifi_signal_level_poor
    }
