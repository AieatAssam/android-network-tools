package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

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
        val uncertainCount: Int = 0,
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
        val uncertainCount: Int = 0,
        val diagnostic: LanScanDiagnostic? = null,
    ) : LanScanFlowResult

    /** Emitted once after all IPs have been probed (always the last event). */
    data class ScanComplete(val summary: LanScanSummary) : LanScanFlowResult

    /** Emitted as the only event when input validation fails. */
    data class ValidationError(val info: ErrorInfo) : LanScanFlowResult {
        val message: String get() = info.developerCopy()
        constructor(message: String) : this(ErrorInfo(ErrorCode.UNKNOWN, developerMessage = message))
    }
}
