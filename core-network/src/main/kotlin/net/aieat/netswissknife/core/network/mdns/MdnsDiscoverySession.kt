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

/** Resource ceilings applied before a packet can grow per-scan discovery state or follow-up work. */
data class MdnsDiscoveryLimits(
    val maxServiceTypes: Int = DEFAULT_MAX_SERVICE_TYPES,
    val maxInstances: Int = DEFAULT_MAX_INSTANCES,
    val maxAddressesPerService: Int = DEFAULT_MAX_ADDRESSES_PER_SERVICE,
    val maxAddressBytesPerService: Int = DEFAULT_MAX_ADDRESS_BYTES_PER_SERVICE,
    val maxTxtBytesPerService: Int = DEFAULT_MAX_TXT_BYTES_PER_SERVICE,
    val maxQueries: Int = DEFAULT_MAX_QUERIES,
    val maxRecordsPerPacket: Int = DEFAULT_MAX_RECORDS_PER_PACKET,
) {
    init {
        require(maxServiceTypes in 1..MAX_SERVICE_TYPES_LIMIT)
        require(maxInstances in 1..MAX_INSTANCES_LIMIT)
        require(maxAddressesPerService in 1..MAX_ADDRESSES_PER_SERVICE_LIMIT)
        require(maxAddressBytesPerService in 1..MAX_ADDRESS_BYTES_PER_SERVICE_LIMIT)
        require(maxTxtBytesPerService in 1..MAX_TXT_BYTES_PER_SERVICE_LIMIT)
        require(maxQueries in 1..MAX_QUERIES_LIMIT)
        require(maxRecordsPerPacket in 1..MAX_RECORDS_PER_PACKET_LIMIT)
    }

    companion object {
        const val DEFAULT_MAX_SERVICE_TYPES = 64
        const val DEFAULT_MAX_INSTANCES = 256
        const val DEFAULT_MAX_ADDRESSES_PER_SERVICE = 16
        const val DEFAULT_MAX_ADDRESS_BYTES_PER_SERVICE = 1_024
        const val DEFAULT_MAX_TXT_BYTES_PER_SERVICE = 4_096
        const val DEFAULT_MAX_QUERIES = 1_024
        const val DEFAULT_MAX_RECORDS_PER_PACKET = 512
        const val MAX_SERVICE_TYPES_LIMIT = 256
        const val MAX_INSTANCES_LIMIT = 4_096
        const val MAX_ADDRESSES_PER_SERVICE_LIMIT = 64
        const val MAX_ADDRESS_BYTES_PER_SERVICE_LIMIT = 4_096
        const val MAX_TXT_BYTES_PER_SERVICE_LIMIT = 16_384
        const val MAX_QUERIES_LIMIT = 4_096
        const val MAX_RECORDS_PER_PACKET_LIMIT = 4_096
    }
}

enum class MdnsTruncationReason {
    SERVICE_TYPE_LIMIT,
    INSTANCE_LIMIT,
    ADDRESS_LIMIT,
    TXT_BYTES_LIMIT,
    QUERY_LIMIT,
    PACKET_RECORD_LIMIT,
}

/** Holds bounded evolving service state for one scan, separate from socket and Android lifecycle code. */
class MdnsDiscoverySession(
    private val limits: MdnsDiscoveryLimits = MdnsDiscoveryLimits(),
) {
    private val discoveredTypes = linkedSetOf<String>()
    private val partialServices = linkedMapOf<String, PartialService>()
    private val emittedServices = linkedMapOf<String, DiscoveredService>()
    private val scheduledQueries = linkedSetOf<MdnsSessionQuery>()
    private val truncations = linkedSetOf<MdnsTruncationReason>()

    val serviceTypes: Set<String> get() = discoveredTypes.toSet()
    val instanceCount: Int get() = partialServices.size
    val totalFound: Int get() = emittedServices.size
    val scheduledQueryCount: Int get() = scheduledQueries.size
    val truncationReasons: Set<MdnsTruncationReason> get() = truncations.toSet()
    val retainedAddressCount: Int get() = partialServices.values.sumOf { it.ipAddresses.size }
    val retainedAddressBytes: Int get() = partialServices.values.sumOf { it.addressBytes }
    val retainedTxtBytes: Int get() = partialServices.values.sumOf { it.txtBytes }

    /** Processes all records from one packet and returns one latest snapshot per changed service. */
    fun process(records: Iterable<MdnsSessionRecord>): MdnsSessionResult {
        val queries = mutableListOf<MdnsSessionQuery>()

        for (record in records) {
            when (record) {
                is MdnsSessionRecord.Ptr -> {
                    if (record.owner.contains("_services._dns-sd")) {
                        val type = MdnsPacketParser.extractServiceType(record.target)
                        if (type.isNotEmpty() && type !in discoveredTypes) {
                            if (discoveredTypes.size >= limits.maxServiceTypes) {
                                truncations += MdnsTruncationReason.SERVICE_TYPE_LIMIT
                            } else {
                                discoveredTypes += type
                                schedule(MdnsSessionQuery(record.target, MdnsQueryType.PTR), queries)
                            }
                        }
                    } else if (record.target !in partialServices) {
                        if (partialServices.size >= limits.maxInstances) {
                            truncations += MdnsTruncationReason.INSTANCE_LIMIT
                        } else {
                            val service = PartialService(
                                instanceName = record.target,
                                displayName = MdnsPacketParser.extractDisplayName(record.target),
                                serviceType = MdnsPacketParser.extractServiceType(record.target),
                            )
                            partialServices[record.target] = service
                            schedule(MdnsSessionQuery(record.target, MdnsQueryType.SRV), queries)
                            schedule(MdnsSessionQuery(record.target, MdnsQueryType.TXT), queries)
                        }
                    }
                }

                is MdnsSessionRecord.Srv -> {
                    partialServices[record.owner]?.let { service ->
                        val hostname = MdnsPacketParser.normalizeHostname(record.hostname)
                        service.hostname = hostname
                        service.port = record.port
                        schedule(MdnsSessionQuery(record.hostname, MdnsQueryType.A), queries)
                        schedule(MdnsSessionQuery(record.hostname, MdnsQueryType.AAAA), queries)
                    }
                }

                is MdnsSessionRecord.Txt -> {
                    partialServices[record.owner]?.let { service ->
                        val bounded = linkedMapOf<String, String>()
                        val entryBytesByKey = linkedMapOf<String, Int>()
                        var retainedBytes = 0
                        for (entry in record.entries) {
                            val eqIdx = entry.indexOf('=')
                            val key = if (eqIdx >= 0) entry.substring(0, eqIdx) else entry
                            val value = if (eqIdx >= 0) entry.substring(eqIdx + 1) else ""
                            val bytes = entry.toByteArray(Charsets.UTF_8).size
                            val previousBytes = entryBytesByKey[key] ?: 0
                            val updatedBytes = retainedBytes - previousBytes + bytes
                            if (updatedBytes > limits.maxTxtBytesPerService) {
                                truncations += MdnsTruncationReason.TXT_BYTES_LIMIT
                                continue
                            }
                            bounded[key] = value
                            entryBytesByKey[key] = bytes
                            retainedBytes = updatedBytes
                        }
                        service.txtRecords = bounded
                        service.txtBytes = retainedBytes
                    }
                }

                is MdnsSessionRecord.Address -> {
                    val hostname = MdnsPacketParser.normalizeHostname(record.hostname)
                    partialServices.values
                        .filter { it.hostname == hostname }
                        .forEach { service ->
                            if (record.address !in service.ipAddresses) {
                                val addressBytes = record.address.toByteArray(Charsets.UTF_8).size
                                if (service.ipAddresses.size >= limits.maxAddressesPerService ||
                                    service.addressBytes + addressBytes > limits.maxAddressBytesPerService
                                ) {
                                    truncations += MdnsTruncationReason.ADDRESS_LIMIT
                                } else {
                                    service.ipAddresses += record.address
                                    service.addressBytes += addressBytes
                                }
                            }
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

    /** Mark an outbound-send ceiling reached by the socket owner. */
    fun markQueryLimitReached() {
        truncations += MdnsTruncationReason.QUERY_LIMIT
    }

    fun markPacketRecordLimitReached() {
        truncations += MdnsTruncationReason.PACKET_RECORD_LIMIT
    }

    private fun schedule(query: MdnsSessionQuery, packetQueries: MutableList<MdnsSessionQuery>) {
        if (query in scheduledQueries) return
        if (scheduledQueries.size >= limits.maxQueries) {
            truncations += MdnsTruncationReason.QUERY_LIMIT
            return
        }
        scheduledQueries += query
        packetQueries += query
    }

    private class PartialService(
        val instanceName: String,
        val displayName: String,
        val serviceType: String,
        var hostname: String? = null,
        var port: Int = 0,
        val ipAddresses: MutableList<String> = mutableListOf(),
        var addressBytes: Int = 0,
        var txtRecords: Map<String, String> = emptyMap(),
        var txtBytes: Int = 0,
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
