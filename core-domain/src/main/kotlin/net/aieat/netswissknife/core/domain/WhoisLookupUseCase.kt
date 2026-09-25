package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.flow.SharedFlow
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisProtocol
import net.aieat.netswissknife.core.network.whois.WhoisQueryTypeDetector
import net.aieat.netswissknife.core.network.whois.WhoisRepository
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.operation.OperationSession

data class WhoisParams @JvmOverloads constructor(
    val query: String,
    val timeoutMs: Int = 10_000,
    val protocol: WhoisProtocol = WhoisProtocol.AUTO,
)

class WhoisLookupUseCase(private val repository: WhoisRepository) {

    /** Live hop events emitted by the repository as the chain progresses. */
    val hopProgress: SharedFlow<WhoisHop> get() = repository.hopProgress

    suspend operator fun invoke(params: WhoisParams): NetworkResult<WhoisResult> {
        return execute(params, null)
    }

    suspend operator fun invoke(
        params: WhoisParams,
        operationSession: OperationSession,
    ): NetworkResult<WhoisResult> = execute(params, operationSession)

    private suspend fun execute(
        params: WhoisParams,
        operationSession: OperationSession?,
    ): NetworkResult<WhoisResult> {
        val query = WhoisQueryTypeDetector.normalize(params.query)
            ?: return NetworkResult.Error("Enter a valid domain, IP address, or ASN without spaces")
        if (params.timeoutMs !in 500..30_000)
            return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        return when {
            operationSession == null && params.protocol == WhoisProtocol.AUTO ->
                repository.lookup(query.value, params.timeoutMs)
            operationSession == null -> repository.lookup(query.value, params.timeoutMs, params.protocol)
            params.protocol == WhoisProtocol.AUTO ->
                repository.lookup(query.value, params.timeoutMs, operationSession)
            else -> repository.lookup(query.value, params.timeoutMs, operationSession, params.protocol)
        }
    }
}
