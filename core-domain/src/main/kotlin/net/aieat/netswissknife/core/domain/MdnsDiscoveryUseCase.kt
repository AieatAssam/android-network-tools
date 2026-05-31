package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import kotlinx.coroutines.flow.Flow

class MdnsDiscoveryUseCase(
    private val repository: MdnsRepository
) {
    operator fun invoke(timeoutMs: Long = 5_000L): Flow<MdnsUpdate> =
        repository.discover(timeoutMs)
}
