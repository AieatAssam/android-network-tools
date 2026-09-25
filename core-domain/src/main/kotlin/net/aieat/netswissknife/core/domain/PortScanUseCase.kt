package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.portscan.PortScanRepository
import net.aieat.netswissknife.core.network.portscan.PortScanOperationBudget
import net.aieat.netswissknife.core.network.portscan.PortScanUpdate
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case that validates [PortScanParams] and delegates to [PortScanRepository].
 *
 * Validation rules:
 * - Host must not be blank and must be a valid hostname/IPv4
 * - For CUSTOM preset: startPort and endPort must be in 1–65535, startPort ≤ endPort
 * - Max custom range: 10 000 ports
 * - timeoutMs must be in 100–30 000
 * - concurrency must be in 1–500
 */
class PortScanUseCase(private val repository: PortScanRepository) {

    /** Creates one monotonic session sized for this scan's work and effective concurrency. */
    fun newSession(params: PortScanParams): OperationSession {
        val concurrency = params.concurrency.coerceIn(1, PortScanOperationBudget.MAX_CONCURRENCY)
        return OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.ANY_NETWORK,
                timeoutMillis = PortScanDeadlineBudget.sessionTimeoutMillis(params),
                maxConcurrentProbes = concurrency,
            ),
        )
    }

    operator fun invoke(params: PortScanParams): Flow<PortScanFlowResult> =
        execute(params, newSession(params))

    operator fun invoke(params: PortScanParams, operationSession: OperationSession): Flow<PortScanFlowResult> =
        execute(params, operationSession)

    private fun execute(params: PortScanParams, operationSession: OperationSession?): Flow<PortScanFlowResult> = flow {
        val host = HostValidator.normalize(params.host) ?: params.host.trim()

        // Validate host
        if (host.isBlank()) {
            emit(PortScanFlowResult.ValidationError("Host must not be blank"))
            return@flow
        }
        if (!HostValidator.isValidHostname(host)) {
            emit(PortScanFlowResult.ValidationError("Invalid hostname or IP address: \"$host\""))
            return@flow
        }

        // Validate timeout
        if (params.timeoutMs < 100 || params.timeoutMs > 30_000) {
            emit(PortScanFlowResult.ValidationError("Timeout must be between 100 ms and 30 000 ms"))
            return@flow
        }

        // Validate concurrency
        if (params.concurrency < 1 || params.concurrency > 500) {
            emit(PortScanFlowResult.ValidationError("Concurrency must be between 1 and 500"))
            return@flow
        }

        // Resolve ports to scan
        val portsToScan: List<Int> = if (params.preset == PortScanPreset.CUSTOM) {
            if (params.startPort < 1 || params.startPort > 65_535) {
                emit(PortScanFlowResult.ValidationError("Start port must be between 1 and 65 535"))
                return@flow
            }
            if (params.endPort < 1 || params.endPort > 65_535) {
                emit(PortScanFlowResult.ValidationError("End port must be between 1 and 65 535"))
                return@flow
            }
            if (params.startPort > params.endPort) {
                emit(PortScanFlowResult.ValidationError("Start port must be ≤ end port"))
                return@flow
            }
            val rangeSize = params.endPort - params.startPort + 1
            if (rangeSize > 10_000) {
                emit(PortScanFlowResult.ValidationError("Custom range must not exceed 10 000 ports (got $rangeSize)"))
                return@flow
            }
            (params.startPort..params.endPort).toList()
        } else {
            params.preset.ports
        }

        if (portsToScan.isEmpty()) {
            emit(PortScanFlowResult.ValidationError("No ports to scan in the selected preset"))
            return@flow
        }

        val estimate = PortScanDeadlineBudget.estimate(
            portCount = portsToScan.size,
            timeoutMs = params.timeoutMs,
            requestedConcurrency = params.concurrency,
            sessionConcurrency = operationSession?.budget?.maxConcurrentProbes ?: params.concurrency,
        )
        if (estimate.exceedsHardCeiling) {
            emit(
                PortScanFlowResult.ValidationError(
                    "This scan may exceed the 15-minute operation limit; increase concurrency or reduce the port range or timeout.",
                ),
            )
            return@flow
        }

        // Delegate to repository and map updates
        val updates = if (operationSession == null) repository.scan(
            host = host,
            ports = portsToScan,
            timeoutMs = params.timeoutMs,
            concurrency = params.concurrency
        ) else repository.scan(
            host = host,
            ports = portsToScan,
            timeoutMs = params.timeoutMs,
            concurrency = params.concurrency,
            aggressiveProbes = params.aggressiveProbes,
            operationSession = operationSession,
        )
        updates.collect { update ->
            when (update) {
                is PortScanUpdate.Started -> emit(
                    PortScanFlowResult.Started(
                        resolvedIp = update.resolvedIp,
                        totalCount = update.totalCount
                    )
                )
                is PortScanUpdate.PortResult -> emit(
                    PortScanFlowResult.PortScanned(
                        result = update.result,
                        scannedCount = update.scannedCount,
                        totalCount = update.totalCount
                    )
                )
                is PortScanUpdate.Complete -> emit(
                    PortScanFlowResult.ScanComplete(update.summary)
                )
            }
        }
    }
}
