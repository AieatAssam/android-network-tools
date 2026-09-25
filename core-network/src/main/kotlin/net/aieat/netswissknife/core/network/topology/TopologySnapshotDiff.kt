package net.aieat.netswissknife.core.network.topology

/**
 * A credential-free, immutable view of one topology observation. This type deliberately only
 * retains discovered facts; SNMP parameters and secrets never enter the snapshot model.
 */
data class TopologySnapshot(
    val seedIp: String,
    val queriedAt: Long,
    val nodes: List<TopologyNodeSnapshot>,
    val links: List<TopologyLinkSnapshot>,
    val truncationReasons: Set<TopologyTruncationReason>,
    val hadSnmpErrors: Boolean,
    /** IPs whose duplicate records disagree and therefore cannot safely identify one device. */
    val conflictingNodeIps: Set<String>,
    /** Null means the producer did not retain enough scope to make a safe comparison. */
    val scanContext: TopologyScanContext? = null
) {
    companion object {
        fun from(graph: TopologyGraph): TopologySnapshot {
            val byIp = graph.nodes.groupBy { it.ip.snapshotIdentity() }
            val conflicts = byIp.filter { (ip, records) ->
                records.map { it.toSnapshot(ip) }.distinct().size > 1
            }.keys
            val nodes = byIp.mapNotNull { (ip, records) ->
                if (ip in conflicts) null else records.firstOrNull()?.toSnapshot(ip)
            }.sortedBy { it.ip }
            val links = graph.links.map(TopologyLinkSnapshot::from)
                .sortedWith(TopologyLinkSnapshot.ORDER)
            return TopologySnapshot(
                seedIp = graph.seedIp.snapshotIdentity(),
                queriedAt = graph.queriedAt,
                nodes = nodes,
                links = links,
                truncationReasons = graph.truncationReasons.toSet(),
                hadSnmpErrors = graph.hadSnmpErrors,
                conflictingNodeIps = conflicts.toSortedSet(),
                scanContext = graph.scanContext
            )
        }

    }
}

/** Safe material scope for deciding whether two topology observations can be compared. */
data class TopologyScanContext(
    val snmpVersion: SnmpVersion,
    val seedIp: String,
    val targetIp: String,
    val maxHops: Int,
    val timeoutMs: Int,
    val retries: Int,
    val v3AuthProtocol: V3AuthProtocol,
    val v3PrivProtocol: V3PrivProtocol,
    /**
     * Opaque caller-provided profile token. It can correlate scans, so it must not identify a
     * person or contain/derive from credentials. Raw credentials and their hashes are never kept.
     */
    val credentialScopeId: String?
) {
    companion object {
        fun from(seedIp: String, params: TopologyParams) = TopologyScanContext(
            snmpVersion = params.snmpVersion,
            seedIp = seedIp.snapshotIdentity(),
            targetIp = params.targetIp.snapshotIdentity(),
            maxHops = params.maxHops,
            timeoutMs = params.timeoutMs,
            retries = params.retries,
            v3AuthProtocol = params.v3AuthProtocol,
            v3PrivProtocol = params.v3PrivProtocol,
            credentialScopeId = params.credentialScopeId
        )
    }
}

data class TopologyNodeSnapshot(
    val ip: String,
    val sysName: String?,
    val sysDescr: String?,
    val vendor: String?,
    val model: String?,
    val firmwareVersion: String?,
    val sysLocation: String?,
    val uptimeHuman: String?,
    val capabilities: Set<DeviceCapability>,
    val interfaces: List<SnmpInterface>,
    val vlans: List<VlanInfo>,
    val snmpReachable: Boolean,
    val tableObservations: Map<TopologyDataTable, TopologyTableObservation>
)

data class TopologyLinkSnapshot(
    val fromIp: String,
    val fromPort: String?,
    val toIp: String,
    val toPort: String?,
    val protocol: LinkProtocol,
    val neighbourSysName: String?
) {
    internal fun stableKey(): LinkKey = LinkKey(
        fromIp, fromPort, toIp, toPort, protocol
    )

    companion object {
        internal val ORDER = compareBy<TopologyLinkSnapshot>(
            { it.protocol.name }, { it.fromIp }, { it.fromPort },
            { it.toIp }, { it.toPort }, { it.neighbourSysName }
        )

        internal fun from(link: TopologyLink) = TopologyLinkSnapshot(
            fromIp = link.fromIp.snapshotIdentity(),
            fromPort = link.fromPort,
            toIp = link.toIp.snapshotIdentity(),
            toPort = link.toPort,
            protocol = link.protocol,
            neighbourSysName = link.neighbourSysName
        )
    }
}

internal data class LinkKey(
    val fromIp: String,
    val fromPort: String?,
    val toIp: String,
    val toPort: String?,
    val protocol: LinkProtocol
)

private data class LinkEndpointKey(
    val fromIp: String,
    val fromPort: String?,
    val toIp: String,
    val toPort: String?
)

private data class IndexedLink(val occurrence: Int, val link: TopologyLinkSnapshot)

private fun TopologyLinkSnapshot.endpointKey() =
    LinkEndpointKey(fromIp, fromPort, toIp, toPort)

private fun TopologyNode.toSnapshot(identity: String) = TopologyNodeSnapshot(
    ip = identity,
    sysName = sysName,
    sysDescr = sysDescr,
    vendor = vendor,
    model = model,
    firmwareVersion = firmwareVersion,
    sysLocation = sysLocation,
    uptimeHuman = uptimeHuman,
    capabilities = capabilities.toSet(),
    interfaces = interfaces.sortedWith(compareBy<SnmpInterface>({ it.index }, { it.name }))
        .toList(),
    vlans = vlans.sortedWith(compareBy<VlanInfo>({ it.id }, { it.name })).toList(),
    snmpReachable = snmpReachable,
    tableObservations = tableObservations.toSortedMap(compareBy { it.name }).toMap()
)

private fun TopologyNodeSnapshot.canonicalized(identity: String) = copy(
    ip = identity,
    capabilities = capabilities.toSet(),
    interfaces = interfaces.sortedWith(compareBy<SnmpInterface>({ it.index }, { it.name })).toList(),
    vlans = vlans.sortedWith(compareBy<VlanInfo>({ it.id }, { it.name })).toList(),
    tableObservations = tableObservations.toSortedMap(compareBy { it.name }).toMap()
)

private fun TopologySnapshot.canonicalized(): TopologySnapshot {
    val groupedNodes = nodes.groupBy { it.ip.snapshotIdentity() }
    val conflicts = groupedNodes.filter { (ip, records) ->
        records.map { it.canonicalized(ip) }.distinct().size > 1
    }.keys
    val canonicalNodes = groupedNodes.mapNotNull { (ip, records) ->
        if (ip in conflicts) null else records.firstOrNull()?.canonicalized(ip)
    }.sortedBy { it.ip }
    val canonicalLinks = links.map { link ->
        link.copy(
            fromIp = link.fromIp.snapshotIdentity(),
            toIp = link.toIp.snapshotIdentity()
        )
    }.sortedWith(TopologyLinkSnapshot.ORDER)
    return copy(
        seedIp = seedIp.snapshotIdentity(),
        scanContext = scanContext?.canonicalized(),
        nodes = canonicalNodes,
        links = canonicalLinks,
        truncationReasons = truncationReasons.toSet(),
        conflictingNodeIps = (conflictingNodeIps.map { it.snapshotIdentity() } + conflicts)
            .toSortedSet()
    )
}

private fun TopologyScanContext.canonicalized() = copy(
    seedIp = seedIp.snapshotIdentity(),
    targetIp = targetIp.snapshotIdentity(),
    credentialScopeId = credentialScopeId?.trim()?.takeIf(String::isNotEmpty)
)

private fun String.snapshotIdentity(): String {
    val value = trim()
    canonicalIpv4(value.lowercase())?.let { return it }
    canonicalIpv6(value)?.let { return it }
    return value.lowercase()
}

/** Parse only numeric IPv4 syntax; never pass a hostname to any resolver. */
private fun canonicalIpv4(value: String): String? {
    val parts = value.split('.')
    if (parts.size != 4 || parts.any { part ->
            part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }
        }) return null
    val octets = parts.map { it.toIntOrNull()?.takeIf { number -> number in 0..255 } ?: return null }
    return octets.joinToString(".")
}

/** Parse numeric IPv6 grammar and render RFC 5952-style text without DNS or platform resolvers. */
private fun canonicalIpv6(value: String): String? {
    if (':' !in value) return null
    val zoneAt = value.indexOf('%')
    val zone = if (zoneAt >= 0) {
        value.substring(zoneAt + 1).takeIf { it.isNotEmpty() && it.all { c ->
            c.isLetterOrDigit() || c in "_.-"
        } } ?: return null
    } else null
    val address = (if (zoneAt >= 0) value.substring(0, zoneAt) else value).lowercase()
    if ('%' in address) return null
    val expandedAddress = if ('.' in address) {
        val lastColon = address.lastIndexOf(':')
        if (lastColon < 0) return null
        val ipv4 = canonicalIpv4(address.substring(lastColon + 1)) ?: return null
        val octets = ipv4.split('.').map { it.toInt() }
        val first = (octets[0] shl 8) or octets[1]
        val second = (octets[2] shl 8) or octets[3]
        address.substring(0, lastColon + 1) +
            first.toString(16) + ":" + second.toString(16)
    } else address
    val compressionAt = expandedAddress.indexOf("::")
    if (compressionAt >= 0 && expandedAddress.indexOf("::", compressionAt + 2) >= 0) return null
    fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val tokens = part.split(':')
        if (tokens.any { token -> token.isEmpty() || token.length > 4 ||
                token.any { it !in '0'..'9' && it !in 'a'..'f' } }) return null
        return tokens.map { it.toInt(16) }
    }
    val words = if (compressionAt >= 0) {
        val left = groups(expandedAddress.substring(0, compressionAt)) ?: return null
        val right = groups(expandedAddress.substring(compressionAt + 2)) ?: return null
        val zeroCount = 8 - left.size - right.size
        if (zeroCount < 1) return null
        left + List(zeroCount) { 0 } + right
    } else {
        groups(expandedAddress)?.takeIf { it.size == 8 } ?: return null
    }
    if (words.size != 8) return null

    var bestStart = -1
    var bestLength = 1
    var index = 0
    while (index < words.size) {
        if (words[index] != 0) {
            index++
            continue
        }
        val start = index
        while (index < words.size && words[index] == 0) index++
        val length = index - start
        if (length > bestLength) {
            bestStart = start
            bestLength = length
        }
    }
    val rendered = if (bestStart < 0) words.joinToString(":") { it.toString(16) }
    else {
        val left = words.take(bestStart).joinToString(":") { it.toString(16) }
        val right = words.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
        when {
            left.isEmpty() && right.isEmpty() -> "::"
            left.isEmpty() -> "::$right"
            right.isEmpty() -> "$left::"
            else -> "$left::$right"
        }
    }
    return rendered + (zone?.let { "%$it" } ?: "")
}

enum class TopologyComparisonStatus { COMPARABLE, INCOMPARABLE }
enum class TopologyChangeKind { ADDED, REMOVED, CHANGED }

enum class TopologyIncomparabilityReason {
    MISSING_TABLE_OBSERVATION,
    PARTIAL_TABLE_OBSERVATION,
    FAILED_TABLE_OBSERVATION,
    GRAPH_SNMP_ERRORS,
    GRAPH_TRUNCATED,
    CONFLICTING_NODE_IDENTITY,
    REUSED_NODE_IDENTITY,
    CONFLICTING_ROW_IDENTITY,
    UNKNOWN_SCAN_CONTEXT,
    UNKNOWN_CREDENTIAL_SCOPE,
    DIFFERENT_SCAN_CONTEXT
}

data class TopologyTableChanges(
    val table: TopologyDataTable,
    val status: TopologyComparisonStatus,
    val reasons: Set<TopologyIncomparabilityReason>,
    val interfaceChanges: List<TopologyInterfaceChange> = emptyList(),
    val vlanChanges: List<TopologyVlanChange> = emptyList(),
    val linkChanges: List<TopologyLinkChange> = emptyList()
)

data class TopologyInterfaceChange(
    val kind: TopologyChangeKind,
    val nodeIp: String,
    val index: Int,
    val before: SnmpInterface?,
    val after: SnmpInterface?
)

data class TopologyVlanChange(
    val kind: TopologyChangeKind,
    val nodeIp: String,
    val id: Int,
    val before: VlanInfo?,
    val after: VlanInfo?
)

/** Each occurrence is retained: exact duplicate edges are compared as a deterministic multiset. */
data class TopologyLinkChange(
    val kind: TopologyChangeKind,
    val link: TopologyLinkSnapshot,
    val occurrence: Int,
    /** Set only for CHANGED, where the stable endpoint is the same but observed label changed. */
    val before: TopologyLinkSnapshot? = null
)

enum class TopologyNodeField {
    SYS_NAME, SYS_DESCR, VENDOR, MODEL, FIRMWARE_VERSION, SYS_LOCATION, UPTIME, CAPABILITIES,
    SNMP_REACHABLE
}

data class TopologyNodeChange(
    val ip: String,
    val before: TopologyNodeSnapshot,
    val after: TopologyNodeSnapshot,
    val changedFields: Set<TopologyNodeField>
)

data class TopologySnapshotDiff(
    val tableChanges: Map<TopologyDataTable, TopologyTableChanges>,
    val nodeChanges: List<TopologyNodeChange>,
    /** Candidate absences are unknown because a graph carries no full-coverage proof. */
    val unknownNodeAbsences: Set<String>,
    val unknownNodeAppearances: Set<String>,
    /** Same IP has disjoint complete interface MAC evidence across snapshots. */
    val reusedNodeIdentities: Set<String>
) {
    fun changesFor(table: TopologyDataTable): TopologyTableChanges =
        requireNotNull(tableChanges[table])
}

object TopologySnapshotDiffer {
    fun compare(before: TopologyGraph, after: TopologyGraph): TopologySnapshotDiff =
        compare(TopologySnapshot.from(before), TopologySnapshot.from(after))

    fun compare(before: TopologySnapshot, after: TopologySnapshot): TopologySnapshotDiff {
        val oldSnapshot = before.canonicalized()
        val newSnapshot = after.canonicalized()
        val contextReasons = buildSet {
            if (oldSnapshot.scanContext == null || newSnapshot.scanContext == null) {
                add(TopologyIncomparabilityReason.UNKNOWN_SCAN_CONTEXT)
            }
            if (oldSnapshot.scanContext?.credentialScopeId.isNullOrBlank() ||
                newSnapshot.scanContext?.credentialScopeId.isNullOrBlank()
            ) {
                add(TopologyIncomparabilityReason.UNKNOWN_CREDENTIAL_SCOPE)
            }
            if (oldSnapshot.scanContext != null && newSnapshot.scanContext != null &&
                oldSnapshot.scanContext != newSnapshot.scanContext
            ) {
                add(TopologyIncomparabilityReason.DIFFERENT_SCAN_CONTEXT)
            }
        }
        val beforeNodes = oldSnapshot.nodes.associateBy { it.ip }
        val afterNodes = newSnapshot.nodes.associateBy { it.ip }
        val conflicts = oldSnapshot.conflictingNodeIps + newSnapshot.conflictingNodeIps
        val reused = detectReusedIdentities(beforeNodes, afterNodes, conflicts)
        val baseChanges = TopologyDataTable.entries.associateWith { table ->
            tableChanges(
                table, oldSnapshot, newSnapshot, beforeNodes, afterNodes, conflicts, reused,
                contextReasons
            )
        }.toSortedMap(compareBy { it.name })
        val changes = if (contextReasons.isEmpty()) {
            classifyProtocolChanges(baseChanges, oldSnapshot.links, newSnapshot.links)
        } else baseChanges
        val common = beforeNodes.keys.intersect(afterNodes.keys) - conflicts - reused
        val nodeChanges = if (contextReasons.isNotEmpty()) emptyList() else common.mapNotNull { ip ->
            val old = beforeNodes.getValue(ip)
            val new = afterNodes.getValue(ip)
            val changed = changedFields(old, new)
            if (changed.isEmpty()) null else TopologyNodeChange(ip, old, new, changed)
        }.sortedBy { it.ip }
        val beforeIps = beforeNodes.keys + oldSnapshot.conflictingNodeIps
        val afterIps = afterNodes.keys + newSnapshot.conflictingNodeIps
        return TopologySnapshotDiff(
            tableChanges = changes,
            nodeChanges = nodeChanges,
            unknownNodeAbsences = if (contextReasons.isEmpty()) {
                (beforeIps - afterIps).toSortedSet()
            } else emptySet(),
            unknownNodeAppearances = if (contextReasons.isEmpty()) {
                (afterIps - beforeIps).toSortedSet()
            } else emptySet(),
            reusedNodeIdentities = if (contextReasons.isEmpty()) reused.toSortedSet() else emptySet()
        )
    }

    private fun tableChanges(
        table: TopologyDataTable,
        before: TopologySnapshot,
        after: TopologySnapshot,
        beforeNodes: Map<String, TopologyNodeSnapshot>,
        afterNodes: Map<String, TopologyNodeSnapshot>,
        conflicts: Set<String>,
        reused: Set<String>,
        contextReasons: Set<TopologyIncomparabilityReason>
    ): TopologyTableChanges {
        val reasons = buildSet {
            addAll(contextReasons)
            if (before.hadSnmpErrors || after.hadSnmpErrors) {
                add(TopologyIncomparabilityReason.GRAPH_SNMP_ERRORS)
            }
            if (before.truncationReasons.any { affects(it, table) } ||
                after.truncationReasons.any { affects(it, table) }) {
                add(TopologyIncomparabilityReason.GRAPH_TRUNCATED)
            }
            if (conflicts.isNotEmpty()) add(TopologyIncomparabilityReason.CONFLICTING_NODE_IDENTITY)
            if (reused.isNotEmpty()) add(TopologyIncomparabilityReason.REUSED_NODE_IDENTITY)

            val protocol = when (table) {
                TopologyDataTable.LLDP_NEIGHBORS -> LinkProtocol.LLDP
                TopologyDataTable.CDP_NEIGHBORS -> LinkProtocol.CDP
                else -> null
            }
            val linkOwners = if (protocol == null) emptySet() else {
                (before.links.asSequence() + after.links.asSequence())
                    .filter { it.protocol == protocol }
                    .map { it.fromIp }
                    .toSet()
            }
            val allNodes = beforeNodes.keys + afterNodes.keys + linkOwners
            allNodes.forEach { ip ->
                if (ip in conflicts) return@forEach
                val old = beforeNodes[ip]
                val new = afterNodes[ip]
                if (old == null || new == null) {
                    add(TopologyIncomparabilityReason.MISSING_TABLE_OBSERVATION)
                } else {
                    observationReason(old, table)?.let(::add)
                    observationReason(new, table)?.let(::add)
                }
            }
            (beforeNodes.values + afterNodes.values).forEach { node ->
                val duplicateRows = when (table) {
                    TopologyDataTable.INTERFACES ->
                        node.interfaces.groupBy { it.index }.values.any { it.distinct().size > 1 }
                    TopologyDataTable.VLANS ->
                        node.vlans.groupBy { it.id }.values.any { it.distinct().size > 1 }
                    else -> false
                }
                if (duplicateRows) add(TopologyIncomparabilityReason.CONFLICTING_ROW_IDENTITY)
            }
        }
        val status = if (reasons.isEmpty()) TopologyComparisonStatus.COMPARABLE
        else TopologyComparisonStatus.INCOMPARABLE
        val commonIps = beforeNodes.keys.intersect(afterNodes.keys).minus(conflicts)
        return TopologyTableChanges(
            table = table,
            status = status,
            reasons = reasons,
            interfaceChanges = if (table == TopologyDataTable.INTERFACES) {
                diffInterfaces(commonIps, beforeNodes, afterNodes, status)
            } else emptyList(),
            vlanChanges = if (table == TopologyDataTable.VLANS) {
                diffVlans(commonIps, beforeNodes, afterNodes, status)
            } else emptyList(),
            linkChanges = if (table == TopologyDataTable.LLDP_NEIGHBORS ||
                table == TopologyDataTable.CDP_NEIGHBORS) {
                diffLinks(table, before.links, after.links, status)
            } else emptyList()
        )
    }

    private fun observationReason(
        node: TopologyNodeSnapshot,
        table: TopologyDataTable
    ): TopologyIncomparabilityReason? {
        val observation = node.tableObservations[table]
            ?: return TopologyIncomparabilityReason.MISSING_TABLE_OBSERVATION
        return when (observation.completeness) {
            TopologyTableCompleteness.COMPLETE -> null
            TopologyTableCompleteness.PARTIAL -> TopologyIncomparabilityReason.PARTIAL_TABLE_OBSERVATION
            TopologyTableCompleteness.FAILED -> TopologyIncomparabilityReason.FAILED_TABLE_OBSERVATION
        }
    }

    private fun detectReusedIdentities(
        before: Map<String, TopologyNodeSnapshot>,
        after: Map<String, TopologyNodeSnapshot>,
        conflicts: Set<String>
    ): Set<String> = before.keys.intersect(after.keys).asSequence()
        .filterNot { it in conflicts }
        .filter { ip ->
            val old = before.getValue(ip)
            val new = after.getValue(ip)
            val oldComplete = old.tableObservations[TopologyDataTable.INTERFACES]
                ?.completeness == TopologyTableCompleteness.COMPLETE
            val newComplete = new.tableObservations[TopologyDataTable.INTERFACES]
                ?.completeness == TopologyTableCompleteness.COMPLETE
            if (!oldComplete || !newComplete) return@filter false
            val oldMacs = old.interfaces.mapNotNull { it.macAddress?.normalizeMac() }.toSet()
            val newMacs = new.interfaces.mapNotNull { it.macAddress?.normalizeMac() }.toSet()
            oldMacs.isNotEmpty() && newMacs.isNotEmpty() && oldMacs.intersect(newMacs).isEmpty()
        }.toSet()

    private fun String.normalizeMac(): String? = filterNot { it == ':' || it == '-' || it == '.' }
        .lowercase()
        .takeIf {
            it.length in setOf(12, 16) &&
                it.all { c -> c in '0'..'9' || c in 'a'..'f' } &&
                it != "000000000000" && it != "0000000000000000" &&
                it != "ffffffffffff" && it != "ffffffffffffffff"
        }

    private fun affects(reason: TopologyTruncationReason, table: TopologyDataTable): Boolean =
        when (reason) {
            TopologyTruncationReason.INTERFACE_LIMIT ->
                table == TopologyDataTable.INTERFACES
            TopologyTruncationReason.VLAN_LIMIT ->
                table == TopologyDataTable.VLANS
            TopologyTruncationReason.LINK_LIMIT ->
                table == TopologyDataTable.LLDP_NEIGHBORS ||
                    table == TopologyDataTable.CDP_NEIGHBORS
            else -> true
        }

    private fun diffInterfaces(
        ips: Set<String>,
        before: Map<String, TopologyNodeSnapshot>,
        after: Map<String, TopologyNodeSnapshot>,
        status: TopologyComparisonStatus
    ): List<TopologyInterfaceChange> = buildList {
        if (status != TopologyComparisonStatus.COMPARABLE) return@buildList
        ips.sorted().forEach { ip ->
            val old = before.getValue(ip).interfaces.associateBy { it.index }
            val new = after.getValue(ip).interfaces.associateBy { it.index }
            (old.keys + new.keys).sorted().forEach { index ->
                val previous = old[index]
                val current = new[index]
                if (previous != current) {
                    add(TopologyInterfaceChange(
                        kind = changeKind(previous, current),
                        nodeIp = ip,
                        index = index,
                        before = previous,
                        after = current
                    ))
                }
            }
        }
    }

    private fun diffVlans(
        ips: Set<String>,
        before: Map<String, TopologyNodeSnapshot>,
        after: Map<String, TopologyNodeSnapshot>,
        status: TopologyComparisonStatus
    ): List<TopologyVlanChange> = buildList {
        if (status != TopologyComparisonStatus.COMPARABLE) return@buildList
        ips.sorted().forEach { ip ->
            val old = before.getValue(ip).vlans.associateBy { it.id }
            val new = after.getValue(ip).vlans.associateBy { it.id }
            (old.keys + new.keys).sorted().forEach { id ->
                val previous = old[id]
                val current = new[id]
                if (previous != current) {
                    add(TopologyVlanChange(
                        kind = changeKind(previous, current),
                        nodeIp = ip,
                        id = id,
                        before = previous,
                        after = current
                    ))
                }
            }
        }
    }

    private fun diffLinks(
        table: TopologyDataTable,
        before: List<TopologyLinkSnapshot>,
        after: List<TopologyLinkSnapshot>,
        status: TopologyComparisonStatus
    ): List<TopologyLinkChange> {
        if (status != TopologyComparisonStatus.COMPARABLE) return emptyList()
        val protocol = if (table == TopologyDataTable.LLDP_NEIGHBORS) LinkProtocol.LLDP
        else LinkProtocol.CDP
        val oldLinks = before.filter { it.protocol == protocol }.groupBy { it.stableKey() }
        val newLinks = after.filter { it.protocol == protocol }.groupBy { it.stableKey() }
        val links = (oldLinks.keys + newLinks.keys).sortedWith(
            compareBy<LinkKey>(
                { it.fromIp }, { it.fromPort }, { it.toIp }, { it.toPort }
            )
        )
        return buildList {
            links.forEach { key ->
                val oldOccurrences = oldLinks[key].orEmpty().sortedBy { it.neighbourSysName }
                val newOccurrences = newLinks[key].orEmpty().sortedBy { it.neighbourSysName }
                val sharedCount = minOf(oldOccurrences.size, newOccurrences.size)
                (0 until sharedCount).forEach { index ->
                    val old = oldOccurrences[index]
                    val new = newOccurrences[index]
                    if (old.neighbourSysName != new.neighbourSysName) {
                        add(TopologyLinkChange(
                            TopologyChangeKind.CHANGED, new, index + 1, before = old
                        ))
                    }
                }
                (sharedCount until newOccurrences.size).forEach { index ->
                    add(TopologyLinkChange(
                        TopologyChangeKind.ADDED, newOccurrences[index], index + 1
                    ))
                }
                (sharedCount until oldOccurrences.size).forEach { index ->
                    add(TopologyLinkChange(
                        TopologyChangeKind.REMOVED, oldOccurrences[index], index + 1
                    ))
                }
            }
        }
    }

    /**
     * A stable endpoint seen under LLDP before and CDP after (or vice versa) is a protocol change,
     * not a missing edge plus a new one. Both protocol table observations must be complete.
     * If either observation is incomplete, suppress that candidate transition instead of
     * presenting an unsupported removal/addition.
     */
    private fun classifyProtocolChanges(
        base: Map<TopologyDataTable, TopologyTableChanges>,
        before: List<TopologyLinkSnapshot>,
        after: List<TopologyLinkSnapshot>
    ): Map<TopologyDataTable, TopologyTableChanges> {
        val endpoints = (before.map { it.endpointKey() } + after.map { it.endpointKey() })
            .toSortedSet(compareBy<LinkEndpointKey>(
                { it.fromIp }, { it.fromPort }, { it.toIp }, { it.toPort }
            ))
        val mutable = base.mapValues { (_, value) -> value.linkChanges.toMutableList() }.toMutableMap()
        endpoints.forEach { endpoint ->
            val oldByProtocol = before.filter { it.endpointKey() == endpoint }
                .groupBy { it.protocol }.mapValues { (_, links) ->
                    links.sortedBy { it.neighbourSysName }
                }
            val newByProtocol = after.filter { it.endpointKey() == endpoint }
                .groupBy { it.protocol }.mapValues { (_, links) ->
                    links.sortedBy { it.neighbourSysName }
                }
            val oldResidual = mutableMapOf<LinkProtocol, List<IndexedLink>>()
            val newResidual = mutableMapOf<LinkProtocol, List<IndexedLink>>()
            LinkProtocol.entries.forEach { protocol ->
                val oldLinks = oldByProtocol[protocol].orEmpty()
                val newLinks = newByProtocol[protocol].orEmpty()
                val unchangedCount = minOf(oldLinks.size, newLinks.size)
                if (oldLinks.size > unchangedCount) {
                    oldResidual[protocol] = oldLinks.withIndex().drop(unchangedCount)
                        .map { IndexedLink(it.index + 1, it.value) }
                }
                if (newLinks.size > unchangedCount) {
                    newResidual[protocol] = newLinks.withIndex().drop(unchangedCount)
                        .map { IndexedLink(it.index + 1, it.value) }
                }
            }
            LinkProtocol.entries.forEach { oldProtocol ->
                LinkProtocol.entries.forEach targetLoop@{ newProtocol ->
                    if (oldProtocol == newProtocol) return@targetLoop
                    val oldCandidates = oldResidual[oldProtocol].orEmpty()
                    val newCandidates = newResidual[newProtocol].orEmpty()
                    val count = minOf(oldCandidates.size, newCandidates.size)
                    repeat(count) { index ->
                        val old = oldCandidates[index]
                        val new = newCandidates[index]
                        val oldTable = oldProtocol.toTable()
                        val newTable = newProtocol.toTable()
                        mutable[oldTable]?.removeOne(
                            TopologyChangeKind.REMOVED, old.link, old.occurrence
                        )
                        mutable[newTable]?.removeOne(
                            TopologyChangeKind.ADDED, new.link, new.occurrence
                        )
                        val bothComplete =
                            base[oldTable]?.status == TopologyComparisonStatus.COMPARABLE &&
                                base[newTable]?.status == TopologyComparisonStatus.COMPARABLE
                        if (bothComplete) {
                            mutable[newTable]?.add(TopologyLinkChange(
                                kind = TopologyChangeKind.CHANGED,
                                link = new.link,
                                occurrence = new.occurrence,
                                before = old.link
                            ))
                        }
                    }
                    if (count > 0) {
                        oldResidual[oldProtocol] = oldCandidates.drop(count)
                        newResidual[newProtocol] = newCandidates.drop(count)
                    }
                }
            }
        }
        return base.mapValues { (table, value) ->
            val linkChanges = mutable[table].orEmpty().sortedWith(
                compareBy<TopologyLinkChange>(
                    { it.link.protocol.name },
                    { it.link.fromIp },
                    { it.link.fromPort },
                    { it.link.toIp },
                    { it.link.toPort },
                    { it.link.neighbourSysName },
                    { it.kind.name }
                )
                    .thenBy { it.occurrence }
            )
            value.copy(linkChanges = linkChanges)
        }.toSortedMap(compareBy { it.name })
    }

    private fun MutableList<TopologyLinkChange>.removeOne(
        kind: TopologyChangeKind,
        link: TopologyLinkSnapshot,
        occurrence: Int
    ) {
        val index = indexOfFirst {
            it.kind == kind && it.link == link && it.occurrence == occurrence
        }
        if (index >= 0) removeAt(index)
    }

    private fun LinkProtocol.toTable() = when (this) {
        LinkProtocol.LLDP -> TopologyDataTable.LLDP_NEIGHBORS
        LinkProtocol.CDP -> TopologyDataTable.CDP_NEIGHBORS
    }

    private fun changeKind(before: Any?, after: Any?): TopologyChangeKind = when {
        before == null -> TopologyChangeKind.ADDED
        after == null -> TopologyChangeKind.REMOVED
        else -> TopologyChangeKind.CHANGED
    }

    private fun changedFields(
        before: TopologyNodeSnapshot,
        after: TopologyNodeSnapshot
    ): Set<TopologyNodeField> = buildSet {
        if (before.sysName != after.sysName) add(TopologyNodeField.SYS_NAME)
        if (before.sysDescr != after.sysDescr) add(TopologyNodeField.SYS_DESCR)
        if (before.vendor != after.vendor) add(TopologyNodeField.VENDOR)
        if (before.model != after.model) add(TopologyNodeField.MODEL)
        if (before.firmwareVersion != after.firmwareVersion) add(TopologyNodeField.FIRMWARE_VERSION)
        if (before.sysLocation != after.sysLocation) add(TopologyNodeField.SYS_LOCATION)
        if (before.uptimeHuman != after.uptimeHuman) add(TopologyNodeField.UPTIME)
        if (before.capabilities != after.capabilities) add(TopologyNodeField.CAPABILITIES)
        if (before.snmpReachable != after.snmpReachable) add(TopologyNodeField.SNMP_REACHABLE)
    }
}
