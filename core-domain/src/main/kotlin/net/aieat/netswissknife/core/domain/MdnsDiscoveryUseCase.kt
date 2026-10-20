package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.mdns.MdnsOperation
import net.aieat.netswissknife.core.network.operation.OperationSession
import kotlinx.coroutines.flow.Flow

class MdnsDiscoveryUseCase(
    private val repository: MdnsRepository
) {
    operator fun invoke(timeoutMs: Long = 8_000L): Flow<MdnsUpdate> =
        repository.discover(MdnsOperation.requireValidScanDuration(timeoutMs))

    operator fun invoke(timeoutMs: Long, operationSession: OperationSession): Flow<MdnsUpdate> =
        repository.discover(MdnsOperation.requireValidScanDuration(timeoutMs), operationSession)
}
