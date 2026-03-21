package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.HostValidator
import com.example.netswissknife.core.network.NetworkResult

/**
 * Use case for validating a host string before issuing network commands.
 */
class ValidateHostUseCase : UseCase<String, NetworkResult<String>> {
    override suspend fun invoke(params: String): NetworkResult<String> {
        return if (HostValidator.isValidHostname(params)) {
            NetworkResult.Success(params.trim())
        } else {
            NetworkResult.Error("Invalid host: '$params'")
        }
    }
}
