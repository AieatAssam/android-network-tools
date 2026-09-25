package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.aieat.netswissknife.core.network.HostValidator
import net.aieat.netswissknife.core.network.net.NetworkBinder
import net.aieat.netswissknife.core.network.net.NoOpNetworkBinder
import net.aieat.netswissknife.core.network.net.containsLocalNetworkPermissionDenied
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class TopologyDiscoveryRepositoryImpl(
    private val snmpClientFactory: SnmpClientFactory? = null,
    private val limits: TopologyResourceLimits = TopologyResourceLimits(),
    private val binder: NetworkBinder = NoOpNetworkBinder,
) : TopologyDiscoveryRepository {

    private val requestLimiter = Semaphore(MAX_CONCURRENT_SNMP_REQUESTS)
    private val effectiveSnmpClientFactory = snmpClientFactory
        ?: object : SnmpClientFactory {
            override fun create(params: TopologyParams): SnmpClient = Snmp4jClientImpl(params, binder)

            override fun create(params: TopologyParams, session: OperationSession): SnmpClient =
                Snmp4jClientImpl(
                    params,
                    binder,
                    session.budget.deadline,
                    deferInitialization = true,
                )
        }

    constructor(client: SnmpClient) : this(SnmpClientFactory { client }, TopologyResourceLimits())
    constructor(client: SnmpClient, limits: TopologyResourceLimits) :
        this(SnmpClientFactory { client }, limits)

    // Session-aware creation returns a closeable, uninitialized SNMP client first. The client
    // is then registered with the operation before its bounded transport setup/listen begins.
    override fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent> = channelFlow {
        val estimate = TopologyOperationBudget.estimate(
            params,
            configuredMaxNodes = limits.maxNodes,
            maxPagesPerWalk = limits.maxPagesPerWalk,
        )
        if (estimate == null) {
            send(TopologyDiscoveryEvent.Error(TopologyOperationBudget.OVER_CEILING_MESSAGE))
            return@channelFlow
        }
        // Keep session creation inside collection so re-collecting a cold Flow receives a
        // fresh one-shot session and an independent bounded deadline.
        discover(params, newSession(estimate.timeoutMillis)).collect { send(it) }
    }

    override fun discover(
        params: TopologyParams,
        session: OperationSession,
    ): Flow<TopologyDiscoveryEvent> = channelFlow {
        val effectiveRequestConcurrency = minOf(
            MAX_CONCURRENT_SNMP_REQUESTS,
            session.budget.maxConcurrentProbes,
        )
        val budgetEstimate = TopologyOperationBudget.estimate(
            params,
            configuredMaxNodes = limits.maxNodes,
            sessionConcurrency = effectiveRequestConcurrency,
            maxPagesPerWalk = limits.maxPagesPerWalk,
        )
        if (budgetEstimate == null) {
            send(TopologyDiscoveryEvent.Error(TopologyOperationBudget.OVER_CEILING_MESSAGE))
            return@channelFlow
        }
        val normalizedTarget = HostValidator.normalize(params.targetIp) ?: params.targetIp
        val effectiveParams = params.copy(targetIp = normalizedTarget)
        val scanContext = TopologyScanContext.from(effectiveParams.targetIp, effectiveParams)
        val partialGraph = AtomicReference(
            TopologyGraph(
                emptyList(),
                emptyList(),
                effectiveParams.targetIp,
                System.currentTimeMillis(),
                scanContext = scanContext
            )
        )
        try {
            val graph = OperationRunner.run(session) {
                currentCoroutineContext().ensureActive()
                val sessionRequestLimiter = Semaphore(
                    effectiveRequestConcurrency
                )
                val snmpClient = resources.register(
                    CloseOnceSnmpClient(effectiveSnmpClientFactory.create(effectiveParams, session))
                )
                val visited = mutableSetOf<String>()
                val queue = LinkedList<Pair<String, Int>>() // ip to hop depth
                queue.add(effectiveParams.targetIp to 0)
                val scheduledTargets = mutableSetOf(effectiveParams.targetIp)
                val effectiveMaxNodes = minOf(limits.maxNodes, budgetEstimate.maxNodes)

                val allNodes = mutableListOf<TopologyNode>()
                val allLinks = mutableListOf<TopologyLink>()
                val truncationReasons = ConcurrentHashMap.newKeySet<TopologyTruncationReason>()
                val snmpErrors = AtomicBoolean(false)
                val linkBudget = LinkBudget(limits.maxLinks, truncationReasons)
                val graphBudget = GraphByteBudget(limits.maxBytesPerGraph)

                while (queue.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    if (allNodes.size >= effectiveMaxNodes) {
                        truncationReasons.add(TopologyTruncationReason.NODE_LIMIT)
                        break
                    }
                    val (currentIp, currentHop) = queue.poll()
                    if (currentIp in visited) continue
                    visited.add(currentIp)

                    send(TopologyDiscoveryEvent.Progress("Querying $currentIp...", allNodes.size))

                    val target = SnmpTarget(ip = currentIp, params = effectiveParams)
                    val walkBudget = SnmpWalkBudget(limits)
                    val tableObservations = TableObservationCollector()

                    val sysDescrAttempt = attemptGet(snmpClient, target, "1.3.6.1.2.1.1.1.0", sessionRequestLimiter)
                    val sysNameAttempt = attemptGet(snmpClient, target, "1.3.6.1.2.1.1.5.0", sessionRequestLimiter)
                    if (sysDescrAttempt.error != null || sysNameAttempt.error != null) snmpErrors.set(true)
                    val sysDescr = retainScalar("1.3.6.1.2.1.1.1.0", sysDescrAttempt.value, walkBudget, truncationReasons)
                    val sysName = retainScalar("1.3.6.1.2.1.1.5.0", sysNameAttempt.value, walkBudget, truncationReasons)
                    val systemFailure = sysDescrAttempt.error ?: sysNameAttempt.error
                    if (sysDescr == null && sysName == null && systemFailure != null) {
                        if (currentIp == effectiveParams.targetIp) {
                            send(TopologyDiscoveryEvent.Error(SnmpErrorFormatter.describe(systemFailure), systemFailure))
                            return@run null
                        }
                    }
                    val sysLocation = retainScalar(
                        "1.3.6.1.2.1.1.6.0",
                        safeGet(snmpClient, target, "1.3.6.1.2.1.1.6.0", snmpErrors, sessionRequestLimiter),
                        walkBudget,
                        truncationReasons
                    )
                    val sysUpTimeStr = retainScalar(
                        "1.3.6.1.2.1.1.3.0",
                        safeGet(snmpClient, target, "1.3.6.1.2.1.1.3.0", snmpErrors, sessionRequestLimiter),
                        walkBudget,
                        truncationReasons
                    )

                    val snmpReachable = sysDescr != null || sysName != null
                    val uptimeHuman = sysUpTimeStr?.toLongOrNull()?.let {
                        TopologyNodeParser.timeticksToHuman(it)
                    }

                    val vendor = TopologyNodeParser.parseVendor(sysDescr ?: "")
                    val model = TopologyNodeParser.parseModel(sysDescr, null)
                    val firmware = TopologyNodeParser.parseFirmwareVersion(sysDescr, null)
                    val (interfaces, vlans, lldpResult, cdpResult) = coroutineScope {
                        val interfaces = async {
                            queryInterfaces(snmpClient, target, walkBudget, truncationReasons, snmpErrors, sessionRequestLimiter, tableObservations)
                        }
                        val vlans = async {
                            queryVlans(snmpClient, target, walkBudget, truncationReasons, snmpErrors, sessionRequestLimiter, tableObservations)
                        }
                        val lldp = async {
                            queryLldpNeighbours(
                                snmpClient, target, currentIp, currentHop, params.maxHops,
                                walkBudget, truncationReasons, snmpErrors, linkBudget, sessionRequestLimiter, tableObservations
                            )
                        }
                        val cdp = async {
                            queryCdpNeighbours(
                                snmpClient, target, currentIp, currentHop, params.maxHops,
                                walkBudget, truncationReasons, snmpErrors, linkBudget, sessionRequestLimiter, tableObservations
                            )
                        }
                        Quadruple(interfaces.await(), vlans.await(), lldp.await(), cdp.await())
                    }
                    val (lldpLinks, lldpNeighbourIps) = lldpResult
                    val (cdpLinks, cdpNeighbourIps) = cdpResult

                    val node = TopologyNode(
                        ip = currentIp,
                        sysName = sysName,
                        sysDescr = sysDescr,
                        vendor = vendor,
                        model = model,
                        firmwareVersion = firmware,
                        sysLocation = sysLocation,
                        uptimeHuman = uptimeHuman,
                        capabilities = inferCapabilities(sysDescr, vendor),
                        interfaces = interfaces,
                        vlans = vlans,
                        snmpReachable = snmpReachable,
                        tableObservations = tableObservations.snapshot()
                    )

                    if (!graphBudget.tryReserve(node)) {
                        truncationReasons.add(TopologyTruncationReason.GRAPH_BYTE_LIMIT)
                        break
                    }
                    var graphByteLimitReached = false
                    val retainedLinks = mutableListOf<TopologyLink>()
                    val linkTables = listOf(
                        TopologyDataTable.LLDP_NEIGHBORS to lldpLinks,
                        TopologyDataTable.CDP_NEIGHBORS to cdpLinks,
                    )
                    for (tableIndex in linkTables.indices) {
                        val (table, links) = linkTables[tableIndex]
                        for (link in links) {
                            if (!graphBudget.tryReserve(link)) {
                                truncationReasons.add(TopologyTruncationReason.GRAPH_BYTE_LIMIT)
                                tableObservations.recordTruncation(table)
                                graphByteLimitReached = true
                                break
                            }
                            retainedLinks.add(link)
                        }
                        if (graphByteLimitReached) {
                            linkTables.drop(tableIndex + 1).filter { it.second.isNotEmpty() }
                                .forEach { (omittedTable, _) -> tableObservations.recordTruncation(omittedTable) }
                            break
                        }
                    }
                    val retainedNode = node.copy(tableObservations = tableObservations.snapshot())
                    allNodes.add(retainedNode)
                    partialGraph.set(
                        TopologyGraph(
                            nodes = allNodes.toList(),
                            links = allLinks.toList(),
                            seedIp = effectiveParams.targetIp,
                            queriedAt = System.currentTimeMillis(),
                            truncationReasons = truncationReasons.toSet(),
                            hadSnmpErrors = snmpErrors.get(),
                            scanContext = scanContext,
                        )
                    )
                    send(TopologyDiscoveryEvent.NodeDiscovered(retainedNode))
                    retainedLinks.forEach { link ->
                        allLinks.add(link)
                        partialGraph.updateAndGet { prior -> prior.copy(links = allLinks.toList()) }
                        send(TopologyDiscoveryEvent.LinkDiscovered(link))
                    }
                    if (graphByteLimitReached) break

                    (lldpNeighbourIps + cdpNeighbourIps).forEach { neighbourIp ->
                        if (neighbourIp.isBlank() || neighbourIp in scheduledTargets) return@forEach
                        if (allNodes.size + queue.size >= effectiveMaxNodes) {
                            truncationReasons.add(TopologyTruncationReason.NODE_LIMIT)
                            return@forEach
                        }
                        if (queue.size >= limits.maxPendingTargets) {
                            truncationReasons.add(TopologyTruncationReason.PENDING_TARGET_LIMIT)
                            return@forEach
                        }
                        scheduledTargets.add(neighbourIp)
                        queue.add(neighbourIp to currentHop + 1)
                    }
                }

                TopologyGraph(
                    nodes = allNodes,
                    links = allLinks,
                    seedIp = effectiveParams.targetIp,
                    queriedAt = System.currentTimeMillis(),
                    truncationReasons = truncationReasons.toSet(),
                    hadSnmpErrors = snmpErrors.get(),
                    scanContext = scanContext
                )
            }
            // OperationRunner closes the registered client before returning; a cleanup error
            // therefore prevents publication of the terminal Complete event.
            if (graph != null) send(TopologyDiscoveryEvent.Complete(graph))
        } catch (e: Exception) {
            if (session.cancellationReason == CancellationReason.DEADLINE_EXCEEDED ||
                e is OperationDeadlineExceededException
            ) {
                send(TopologyDiscoveryEvent.TimeLimit(partialGraph.get()))
            } else {
                if (e is CancellationException) throw e
                send(TopologyDiscoveryEvent.Error(SnmpErrorFormatter.describe(e), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun newSession(timeoutMillis: Long): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.LOCAL_NETWORK,
            timeoutMillis = timeoutMillis,
        )
    )

    private companion object {
        const val MAX_CONCURRENT_SNMP_REQUESTS = 4
    }

    private data class GetAttempt(val value: String?, val error: Exception?)

    /** Aggregates every walk contributing to one semantic table on this node. */
    private class TableObservationCollector {
        private data class Counts(var successful: Int = 0, var failed: Int = 0, val failures: MutableSet<TopologyTableFailure> = mutableSetOf())
        private val counts = TopologyDataTable.entries.associateWith { Counts() }

        @Synchronized
        fun record(table: TopologyDataTable, result: SnmpWalkResult) {
            val tableCounts = counts.getValue(table)
            if (result.hadError) {
                tableCounts.failed++
                tableCounts.failures.add(TopologyTableFailure.SNMP_RESPONSE)
                if (result.entries.isNotEmpty()) tableCounts.successful++
            } else {
                tableCounts.successful++
            }
            if (result.truncationReasons.isNotEmpty()) {
                tableCounts.failures.add(TopologyTableFailure.TRUNCATED)
            }
        }

        @Synchronized
        fun recordFailure(table: TopologyDataTable, error: Exception) {
            val tableCounts = counts.getValue(table)
            tableCounts.failed++
            tableCounts.failures.add(error.topologyTableFailure())
        }

        private fun Exception.topologyTableFailure(): TopologyTableFailure {
            val message = message.orEmpty().lowercase()
            return when {
                this is java.net.SocketTimeoutException || "timeout" in message || "timed out" in message -> TopologyTableFailure.TIMEOUT
                "auth" in message || "credential" in message || "community" in message -> TopologyTableFailure.AUTHENTICATION
                else -> TopologyTableFailure.REQUEST_FAILED
            }
        }

        @Synchronized
        fun recordTruncation(table: TopologyDataTable) {
            counts.getValue(table).failures.add(TopologyTableFailure.TRUNCATED)
        }

        @Synchronized
        fun snapshot(): Map<TopologyDataTable, TopologyTableObservation> = counts.mapNotNull { (table, tableCounts) ->
            if (tableCounts.successful == 0 && tableCounts.failed == 0) return@mapNotNull null
            val failures = tableCounts.failures.toSet()
            val completeness = when {
                failures.isEmpty() -> TopologyTableCompleteness.COMPLETE
                tableCounts.successful > 0 -> TopologyTableCompleteness.PARTIAL
                else -> TopologyTableCompleteness.FAILED
            }
            table to TopologyTableObservation(completeness, failures)
        }.toMap()
    }

    private suspend fun attemptGet(
        client: SnmpClient,
        target: SnmpTarget,
        oid: String,
        sessionRequestLimiter: Semaphore,
    ): GetAttempt = sessionRequestLimiter.withPermit {
        requestLimiter.withPermit {
            try {
                GetAttempt(client.get(target, oid), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OperationDeadlineExceededException) {
                throw e
            } catch (e: Exception) {
                if (e.containsLocalNetworkPermissionDenied()) throw e
                GetAttempt(null, e)
            }
        }
    }

    private suspend fun safeGet(
        client: SnmpClient,
        target: SnmpTarget,
        oid: String,
        snmpErrors: AtomicBoolean,
        sessionRequestLimiter: Semaphore,
    ): String? {
        val attempt = attemptGet(client, target, oid, sessionRequestLimiter)
        if (attempt.error != null) snmpErrors.set(true)
        return attempt.value
    }

    private fun retainScalar(
        oid: String,
        value: String?,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>
    ): String? {
        if (value == null) return null
        val retainedValue = if (value.length > budget.maxValueChars) {
            truncationReasons.add(TopologyTruncationReason.SCALAR_VALUE_LIMIT)
            value.take(budget.maxValueChars)
        } else {
            value
        }
        return when (val reservation = budget.tryReserveScalar(oid, retainedValue)) {
            is SnmpWalkBudget.Reservation.Accepted -> retainedValue
            is SnmpWalkBudget.Reservation.Rejected -> {
                truncationReasons.add(reservation.reason)
                null
            }
        }
    }

    private suspend fun safeWalk(
        client: SnmpClient,
        target: SnmpTarget,
        oid: String,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>,
        snmpErrors: AtomicBoolean,
        sessionRequestLimiter: Semaphore,
        table: TopologyDataTable,
        tableObservations: TableObservationCollector,
    ): Map<String, String> =
        sessionRequestLimiter.withPermit {
            requestLimiter.withPermit {
                try {
                    val result = client.walk(target, oid, budget)
                    truncationReasons.addAll(result.truncationReasons)
                    if (result.hadError) snmpErrors.set(true)
                    tableObservations.record(table, result)
                    result.entries
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OperationDeadlineExceededException) {
                    throw e
                } catch (e: Exception) {
                    if (e.containsLocalNetworkPermissionDenied()) throw e
                    snmpErrors.set(true)
                    tableObservations.recordFailure(table, e)
                    emptyMap()
                }
            }
        }

    private suspend fun queryInterfaces(
        client: SnmpClient,
        target: SnmpTarget,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>,
        snmpErrors: AtomicBoolean,
        sessionRequestLimiter: Semaphore,
        tableObservations: TableObservationCollector,
    ): List<SnmpInterface> {
        val (descrWalk, speedWalk, highSpeedWalk, statusWalk, macWalk) = coroutineScope {
            val table = TopologyDataTable.INTERFACES
            val descr = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.2", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            val speed = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.5", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            val highSpeed = async { safeWalk(client, target, "1.3.6.1.2.1.31.1.1.1.15", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            val status = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.8", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            val mac = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.6", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            Quintuple(descr.await(), speed.await(), highSpeed.await(), status.await(), mac.await())
        }

        val parsed = descrWalk.entries.asSequence().mapNotNull { (oid, name) ->
            val idx = oid.substringAfterLast(".").toIntOrNull() ?: return@mapNotNull null
            val status = when (statusWalk["1.3.6.1.2.1.2.2.1.8.$idx"]?.toIntOrNull()) {
                1 -> InterfaceStatus.UP
                2 -> InterfaceStatus.DOWN
                else -> InterfaceStatus.UNKNOWN
            }
            val speedBps = highSpeedWalk["1.3.6.1.2.1.31.1.1.1.15.$idx"]
                ?.toLongOrNull()
                ?.let(TopologyNodeParser::ifHighSpeedToSpeedBps)
                ?: speedWalk["1.3.6.1.2.1.2.2.1.5.$idx"]?.toLongOrNull()
            SnmpInterface(
                index = idx,
                name = name,
                macAddress = macWalk["1.3.6.1.2.1.2.2.1.6.$idx"],
                speedBps = speedBps,
                operStatus = status
            )
        }.take(limits.maxInterfacesPerNode + 1).toList()
        if (parsed.size > limits.maxInterfacesPerNode) {
            truncationReasons.add(TopologyTruncationReason.INTERFACE_LIMIT)
            tableObservations.recordTruncation(TopologyDataTable.INTERFACES)
        }
        return parsed.take(limits.maxInterfacesPerNode)
    }

    private suspend fun queryVlans(
        client: SnmpClient,
        target: SnmpTarget,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>,
        snmpErrors: AtomicBoolean,
        sessionRequestLimiter: Semaphore,
        tableObservations: TableObservationCollector,
    ): List<VlanInfo> {
        val vlans = mutableListOf<VlanInfo>()
        val (vtpWalk, vtpStateWalk) = coroutineScope {
            val table = TopologyDataTable.VLANS
            val names = async { safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.4", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            val states = async { safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.2", budget, truncationReasons, snmpErrors, sessionRequestLimiter, table, tableObservations) }
            names.await() to states.await()
        }

        vtpWalk.forEach { (oid, name) ->
            if (vlans.size >= limits.maxVlansPerNode) {
                truncationReasons.add(TopologyTruncationReason.VLAN_LIMIT)
                tableObservations.recordTruncation(TopologyDataTable.VLANS)
                return@forEach
            }
            val vlanId = oid.substringAfterLast(".").toIntOrNull() ?: return@forEach
            val stateVal = vtpStateWalk["1.3.6.1.4.1.9.9.46.1.3.1.1.2.$vlanId"]?.toIntOrNull()
            vlans.add(VlanInfo(id = vlanId, name = name, active = stateVal == 1))
        }

        if (vlans.isEmpty()) {
            safeWalk(client, target, "1.3.6.1.2.1.17.7.1.4.3.1.1", budget, truncationReasons, snmpErrors, sessionRequestLimiter, TopologyDataTable.VLANS, tableObservations).forEach { (oid, name) ->
                if (vlans.size >= limits.maxVlansPerNode) {
                    truncationReasons.add(TopologyTruncationReason.VLAN_LIMIT)
                    tableObservations.recordTruncation(TopologyDataTable.VLANS)
                    return@forEach
                }
                val vlanId = oid.substringAfterLast(".").toIntOrNull() ?: return@forEach
                vlans.add(VlanInfo(id = vlanId, name = name, active = true))
            }
        }
        return vlans
    }

    private suspend fun queryLldpNeighbours(
        client: SnmpClient,
        target: SnmpTarget,
        fromIp: String,
        currentHop: Int,
        maxHops: Int,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>,
        snmpErrors: AtomicBoolean,
        linkBudget: LinkBudget,
        sessionRequestLimiter: Semaphore,
        tableObservations: TableObservationCollector,
    ): Pair<List<TopologyLink>, List<String>> {
        val lldpWalk = safeWalk(client, target, "1.0.8802.1.1.2.1.4", budget, truncationReasons, snmpErrors, sessionRequestLimiter, TopologyDataTable.LLDP_NEIGHBORS, tableObservations)
        if (lldpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val remoteEntries = TopologyMibParser.parseLldpRemTable(lldpWalk)
        val managementAddresses = TopologyMibParser.parseLldpManAddrTable(lldpWalk).ipv4
        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()

        remoteEntries.forEach { entry ->
            val neighbourIp = managementAddresses[entry.key] ?: return@forEach
            if (!linkBudget.tryReserve(TopologyDataTable.LLDP_NEIGHBORS, tableObservations)) return@forEach
            links.add(
                TopologyLink(
                    fromIp = fromIp,
                    fromPort = entry.portId,
                    toIp = neighbourIp,
                    toPort = null,
                    protocol = LinkProtocol.LLDP,
                    neighbourSysName = entry.sysName
                )
            )
            if (currentHop < maxHops) neighbourIps.add(neighbourIp)
        }
        return links to neighbourIps
    }

    private suspend fun queryCdpNeighbours(
        client: SnmpClient,
        target: SnmpTarget,
        fromIp: String,
        currentHop: Int,
        maxHops: Int,
        budget: SnmpWalkBudget,
        truncationReasons: MutableSet<TopologyTruncationReason>,
        snmpErrors: AtomicBoolean,
        linkBudget: LinkBudget,
        sessionRequestLimiter: Semaphore,
        tableObservations: TableObservationCollector,
    ): Pair<List<TopologyLink>, List<String>> {
        val cdpWalk = safeWalk(client, target, "1.3.6.1.4.1.9.9.23.1.2.1", budget, truncationReasons, snmpErrors, sessionRequestLimiter, TopologyDataTable.CDP_NEIGHBORS, tableObservations)
        if (cdpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()
        TopologyMibParser.parseCdpCache(cdpWalk).forEach { entry ->
            if (!linkBudget.tryReserve(TopologyDataTable.CDP_NEIGHBORS, tableObservations)) return@forEach
            links.add(
                TopologyLink(
                    fromIp = fromIp,
                    fromPort = null,
                    toIp = entry.address,
                    toPort = entry.port,
                    protocol = LinkProtocol.CDP,
                    neighbourSysName = entry.deviceId
                )
            )
            if (currentHop < maxHops) neighbourIps.add(entry.address)
        }
        return links to neighbourIps
    }

    private fun inferCapabilities(sysDescr: String?, vendor: String?): Set<DeviceCapability> {
        if (sysDescr == null) return emptySet()
        val caps = mutableSetOf<DeviceCapability>()
        val lower = sysDescr.lowercase()
        if (lower.contains("router") || lower.contains("gateway")) caps.add(DeviceCapability.ROUTER)
        if (lower.contains("switch") || lower.contains("catalyst") || lower.contains("nexus")) caps.add(DeviceCapability.SWITCH)
        if (lower.contains("access point") || lower.contains("wireless") || lower.contains("wifi") || lower.contains("wi-fi")) caps.add(DeviceCapability.AP)
        if (lower.contains("phone") || lower.contains("voip")) caps.add(DeviceCapability.PHONE)
        if (caps.isEmpty()) caps.add(DeviceCapability.OTHER)
        return caps
    }

    private class LinkBudget(
        private val maxLinks: Int,
        private val truncationReasons: MutableSet<TopologyTruncationReason>
    ) {
        private var admitted = 0

        @Synchronized
        fun tryReserve(
            table: TopologyDataTable,
            tableObservations: TableObservationCollector,
        ): Boolean {
            if (admitted >= maxLinks) {
                truncationReasons.add(TopologyTruncationReason.LINK_LIMIT)
                tableObservations.recordTruncation(table)
                return false
            }
            admitted++
            return true
        }
    }

    private class CloseOnceSnmpClient(private val delegate: SnmpClient) : SnmpClient {
        private val closed = AtomicBoolean(false)

        override suspend fun get(target: SnmpTarget, oid: String): String? = delegate.get(target, oid)

        override suspend fun walk(
            target: SnmpTarget,
            oidPrefix: String,
            budget: SnmpWalkBudget
        ): SnmpWalkResult = delegate.walk(target, oidPrefix, budget)

        override fun close() {
            if (closed.compareAndSet(false, true)) delegate.close()
        }
    }

    internal class GraphByteBudget(private val maxBytes: Int) {
        private var retainedBytes = 0L

        @Synchronized
        fun tryReserve(node: TopologyNode): Boolean = reserve(estimateBytes(node))

        @Synchronized
        fun tryReserve(link: TopologyLink): Boolean = reserve(estimateBytes(link))

        private fun reserve(bytes: Long): Boolean {
            if (bytes > maxBytes - retainedBytes) return false
            retainedBytes += bytes
            return true
        }

        companion object {
            internal fun estimateBytes(node: TopologyNode): Long =
                128L + stringBytes(node.ip) + stringBytes(node.sysName) +
                    stringBytes(node.sysDescr) + stringBytes(node.vendor) + stringBytes(node.model) +
                    stringBytes(node.firmwareVersion) + stringBytes(node.sysLocation) + stringBytes(node.uptimeHuman) +
                    // Reserve room for every typed failure marker because graph-level
                    // link-budget checks may add TRUNCATED after the base node is built.
                    node.tableObservations.size * (24L + TopologyTableFailure.entries.size * 8L) +
                    node.interfaces.sumOf { 64L + stringBytes(it.name) + stringBytes(it.macAddress) } +
                    node.vlans.sumOf { 48L + stringBytes(it.name) }

            internal fun estimateBytes(link: TopologyLink): Long =
                96L + stringBytes(link.fromIp) + stringBytes(link.fromPort) +
                    stringBytes(link.toIp) + stringBytes(link.toPort) + stringBytes(link.neighbourSysName)

            private fun stringBytes(value: String?): Long = value?.length?.toLong()?.times(3L) ?: 0L
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
