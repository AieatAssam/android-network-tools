package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.lan.LanHost
import com.example.netswissknife.core.network.lan.LanScanSummary

/** Events emitted by [LanScanUseCase] during a scan. */
sealed interface LanScanFlowResult {

    /**
     * A responsive host was discovered.
     *
     * @param host         The discovered host details.
     * @param scannedCount IPs checked so far (alive + dead).
     * @param totalCount   Total IPs in the subnet.
     */
    data class HostFound(
        val host: LanHost,
        val scannedCount: Int,
        val totalCount: Int,
    ) : LanScanFlowResult

    /**
     * Progress update emitted for IPs that did not respond (no host found).
     *
     * @param scannedCount IPs checked so far.
     * @param totalCount   Total IPs in the subnet.
     */
    data class ScanProgress(
        val scannedCount: Int,
        val totalCount: Int,
    ) : LanScanFlowResult

    /** Emitted once after all IPs have been probed (always the last event). */
    data class ScanComplete(val summary: LanScanSummary) : LanScanFlowResult

    /** Emitted as the only event when input validation fails. */
    data class ValidationError(val message: String) : LanScanFlowResult
}
