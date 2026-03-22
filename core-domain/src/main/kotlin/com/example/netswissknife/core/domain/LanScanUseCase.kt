package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.lan.LanScanRepository
import com.example.netswissknife.core.network.lan.LanScanUpdate
import com.example.netswissknife.core.network.lan.SubnetUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Validates [LanScanParams] and delegates scanning to [LanScanRepository].
 *
 * Emits a single [LanScanFlowResult.ValidationError] on bad input (without
 * touching the repository) or maps repository events to [LanScanFlowResult]
 * variants otherwise.
 */
class LanScanUseCase(private val repository: LanScanRepository) {

    operator fun invoke(params: LanScanParams): Flow<LanScanFlowResult> {
        val subnet = params.subnet.trim()

        // ── Validation ──────────────────────────────────────────────────────

        if (subnet.isBlank()) {
            return errorFlow("Subnet must not be blank")
        }
        if (!SubnetUtils.isValidCidr(subnet)) {
            return errorFlow(
                "Invalid subnet. Expected IPv4 CIDR with prefix /16–/30 (e.g. 192.168.1.0/24)"
            )
        }
        if (params.timeoutMs !in 100..10_000) {
            return errorFlow("Timeout must be between 100 ms and 10 000 ms")
        }
        if (params.concurrency !in 1..500) {
            return errorFlow("Concurrency must be between 1 and 500")
        }

        // ── Delegate to repository and map results ──────────────────────────

        return repository.scan(subnet, params.timeoutMs, params.concurrency)
            .map { update ->
                when (update) {
                    is LanScanUpdate.HostFound ->
                        LanScanFlowResult.HostFound(update.host, update.scannedCount, update.totalCount)

                    is LanScanUpdate.ScanProgress ->
                        LanScanFlowResult.ScanProgress(update.scannedCount, update.totalCount)

                    is LanScanUpdate.ScanComplete ->
                        LanScanFlowResult.ScanComplete(update.summary)
                }
            }
    }

    private fun errorFlow(message: String): Flow<LanScanFlowResult> = flow {
        emit(LanScanFlowResult.ValidationError(message))
    }
}
