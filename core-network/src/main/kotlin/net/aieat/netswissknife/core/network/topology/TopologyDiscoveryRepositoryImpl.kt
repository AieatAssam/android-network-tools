package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.aieat.netswissknife.core.network.HostValidator
import java.util.LinkedList

class TopologyDiscoveryRepositoryImpl(
    private val snmpClientFactory: SnmpClientFactory = SnmpClientFactory { Snmp4jClientImpl(it) }
) : TopologyDiscoveryRepository {

    constructor(client: SnmpClient) : this(SnmpClientFactory { client })

    override fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent> = flow {
        try {
            val normalizedTarget = HostValidator.normalize(params.targetIp) ?: params.targetIp
            val effectiveParams = params.copy(targetIp = normalizedTarget)
            snmpClientFactory.create(effectiveParams).use { snmpClient ->
                val visited = mutableSetOf<String>()
                val queue = LinkedList<Pair<String, Int>>() // ip to hop depth
                queue.add(effectiveParams.targetIp to 0)

                val allNodes = mutableListOf<TopologyNode>()
                val allLinks = mutableListOf<TopologyLink>()

                while (queue.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (currentIp, currentHop) = queue.poll()
                    if (currentIp in visited) continue
                    visited.add(currentIp)

                    emit(TopologyDiscoveryEvent.Progress("Querying $currentIp...", allNodes.size))

                    val target = SnmpTarget(ip = currentIp, params = effectiveParams)

                    val sysDescrAttempt = attemptGet(snmpClient, target, "1.3.6.1.2.1.1.1.0")
                    val sysNameAttempt = attemptGet(snmpClient, target, "1.3.6.1.2.1.1.5.0")
                    val sysDescr = sysDescrAttempt.value
                    val sysName = sysNameAttempt.value
                    val systemFailure = sysDescrAttempt.error ?: sysNameAttempt.error
                    if (sysDescr == null && sysName == null && systemFailure != null) {
                        if (currentIp == effectiveParams.targetIp) {
                            emit(TopologyDiscoveryEvent.Error(SnmpErrorFormatter.describe(systemFailure)))
                            return@flow
                        }
                    }
                    val sysLocation = safeGet(snmpClient, target, "1.3.6.1.2.1.1.6.0")
                    val sysUpTimeStr = safeGet(snmpClient, target, "1.3.6.1.2.1.1.3.0")

                    val snmpReachable = sysDescr != null || sysName != null
                    val uptimeHuman = sysUpTimeStr?.toLongOrNull()?.let {
                        TopologyNodeParser.timeticksToHuman(it)
                    }

                    val vendor = TopologyNodeParser.parseVendor(sysDescr ?: "")
                    val model = TopologyNodeParser.parseModel(sysDescr, null)
                    val firmware = TopologyNodeParser.parseFirmwareVersion(sysDescr, null)
                    val interfaces = queryInterfaces(snmpClient, target)
                    val vlans = queryVlans(snmpClient, target)

                    val (lldpLinks, lldpNeighbourIps) = queryLldpNeighbours(
                        snmpClient, target, currentIp, currentHop, params.maxHops
                    )
                    val (cdpLinks, cdpNeighbourIps) = queryCdpNeighbours(
                        snmpClient, target, currentIp, currentHop, params.maxHops
                    )

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

                    allNodes.add(node)
                    emit(TopologyDiscoveryEvent.NodeDiscovered(node))

                    for (link in lldpLinks + cdpLinks) {
                        allLinks.add(link)
                        emit(TopologyDiscoveryEvent.LinkDiscovered(link))
                    }

                    (lldpNeighbourIps + cdpNeighbourIps)
                        .filter { it.isNotBlank() && it !in visited }
                        .toSet()
                        .forEach { neighbourIp -> queue.add(neighbourIp to currentHop + 1) }
                }

                emit(
                    TopologyDiscoveryEvent.Complete(
                        TopologyGraph(
                            nodes = allNodes,
                            links = allLinks,
                            seedIp = effectiveParams.targetIp,
                            queriedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(TopologyDiscoveryEvent.Error(SnmpErrorFormatter.describe(e)))
        }
    }

    private data class GetAttempt(val value: String?, val error: Exception?)

    private suspend fun attemptGet(client: SnmpClient, target: SnmpTarget, oid: String): GetAttempt =
        try {
            GetAttempt(client.get(target, oid), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GetAttempt(null, e)
        }

    private suspend fun safeGet(client: SnmpClient, target: SnmpTarget, oid: String): String? =
        attemptGet(client, target, oid).value

    private suspend fun safeWalk(client: SnmpClient, target: SnmpTarget, oid: String): Map<String, String> =
        try {
            client.walk(target, oid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }

    private suspend fun queryInterfaces(client: SnmpClient, target: SnmpTarget): List<SnmpInterface> {
        val descrWalk = safeWalk(client, target, "1.3.6.1.2.1.2.2.1.2")
        val speedWalk = safeWalk(client, target, "1.3.6.1.2.1.2.2.1.5")
        val highSpeedWalk = safeWalk(client, target, "1.3.6.1.2.1.31.1.1.1.15")
        val statusWalk = safeWalk(client, target, "1.3.6.1.2.1.2.2.1.8")
        val macWalk = safeWalk(client, target, "1.3.6.1.2.1.2.2.1.6")

        return descrWalk.entries.mapNotNull { (oid, name) ->
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
        }
    }

    private suspend fun queryVlans(client: SnmpClient, target: SnmpTarget): List<VlanInfo> {
        val vlans = mutableListOf<VlanInfo>()
        val vtpWalk = safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.4")
        val vtpStateWalk = safeWalk(client, target, "1.3.6.1.4.1.9.9.46.1.3.1.1.2")

        vtpWalk.forEach { (oid, name) ->
            val vlanId = oid.substringAfterLast(".").toIntOrNull() ?: return@forEach
            val stateVal = vtpStateWalk["1.3.6.1.4.1.9.9.46.1.3.1.1.2.$vlanId"]?.toIntOrNull()
            vlans.add(VlanInfo(id = vlanId, name = name, active = stateVal == 1))
        }

        if (vlans.isEmpty()) {
            safeWalk(client, target, "1.3.6.1.2.1.17.7.1.4.3.1.1").forEach { (oid, name) ->
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
        maxHops: Int
    ): Pair<List<TopologyLink>, List<String>> {
        val lldpWalk = safeWalk(client, target, "1.0.8802.1.1.2.1.4")
        if (lldpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val remoteEntries = TopologyMibParser.parseLldpRemTable(lldpWalk)
        val managementAddresses = TopologyMibParser.parseLldpManAddrTable(lldpWalk).ipv4
        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()

        remoteEntries.forEach { entry ->
            val neighbourIp = managementAddresses[entry.key] ?: return@forEach
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
        maxHops: Int
    ): Pair<List<TopologyLink>, List<String>> {
        val cdpWalk = safeWalk(client, target, "1.3.6.1.4.1.9.9.23.1.2.1")
        if (cdpWalk.isEmpty()) return emptyList<TopologyLink>() to emptyList()

        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()
        TopologyMibParser.parseCdpCache(cdpWalk).forEach { entry ->
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
}
