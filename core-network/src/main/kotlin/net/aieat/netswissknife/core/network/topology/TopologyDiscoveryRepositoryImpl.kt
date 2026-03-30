package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.LinkedList

class TopologyDiscoveryRepositoryImpl(
    private val snmpClient: SnmpClient = Snmp4jClientImpl()
) : TopologyDiscoveryRepository {

    override fun discover(params: TopologyParams): Flow<TopologyDiscoveryEvent> = flow {
        try {
            val visited = mutableSetOf<String>()
            val queue = LinkedList<Pair<String, Int>>() // ip to hop depth
            queue.add(params.targetIp to 0)

            val allNodes = mutableListOf<TopologyNode>()
            val allLinks = mutableListOf<TopologyLink>()

            while (queue.isNotEmpty()) {
                val (currentIp, currentHop) = queue.poll()
                if (currentIp in visited) continue
                visited.add(currentIp)

                emit(TopologyDiscoveryEvent.Progress("Querying $currentIp...", allNodes.size))

                val target = SnmpTarget(ip = currentIp, params = params)

                // Query system info
                val sysDescr = safeGet(snmpClient, target, "1.3.6.1.2.1.1.1.0")
                val sysName = safeGet(snmpClient, target, "1.3.6.1.2.1.1.5.0")
                val sysLocation = safeGet(snmpClient, target, "1.3.6.1.2.1.1.6.0")
                val sysUpTimeStr = safeGet(snmpClient, target, "1.3.6.1.2.1.1.3.0")

                val snmpReachable = sysDescr != null || sysName != null
                val uptimeHuman = sysUpTimeStr?.toLongOrNull()?.let {
                    TopologyNodeParser.timeticksToHuman(it)
                }

                val vendor = TopologyNodeParser.parseVendor(sysDescr ?: "")
                val model = TopologyNodeParser.parseModel(sysDescr, null)
                val firmware = TopologyNodeParser.parseFirmwareVersion(sysDescr, null)

                // Query interfaces
                val interfaces = queryInterfaces(snmpClient, target)

                // Query VLANs
                val vlans = queryVlans(snmpClient, target)

                // Query LLDP neighbours
                val (lldpLinks, lldpNeighbourIps) = queryLldpNeighbours(
                    snmpClient, target, currentIp, currentHop, params.maxHops
                )

                // Query CDP neighbours
                val (cdpLinks, cdpNeighbourIps) = queryCdpNeighbours(
                    snmpClient, target, currentIp, currentHop, params.maxHops
                )

                // Determine capabilities from system description
                val capabilities = inferCapabilities(sysDescr, vendor)

                val node = TopologyNode(
                    ip = currentIp,
                    sysName = sysName,
                    sysDescr = sysDescr,
                    vendor = vendor,
                    model = model,
                    firmwareVersion = firmware,
                    sysLocation = sysLocation,
                    uptimeHuman = uptimeHuman,
                    capabilities = capabilities,
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

                // Add new neighbours to queue
                val newNeighbours = (lldpNeighbourIps + cdpNeighbourIps)
                    .filter { it.isNotBlank() && it !in visited }
                    .toSet()

                for (neighbourIp in newNeighbours) {
                    queue.add(neighbourIp to currentHop + 1)
                }
            }

            emit(TopologyDiscoveryEvent.Complete(
                TopologyGraph(
                    nodes = allNodes,
                    links = allLinks,
                    seedIp = params.targetIp,
                    queriedAt = System.currentTimeMillis()
                )
            ))
        } catch (e: Exception) {
            emit(TopologyDiscoveryEvent.Error(e.message ?: "Unknown SNMP error"))
        }
    }

    private suspend fun safeGet(client: SnmpClient, target: SnmpTarget, oid: String): String? =
        try { client.get(target, oid) } catch (e: Exception) { null }

    private suspend fun queryInterfaces(
        client: SnmpClient,
        target: SnmpTarget
    ): List<SnmpInterface> {
        val descrWalk = try { client.walk(target, "1.3.6.1.2.1.2.2.1.2") } catch (e: Exception) { emptyMap() }
        val speedWalk = try { client.walk(target, "1.3.6.1.2.1.2.2.1.5") } catch (e: Exception) { emptyMap() }
        val highSpeedWalk = try { client.walk(target, "1.3.6.1.2.1.31.1.1.1.15") } catch (e: Exception) { emptyMap() }
        val statusWalk = try { client.walk(target, "1.3.6.1.2.1.2.2.1.8") } catch (e: Exception) { emptyMap() }
        val macWalk = try { client.walk(target, "1.3.6.1.2.1.2.2.1.6") } catch (e: Exception) { emptyMap() }

        return descrWalk.entries.mapNotNull { (oid, name) ->
            val idx = oid.substringAfterLast(".").toIntOrNull() ?: return@mapNotNull null
            val statusVal = statusWalk["1.3.6.1.2.1.2.2.1.8.$idx"]?.toIntOrNull()
            val status = when (statusVal) {
                1 -> InterfaceStatus.UP
                2 -> InterfaceStatus.DOWN
                else -> InterfaceStatus.UNKNOWN
            }
            val highSpeedStr = highSpeedWalk["1.3.6.1.2.1.31.1.1.1.15.$idx"]
            val speedBps = if (highSpeedStr != null) {
                highSpeedStr.toLongOrNull()?.let { TopologyNodeParser.ifHighSpeedToSpeedBps(it) }
            } else {
                speedWalk["1.3.6.1.2.1.2.2.1.5.$idx"]?.toLongOrNull()
            }
            val macStr = macWalk["1.3.6.1.2.1.2.2.1.6.$idx"]
            SnmpInterface(
                index = idx,
                name = name,
                macAddress = macStr,
                speedBps = speedBps,
                operStatus = status
            )
        }
    }

    private suspend fun queryVlans(client: SnmpClient, target: SnmpTarget): List<VlanInfo> {
        val vlans = mutableListOf<VlanInfo>()

        // Try Cisco VTP MIB first
        val vtpWalk = try { client.walk(target, "1.3.6.1.4.1.9.9.46.1.3.1.1.4") } catch (e: Exception) { emptyMap() }
        val vtpStateWalk = try { client.walk(target, "1.3.6.1.4.1.9.9.46.1.3.1.1.2") } catch (e: Exception) { emptyMap() }

        vtpWalk.entries.forEach { (oid, name) ->
            val vlanId = oid.substringAfterLast(".").toIntOrNull() ?: return@forEach
            val stateVal = vtpStateWalk["1.3.6.1.4.1.9.9.46.1.3.1.1.2.$vlanId"]?.toIntOrNull()
            vlans.add(VlanInfo(id = vlanId, name = name, active = stateVal == 1))
        }

        // Also try 802.1Q standard MIB
        if (vlans.isEmpty()) {
            val dot1qWalk = try { client.walk(target, "1.3.6.1.2.1.17.7.1.4.3.1.1") } catch (e: Exception) { emptyMap() }
            dot1qWalk.entries.forEach { (oid, name) ->
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
        val lldpWalk = try { client.walk(target, "1.0.8802.1.1.2.1.4") } catch (e: Exception) { emptyMap() }
        if (lldpWalk.isEmpty()) return Pair(emptyList(), emptyList())

        // Parse neighbour entries - key columns by localPortNum
        // OID structure: 1.0.8802.1.1.2.1.4.1.1.<column>.<timeMark>.<localPort>.<remIndex>
        val neighbourMap = mutableMapOf<String, MutableMap<Int, String>>()

        for ((oid, value) in lldpWalk) {
            val parts = oid.split(".")
            // lldpRemTable OID: 1.0.8802.1.1.2.1.4.1.1.<col>.<timeMark>.<localPort>.<remIndex>
            // Indices:          0 1 2    3 4 5 6 7 8 9 10    11         12         13
            if (parts.size >= 14 && parts[7] == "4" && parts[8] == "1" && parts[9] == "1") {
                val column = parts[10].toIntOrNull() ?: continue
                val localPort = parts[12]
                val remIndex = parts[13]
                val key = "$localPort.$remIndex"
                neighbourMap.getOrPut(key) { mutableMapOf() }[column] = value
            }
        }

        // Parse management addresses for neighbour IPs
        // 1.0.8802.1.1.2.1.4.2.1.4.<timeMark>.<localPort>.<remIndex>.<addrType>.<addr...>
        // Indices: 0 1 2    3 4 5 6 7 8 9 10   11          12         13
        val mgmtAddrMap = mutableMapOf<String, String>()
        for ((oid, value) in lldpWalk) {
            val parts = oid.split(".")
            if (parts.size >= 14 && parts[7] == "4" && parts[8] == "2" && parts[9] == "1" && parts[10] == "4") {
                // key is localPort.remIndex
                val localPort = parts[12]
                val remIndex = parts[13]
                val key = "$localPort.$remIndex"
                // value is the management IP string
                if (value.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
                    mgmtAddrMap[key] = value
                }
            }
        }

        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()

        for ((key, columns) in neighbourMap) {
            val toSysName = columns[9]
            val fromPort = columns[7] // lldpRemPortId
            val neighbourIp = mgmtAddrMap[key] ?: continue

            links.add(TopologyLink(
                fromIp = fromIp,
                fromPort = fromPort,
                toIp = neighbourIp,
                toPort = null,
                protocol = LinkProtocol.LLDP,
                neighbourSysName = toSysName
            ))

            if (currentHop < maxHops) {
                neighbourIps.add(neighbourIp)
            }
        }

        return Pair(links, neighbourIps)
    }

    private suspend fun queryCdpNeighbours(
        client: SnmpClient,
        target: SnmpTarget,
        fromIp: String,
        currentHop: Int,
        maxHops: Int
    ): Pair<List<TopologyLink>, List<String>> {
        val cdpWalk = try { client.walk(target, "1.3.6.1.4.1.9.9.23.1.2.1") } catch (e: Exception) { emptyMap() }
        if (cdpWalk.isEmpty()) return Pair(emptyList(), emptyList())

        // CDP cache table OID: 1.3.6.1.4.1.9.9.23.1.2.1.1.<col>.<ifIndex>.<neighbourIndex>
        // col: 4=address, 5=version, 6=deviceId, 7=port, 8=platform, 9=capabilities
        val entryMap = mutableMapOf<String, MutableMap<Int, String>>()

        for ((oid, value) in cdpWalk) {
            val parts = oid.split(".")
            // prefix 1.3.6.1.4.1.9.9.23.1.2.1.1 = indices 0..11, then col at 12, ifIndex at 13, neighIdx at 14
            if (parts.size >= 15 && parts[11] == "1") {
                val column = parts[12].toIntOrNull() ?: continue
                val ifIndex = parts[13]
                val neighIdx = parts[14]
                val key = "$ifIndex.$neighIdx"
                entryMap.getOrPut(key) { mutableMapOf() }[column] = value
            }
        }

        val links = mutableListOf<TopologyLink>()
        val neighbourIps = mutableListOf<String>()

        for ((_, columns) in entryMap) {
            val neighbourIp = columns[4] ?: continue
            // CDP address may be in hex or dotted notation
            val parsedIp = parseCdpAddress(neighbourIp) ?: continue
            val deviceId = columns[6]
            val toPort = columns[7]

            links.add(TopologyLink(
                fromIp = fromIp,
                fromPort = null,
                toIp = parsedIp,
                toPort = toPort,
                protocol = LinkProtocol.CDP,
                neighbourSysName = deviceId
            ))

            if (currentHop < maxHops) {
                neighbourIps.add(parsedIp)
            }
        }

        return Pair(links, neighbourIps)
    }

    private fun parseCdpAddress(raw: String): String? {
        // CDP addresses may be in format "AA BB CC DD" (hex) or "192.168.1.1"
        if (raw.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) return raw
        return null
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
