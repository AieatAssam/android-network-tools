package net.aieat.netswissknife.app.ui.screens.wifi

import androidx.annotation.StringRes
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.wifi.SignalLevel

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
    if (cacheReadElapsedRealtimeMs <= 0L) return (baseMs / 1_000L).coerceAtLeast(0L)
    val sinceReadMs = (nowElapsedRealtimeMs - cacheReadElapsedRealtimeMs).coerceAtLeast(0L)
    return ((baseMs + sinceReadMs) / 1_000L).coerceAtLeast(0L)
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
