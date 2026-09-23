package net.aieat.netswissknife.core.network.mdns

/** DNS records normalized for the platform-independent mDNS discovery state machine. */
sealed interface MdnsSessionRecord {
    data class Ptr(val owner: String, val target: String) : MdnsSessionRecord
    data class Srv(val owner: String, val hostname: String, val port: Int) : MdnsSessionRecord
    data class Txt(val owner: String, val entries: List<String>) : MdnsSessionRecord
    data class Address(val hostname: String, val address: String) : MdnsSessionRecord
}

enum class MdnsQueryType { PTR, SRV, TXT, A, AAAA }

data class MdnsSessionQuery(val name: String, val type: MdnsQueryType)

data class MdnsSessionResult(
    val queries: List<MdnsSessionQuery>,
    val services: List<DiscoveredService>,
)

/** Holds the evolving service state for one scan, separate from socket and Android lifecycle code. */
class MdnsDiscoverySession {
    private val discoveredTypes = linkedSetOf<String>()
    private val partialServices = linkedMapOf<String, PartialService>()
    private val emittedServices = linkedMapOf<String, DiscoveredService>()

    val serviceTypes: Set<String> get() = discoveredTypes.toSet()
    val totalFound: Int get() = emittedServices.size

    /** Processes all records from one packet and returns one latest snapshot per changed service. */
    fun process(records: Iterable<MdnsSessionRecord>): MdnsSessionResult {
        val queries = mutableListOf<MdnsSessionQuery>()

        for (record in records) {
            when (record) {
                is MdnsSessionRecord.Ptr -> {
                    if (record.owner.contains("_services._dns-sd")) {
                        val type = MdnsPacketParser.extractServiceType(record.target)
                        if (type.isNotEmpty() && discoveredTypes.add(type)) {
                            queries += MdnsSessionQuery(record.target, MdnsQueryType.PTR)
                        }
                    } else if (record.target !in partialServices) {
                        val service = PartialService(
                            instanceName = record.target,
                            displayName = MdnsPacketParser.extractDisplayName(record.target),
                            serviceType = MdnsPacketParser.extractServiceType(record.target),
                        )
                        partialServices[record.target] = service
                        queries += MdnsSessionQuery(record.target, MdnsQueryType.SRV)
                        queries += MdnsSessionQuery(record.target, MdnsQueryType.TXT)
                    }
                }

                is MdnsSessionRecord.Srv -> {
                    partialServices[record.owner]?.let { service ->
                        service.hostname = MdnsPacketParser.normalizeHostname(record.hostname)
                        service.port = record.port
                        queries += MdnsSessionQuery(record.hostname, MdnsQueryType.A)
                        queries += MdnsSessionQuery(record.hostname, MdnsQueryType.AAAA)
                    }
                }

                is MdnsSessionRecord.Txt -> {
                    partialServices[record.owner]?.txtRecords = MdnsPacketParser.parseTxtPairs(record.entries)
                }

                is MdnsSessionRecord.Address -> {
                    val hostname = MdnsPacketParser.normalizeHostname(record.hostname)
                    partialServices.values
                        .filter { it.hostname == hostname }
                        .forEach { service ->
                            if (record.address !in service.ipAddresses) service.ipAddresses += record.address
                        }
                }
            }
        }

        val changedServices = partialServices.values.mapNotNull { partial ->
            val snapshot = partial.toService()
            if (!partial.isReady || emittedServices[partial.instanceName] == snapshot) return@mapNotNull null
            emittedServices[partial.instanceName] = snapshot
            snapshot
        }
        return MdnsSessionResult(queries, changedServices)
    }

    /** Emits any remaining hostname-bearing partials once when the scan ends. */
    fun finish(): List<DiscoveredService> = partialServices.values.mapNotNull { partial ->
        if (partial.hostname == null) return@mapNotNull null
        val snapshot = partial.toService()
        if (emittedServices[partial.instanceName] == snapshot) return@mapNotNull null
        emittedServices[partial.instanceName] = snapshot
        snapshot
    }

    private class PartialService(
        val instanceName: String,
        val displayName: String,
        val serviceType: String,
        var hostname: String? = null,
        var port: Int = 0,
        val ipAddresses: MutableList<String> = mutableListOf(),
        var txtRecords: Map<String, String> = emptyMap(),
    ) {
        val isReady: Boolean get() = hostname != null && port != 0

        fun toService() = DiscoveredService(
            serviceType = serviceType,
            instanceName = instanceName,
            displayName = displayName,
            hostname = hostname.orEmpty(),
            port = port,
            ipAddresses = ipAddresses.toList(),
            txtRecords = txtRecords,
        )
    }
}
