package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.dns.DnsRepository
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import net.aieat.netswissknife.core.network.operation.OperationSession

/**
 * Use case that validates the user's input and delegates to [DnsRepository]
 * for the actual DNS query.
 *
 * Validation rules:
 * - Domain must not be blank
 * - Domain length must not exceed 253 characters
 * - For [DnsServer.Custom][net.aieat.netswissknife.core.network.dns.DnsServer.Custom],
 *   the trimmed custom server address must be an IPv4 or IPv6 literal
 */
class DnsLookupUseCase(
    private val repository: DnsRepository
) : UseCase<DnsLookupParams, NetworkResult<DnsResult>> {

    override suspend fun invoke(params: DnsLookupParams): NetworkResult<DnsResult> {
        return invokeValidated(params, null)
    }

    suspend operator fun invoke(
        params: DnsLookupParams,
        operationSession: OperationSession,
    ): NetworkResult<DnsResult> = invokeValidated(params, operationSession)

    private suspend fun invokeValidated(
        params: DnsLookupParams,
        operationSession: OperationSession?,
    ): NetworkResult<DnsResult> {
        val trimmedDomain = params.domain.trim()

        if (trimmedDomain.isBlank()) {
            return NetworkResult.error(ErrorCode.QUERY_BLANK, "Domain name must not be empty")
        }
        if (trimmedDomain.length > 253) {
            return NetworkResult.error(ErrorCode.DOMAIN_TOO_LONG, "Domain name is too long (max 253 characters)", args = listOf(253))
        }

        val server = when (val requestedServer = params.server) {
            is DnsServer.Custom -> {
                val address = requestedServer.address.trim()
                if (!HostValidator.isValidIpv4(address) && !HostValidator.isValidIpv6(address)) {
                    return NetworkResult.error(ErrorCode.CUSTOM_DNS_INVALID, "Custom DNS server must be an IPv4 or IPv6 address")
                }
                DnsServer.Custom(address)
            }
            else -> requestedServer
        }

        return if (operationSession == null) {
            repository.lookup(
                domain = trimmedDomain,
                recordType = params.recordType,
                server = server
            )
        } else {
            repository.lookup(
                domain = trimmedDomain,
                recordType = params.recordType,
                server = server,
                operationSession = operationSession,
            )
        }
    }
}
