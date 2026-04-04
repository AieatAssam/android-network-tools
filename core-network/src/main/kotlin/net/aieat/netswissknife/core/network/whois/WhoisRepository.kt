package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.flow.SharedFlow
import net.aieat.netswissknife.core.network.NetworkResult

interface WhoisRepository {
    /** Emits each hop as it completes during an active [lookup] call. */
    val hopProgress: SharedFlow<WhoisHop>

    suspend fun lookup(query: String, timeoutMs: Int): NetworkResult<WhoisResult>
}
