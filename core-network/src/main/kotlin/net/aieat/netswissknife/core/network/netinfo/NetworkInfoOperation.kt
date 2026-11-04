package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Bounded operation policy for one user-requested public IP lookup and its optional enrichment. */
object NetworkInfoOperation {
    const val TIMEOUT_MILLIS = 12_000L

    fun newSession(): OperationSession =
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.INTERNET,
                timeoutMillis = TIMEOUT_MILLIS,
            ),
        )
}
