package net.aieat.netswissknife.core.network.whois

import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession

/** Creates a single-use owner for a WHOIS referral chain. */
object WhoisOperation {
    fun newSession(
        timeoutMs: Int = 10_000,
        clock: MonotonicClock = SystemMonotonicClock,
    ): OperationSession {
        require(timeoutMs in 500..30_000) { "Timeout must be between 500 ms and 30 000 ms" }
        return OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.INTERNET,
                timeoutMillis = timeoutMs * 3L,
                maxConcurrentProbes = 1,
                maxResponseBytes = 1_048_576L,
                clock = clock,
            )
        )
    }
}
