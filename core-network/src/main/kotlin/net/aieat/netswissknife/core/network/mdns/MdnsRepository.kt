package net.aieat.netswissknife.core.network.mdns

import kotlinx.coroutines.flow.Flow

data class DiscoveredService(
    val serviceType: String,
    val instanceName: String,
    val displayName: String,
    val hostname: String,
    val port: Int,
    val ipAddresses: List<String> = emptyList(),
    val txtRecords: Map<String, String> = emptyMap()
)

sealed class MdnsUpdate {
    data class ServiceFound(val service: DiscoveredService) : MdnsUpdate()
    data class DiscoveryComplete(val totalFound: Int) : MdnsUpdate()
}

interface MdnsRepository {
    fun discover(timeoutMs: Long = 5_000L): Flow<MdnsUpdate>
}
