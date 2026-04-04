package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.SharedFlow
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisRepository
import net.aieat.netswissknife.core.network.whois.WhoisResult

data class WhoisParams(
    val query: String,
    val timeoutMs: Int = 10_000
)

class WhoisLookupUseCase(private val repository: WhoisRepository) {

    /** Live hop events emitted by the repository as the chain progresses. */
    val hopProgress: SharedFlow<WhoisHop> get() = repository.hopProgress

    suspend operator fun invoke(params: WhoisParams): NetworkResult<WhoisResult> {
        val query = params.query.trim()
        if (query.isBlank()) return NetworkResult.Error("Query must not be blank")
        if (params.timeoutMs !in 500..30_000)
            return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        return repository.lookup(query, params.timeoutMs)
    }
}
