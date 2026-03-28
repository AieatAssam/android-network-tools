package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.portscan.PortScanResult
import net.aieat.netswissknife.core.network.portscan.PortScanSummary

/** Events emitted by [PortScanUseCase] while a scan is in progress. */
sealed interface PortScanFlowResult {
    /** A single port result with scan progress. */
    data class PortScanned(
        val result: PortScanResult,
        val scannedCount: Int,
        val totalCount: Int
    ) : PortScanFlowResult

    /** The scan completed successfully. */
    data class ScanComplete(val summary: PortScanSummary) : PortScanFlowResult

    /** Input validation failed before the scan started. */
    data class ValidationError(val message: String) : PortScanFlowResult
}

val PortScanFlowResult.isError: Boolean
    get() = this is PortScanFlowResult.ValidationError
