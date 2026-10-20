package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo

/** Events emitted by [PortScanUseCase] while a scan is in progress. */
sealed interface PortScanFlowResult {
    /** Target resolution completed; emitted before any port results. */
    data class Started(
        val resolvedIp: String,
        val totalCount: Int
    ) : PortScanFlowResult

    /** A single port result with scan progress. */
    data class PortScanned(
        val result: PortScanResult,
        val scannedCount: Int,
        val totalCount: Int
    ) : PortScanFlowResult

    /** The scan completed successfully. */
    data class ScanComplete(val summary: PortScanSummary) : PortScanFlowResult

    /** Input validation failed before the scan started. */
    data class ValidationError(val info: ErrorInfo) : PortScanFlowResult {
        val message: String get() = info.developerCopy()
        constructor(message: String) : this(ErrorInfo(ErrorCode.UNKNOWN, developerMessage = message))
    }
}

val PortScanFlowResult.isError: Boolean
    get() = this is PortScanFlowResult.ValidationError
