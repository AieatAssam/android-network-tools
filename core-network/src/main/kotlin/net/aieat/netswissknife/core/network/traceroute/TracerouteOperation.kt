package net.aieat.netswissknife.core.network.traceroute

import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Shared bounded policy for a traceroute and all per-hop enrichment it performs. */
object TracerouteOperation {
    const val TIMEOUT_MILLIS = 120_000L
    const val MAX_CONCURRENT_PROBES = 5

    fun newSession(): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.ANY_NETWORK,
            timeoutMillis = TIMEOUT_MILLIS,
            maxConcurrentProbes = MAX_CONCURRENT_PROBES,
        ),
    )
}
