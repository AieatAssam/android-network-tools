package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.NetworkResult
import com.example.netswissknife.core.network.dns.DnsRepository
import com.example.netswissknife.core.network.dns.DnsResult

/**
 * Use case that validates the user's input and delegates to [DnsRepository]
 * for the actual DNS query.
 *
 * Validation rules:
 * - Domain must not be blank
 * - Domain length must not exceed 253 characters
 * - For [DnsServer.Custom][com.example.netswissknife.core.network.dns.DnsServer.Custom],
 *   the custom server address must not be blank
 */
class DnsLookupUseCase(
    private val repository: DnsRepository
) : UseCase<DnsLookupParams, NetworkResult<DnsResult>> {

    override suspend fun invoke(params: DnsLookupParams): NetworkResult<DnsResult> {
        val trimmedDomain = params.domain.trim()

        if (trimmedDomain.isBlank()) {
            return NetworkResult.Error("Domain name must not be empty")
        }
        if (trimmedDomain.length > 253) {
            return NetworkResult.Error("Domain name is too long (max 253 characters)")
        }

        val customServer = params.server as? com.example.netswissknife.core.network.dns.DnsServer.Custom
        if (customServer != null && customServer.address.isBlank()) {
            return NetworkResult.Error("Custom DNS server address must not be empty")
        }

        return repository.lookup(
            domain = trimmedDomain,
            recordType = params.recordType,
            server = params.server
        )
    }
}
