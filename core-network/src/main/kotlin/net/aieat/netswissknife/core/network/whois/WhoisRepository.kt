package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.flow.SharedFlow
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.OperationSession

interface WhoisRepository {
    /** Emits each hop as it completes during an active [lookup] call. */
    val hopProgress: SharedFlow<WhoisHop>

    suspend fun lookup(query: String, timeoutMs: Int): NetworkResult<WhoisResult>

    /**
     * Caller-owned operation overload. The source-compatible default delegates to the legacy
     * method and cannot enforce the session; production repositories that own resources must
     * override this overload.
     */
    suspend fun lookup(query: String, timeoutMs: Int, operationSession: OperationSession): NetworkResult<WhoisResult> =
        lookup(query, timeoutMs)
}
