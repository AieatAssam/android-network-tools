package net.aieat.netswissknife.app.wifi

import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus

/** Maps cache age to wall time without claiming that a cache read performed a scan. */
internal fun estimateScanSampleTimeMs(
    nowWallClockMs: Long,
    scanAgeMs: Long?,
    refreshStatus: WifiScanRefreshStatus
): Long = scanAgeMs?.let { (nowWallClockMs - it).coerceAtLeast(0L) }
    ?: if (refreshStatus == WifiScanRefreshStatus.UPDATED) nowWallClockMs else 0L
