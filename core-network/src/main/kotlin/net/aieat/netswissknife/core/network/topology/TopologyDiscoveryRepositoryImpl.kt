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
import java.util.concurrent.atomic.AtomicBoolean

class TopologyDiscoveryRepositoryImpl(
    private val snmpClientFactory: SnmpClientFactory? = null,
    private val limits: TopologyResourceLimits = TopologyResourceLimits(),
    private val binder: NetworkBinder = NoOpNetworkBinder,
) : TopologyDiscoveryRepository {

    private val requestLimiter = Semaphore(MAX_CONCURRENT_SNMP_REQUESTS)
    private val effectiveSnmpClientFactory = snmpClientFactory
        ?: SnmpClientFactory { params -> Snmp4jClientImpl(params, binder) }

    constructor(client: SnmpClient) : this(SnmpClientFactory { client }, TopologyResourceLimits())
    constructor(client: SnmpClient, limits: TopologyResourceLimits) :
        this(SnmpClientFactory { client }, limits)

    // Snmp4jClientImpl starts a UDP transport while it is constructed. Keep the
    // factory, discovery calls, and client teardown off Android's main thread.
    override fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent> = channelFlow {
        // Keep session creation inside collection so re-collecting a cold Flow receives a
        // fresh one-shot session and an independent bounded deadline.
        discover(params, newSession()).collect { send(it) }
    }

    override fun discover(
        params: TopologyParams,
        session: OperationSession,
    ): Flow<TopologyDiscoveryEvent> = channelFlow {
        try {
            val normalizedTarget = HostValidator.normalize(params.targetIp) ?: params.targetIp
            val effectiveParams = params.copy(targetIp = normalizedTarget)
            val graph = OperationRunner.run(session) {
                currentCoroutineContext().ensureActive()
                val sessionRequestLimiter = Semaphore(
                    minOf(MAX_CONCURRENT_SNMP_REQUESTS, session.budget.maxConcurrentProbes)
                )
                val snmpClient = resources.register(
                    CloseOnceSnmpClient(effectiveSnmpClientFactory.create(effectiveParams))
                )
                val visited = mutableSetOf<String>()
                val queue = LinkedList<Pair<String, Int>>() // ip to hop depth
                queue.add(effectiveParams.targetIp to 0)
                val scheduledTargets = mutableSetOf(effectiveParams.targetIp)

                val allNodes = mutableListOf<TopologyNode>()
                val allLinks = mutableListOf<TopologyLink>()
                val truncationReasons = ConcurrentHashMap.newKeySet<TopologyTruncationReason>()
                val snmpErrors = AtomicBoolean(false)
                val linkBudget = LinkBudget(limits.maxLinks, truncationReasons)
                val graphBudget = GraphByteBudget(limits.maxBytesPerGraph)

                while (queue.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    if (allNodes.size >= limits.maxNodes) {
                        truncationReasons.add(TopologyTruncationReason.NODE_LIMIT)
                        break
                    }
                    val (currentIp, currentHop) = queue.poll()
                    if (currentIp in visited) continue
                    visited.add(currentIp)

                    send(TopologyDiscoveryEvent.Progress("Querying $currentIp...", allNodes.size))

                    val target = SnmpTarget(ip = currentIp, params = effectiveParams)
                    val walkBudget = SnmpWalkBudget(limits)

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
                            queryInterfaces(snmpClient, target, walkBudget, truncationReasons, snmpErrors, sessionRequestLimiter)
                        }
                        val vlans = async {
                            queryVlans(snmpClient, target, walkBudget, truncationReasons, snmpErrors, sessionRequestLimiter)
                        }
                        val lldp = async {
                            queryLldpNeighbours(
                                snmpClient, target, currentIp, currentHop, params.maxHops,
                                walkBudget, truncationReasons, snmpErrors, linkBudget, sessionRequestLimiter
                            )
                        }
                        val cdp = async {
                            queryCdpNeighbours(
                                snmpClient, target, currentIp, currentHop, params.maxHops,
                                walkBudget, truncationReasons, snmpErrors, linkBudget, sessionRequestLimiter
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
                        snmpReachable = snmpReachable
                    )

                    if (!graphBudget.tryReserve(node)) {
                        truncationReasons.add(TopologyTruncationReason.GRAPH_BYTE_LIMIT)
                        break
                    }
                    allNodes.add(node)
                    send(TopologyDiscoveryEvent.NodeDiscovered(node))

                    var graphByteLimitReached = false
                    for (link in lldpLinks + cdpLinks) {
                        if (!graphBudget.tryReserve(link)) {
                            truncationReasons.add(TopologyTruncationReason.GRAPH_BYTE_LIMIT)
                            graphByteLimitReached = true
                            break
                        }
                        allLinks.add(link)
                        send(TopologyDiscoveryEvent.LinkDiscovered(link))
                    }
                    if (graphByteLimitReached) break

                    (lldpNeighbourIps + cdpNeighbourIps).forEach { neighbourIp ->
                        if (neighbourIp.isBlank() || neighbourIp in scheduledTargets) return@forEach
                        if (allNodes.size + queue.size >= limits.maxNodes) {
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
                    hadSnmpErrors = snmpErrors.get()
                )
            }
            // OperationRunner closes the registered client before returning; a cleanup error
            // therefore prevents publication of the terminal Complete event.
            if (graph != null) send(TopologyDiscoveryEvent.Complete(graph))
        } catch (e: Exception) {
            if (session.cancellationReason == CancellationReason.DEADLINE_EXCEEDED ||
                e is OperationDeadlineExceededException
            ) {
                send(TopologyDiscoveryEvent.Error("Topology discovery timed out", e))
            } else {
                if (e is CancellationException) throw e
                send(TopologyDiscoveryEvent.Error(SnmpErrorFormatter.describe(e), e))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun newSession(): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.LOCAL_NETWORK,
            timeoutMillis = DEFAULT_OPERATION_TIMEOUT_MILLIS,
        )
    )

    private companion object {
        const val MAX_CONCURRENT_SNMP_REQUESTS = 4
        const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 120_000L
    }

    private data class GetAttempt(val value: String?, val error: Exception?)

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
    ): Map<String, String> =
        sessionRequestLimiter.withPermit {
            requestLimiter.withPermit {
                try {
                    val result = client.walk(target, oid, budget)
                    truncationReasons.addAll(result.truncationReasons)
                    if (result.hadError) snmpErrors.set(true)
                    result.entries
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.containsLocalNetworkPermissionDenied()) throw e
                    snmpErrors.set(true)
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
    ): List<SnmpInterface> {
        val (descrWalk, speedWalk, highSpeedWalk, statusWalk, macWalk) = coroutineScope {
            val descr = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.2", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            val speed = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.5", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            val highSpeed = async { safeWalk(client, target, "1.3.6.1.2.1.31.1.1.1.15", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            val status = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.8", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            val mac = async { safeWalk(client, target, "1.3.6.1.2.1.2.2.1.6", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
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
    ): List<VlanInfo> {
        val vlans = mutableListOf<VlanInfo>()
        val (vtpWalk, vtpStateWalk) = coroutineScope {
            val names = async { safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.4", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            val states = async { safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.2", budget, truncationReasons, snmpErrors, sessionRequestLimiter) }
            names.await() to states.await()
        }

        vtpWalk.forEach { (oid, name) ->
            if (vlans.size >= limits.maxVlansPerNode) {
                truncationReasons.add(TopologyTruncationReason.VLAN_LIMIT)
                return@forEach
            }
            val vlanId = oid.substringAfterLast(".").toIntOrNull() ?: return@forEach
            val stateVal = vtpStateWalk["1.3.6.1.4.1.9.9.46.1.3.1.1.2.$vlanId"]?.toIntOrNull()
            vlans.add(VlanInfo(id = vlanId, name = name, active = stateVal == 1))
        }

        if (vlans.isEmpty()) {
            safeWalk(client, target, "1.3.6.1.2.1.17.7.1.4.3.1.1", budget, truncationReasons, snmpErrors, sessionRequestLimiter).forEach { (oid, name) ->
                if (vlans.size >= limits.maxVlansPerNode) {
                    truncationReasons.add(TopologyTruncationReason.VLAN_LIMIT)
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
    ): Pair<List<TopologyLink>, List<String>> {
        val lldpWalk = safeWalk(client, target, "1.0.8802.1.1.2.1.4", budget, truncationReasons, snmpErrors, sessionRequestLimiter)
        if (lldpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val remoteEntries = TopologyMibParser.parseLldpRemTable(lldpWalk)
        val managementAddresses = TopologyMibParser.parseLldpManAddrTable(lldpWalk).ipv4
        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()

        remoteEntries.forEach { entry ->
            val neighbourIp = managementAddresses[entry.key] ?: return@forEach
            if (!linkBudget.tryReserve()) return@forEach
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
    ): Pair<List<TopologyLink>, List<String>> {
        val cdpWalk = safeWalk(client, target, "1.3.6.1.4.1.9.9.23.1.2.1", budget, truncationReasons, snmpErrors, sessionRequestLimiter)
        if (cdpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()
        TopologyMibParser.parseCdpCache(cdpWalk).forEach { entry ->
            if (!linkBudget.tryReserve()) return@forEach
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
        fun tryReserve(): Boolean {
            if (admitted >= maxLinks) {
                truncationReasons.add(TopologyTruncationReason.LINK_LIMIT)
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
