package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.lan.LanScanRepository
import net.aieat.netswissknife.core.network.lan.LanScanRequest
import net.aieat.netswissknife.core.network.lan.LanScanOperationBudget
import net.aieat.netswissknife.core.network.lan.LanScanUpdate
import net.aieat.netswissknife.core.network.lan.SubnetUtils
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
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
        return execute(params, operationSession = null)
    }

    /** Uses the caller's session so UI cancellation owns the entire LAN scan operation. */
    operator fun invoke(
        params: LanScanParams,
        operationSession: OperationSession,
    ): Flow<LanScanFlowResult> = execute(params, operationSession)

    private fun execute(
        params: LanScanParams,
        operationSession: OperationSession?,
    ): Flow<LanScanFlowResult> {
        val subnet = params.subnet.trim()

        // ── Validation ──────────────────────────────────────────────────────

        if (subnet.isBlank()) {
            return errorFlow(validationError(ErrorCode.SUBNET_BLANK, "Subnet must not be blank"))
        }
        if (!SubnetUtils.isValidCidr(subnet)) {
            return errorFlow(validationError(
                ErrorCode.SUBNET_INVALID,
                "Invalid subnet. Expected IPv4 CIDR with prefix /16–/30 (e.g. 192.168.1.0/24)",
            ))
        }
        if (params.timeoutMs !in 100..10_000) {
            return errorFlow(validationError(ErrorCode.TIMEOUT_OUT_OF_RANGE, "Timeout must be between 100 ms and 10 000 ms", 100, 10_000))
        }
        if (params.concurrency !in 1..500) {
            return errorFlow(validationError(ErrorCode.CONCURRENCY_OUT_OF_RANGE, "Concurrency must be between 1 and 500", 1, 500))
        }

        // ── Delegate to repository and map results ──────────────────────────

        val gatewayIp = params.gatewayIp
            ?.trim()
            ?.takeIf { SubnetUtils.contains(subnet, it) }

        val request = LanScanRequest(
                subnet = subnet,
                timeoutMs = params.timeoutMs,
                concurrency = params.concurrency,
                gatewayIp = gatewayIp,
                enableNameProbes = params.enableNameProbes,
            )
        if (LanScanOperationBudget.estimate(
                request,
                operationSession?.budget?.maxConcurrentProbes ?: request.concurrency,
            ).exceedsHardCeiling
        ) {
            return errorFlow(validationError(ErrorCode.OPERATION_DEADLINE_EXCEEDED, LanScanOperationBudget.OVER_CEILING_MESSAGE))
        }
        val updates = if (operationSession == null) {
            repository.scan(request)
        } else {
            repository.scan(request, operationSession)
        }

        return updates
            .map { update ->
                when (update) {
                    is LanScanUpdate.HostFound ->
                        LanScanFlowResult.HostFound(
                            update.host,
                            update.scannedCount,
                            update.totalCount,
                            update.uncertainCount,
                        )

                    is LanScanUpdate.ScanProgress ->
                        LanScanFlowResult.ScanProgress(
                            update.scannedCount,
                            update.totalCount,
                            update.uncertainCount,
                            update.diagnostic,
                        )

                    is LanScanUpdate.ScanComplete ->
                        LanScanFlowResult.ScanComplete(update.summary)
                }
            }
    }

    private fun errorFlow(info: ErrorInfo): Flow<LanScanFlowResult> = flow {
        emit(LanScanFlowResult.ValidationError(info))
    }
}
