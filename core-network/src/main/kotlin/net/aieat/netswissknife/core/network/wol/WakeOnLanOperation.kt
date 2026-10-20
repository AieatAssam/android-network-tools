package net.aieat.netswissknife.core.network.wol

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Bounded operation policy for one user-triggered Wake-on-LAN send. */
object WakeOnLanOperation {
    const val TIMEOUT_MILLIS = 10_000L

    fun newSession(): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.LOCAL_NETWORK,
            timeoutMillis = TIMEOUT_MILLIS,
        ),
    )
}
