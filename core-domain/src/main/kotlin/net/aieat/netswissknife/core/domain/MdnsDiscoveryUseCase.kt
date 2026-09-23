package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.Flow

class MdnsDiscoveryUseCase(
    private val repository: MdnsRepository
) {
    operator fun invoke(timeoutMs: Long = 5_000L): Flow<MdnsUpdate> =
        repository.discover(timeoutMs)

    operator fun invoke(timeoutMs: Long, operationSession: OperationSession): Flow<MdnsUpdate> =
        repository.discover(timeoutMs, operationSession)
}
