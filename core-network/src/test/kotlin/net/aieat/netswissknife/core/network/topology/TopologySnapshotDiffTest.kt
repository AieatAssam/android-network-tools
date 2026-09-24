package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologySnapshotDiffTest {

    @Test
    fun `complete observations report changed sysName interface and VLAN independently`() {
        val before = graph(node(
            name = "old-name",
            interfaces = listOf(iface(1, "eth0")),
            vlans = listOf(VlanInfo(10, "users", active = true))
        ))
        val after = graph(node(
            name = "new-name",
            interfaces = listOf(iface(1, "uplink")),
            vlans = listOf(VlanInfo(10, "staff", active = true))
        ))

        val diff = TopologySnapshotDiffer.compare(before, after)

        assertEquals(setOf(TopologyNodeField.SYS_NAME), diff.nodeChanges.single().changedFields)
        assertEquals(
            TopologyChangeKind.CHANGED,
            diff.changesFor(TopologyDataTable.INTERFACES).interfaceChanges.single().kind
        )
        assertEquals(
            TopologyChangeKind.CHANGED,
            diff.changesFor(TopologyDataTable.VLANS).vlanChanges.single().kind
        )
        assertEquals(
            TopologyComparisonStatus.COMPARABLE,
            diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).status
        )
    }

    @Test
    fun `partial missing and failed table evidence never reports removals`() {
        val before = graph(node(
            interfaces = listOf(iface(1), iface(2)),
            vlans = listOf(VlanInfo(10, "users", true))
        ))
        val after = graph(node(
            interfaces = listOf(iface(1)),
            vlans = emptyList(),
            observations = completeObservations().toMutableMap().apply {
                put(TopologyDataTable.INTERFACES, observation(TopologyTableCompleteness.PARTIAL))
                remove(TopologyDataTable.VLANS)
                put(TopologyDataTable.CDP_NEIGHBORS, observation(TopologyTableCompleteness.FAILED))
            }
        ))

        val diff = TopologySnapshotDiffer.compare(before, after)

        assertEquals(TopologyComparisonStatus.INCOMPARABLE,
            diff.changesFor(TopologyDataTable.INTERFACES).status)
        assertTrue(diff.changesFor(TopologyDataTable.INTERFACES).interfaceChanges.isEmpty())
        assertTrue(diff.changesFor(TopologyDataTable.VLANS).vlanChanges.isEmpty())
        assertEquals(setOf(TopologyIncomparabilityReason.MISSING_TABLE_OBSERVATION),
            diff.changesFor(TopologyDataTable.VLANS).reasons)
        assertEquals(setOf(TopologyIncomparabilityReason.FAILED_TABLE_OBSERVATION),
            diff.changesFor(TopologyDataTable.CDP_NEIGHBORS).reasons)
        assertEquals(TopologyComparisonStatus.COMPARABLE,
            diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).status)
    }

    @Test
    fun `table completeness is independent and scoped truncation only invalidates its table`() {
        val before = graph(node(
            interfaces = listOf(iface(1), iface(2)),
            vlans = listOf(VlanInfo(10, "users", true))
        ))
        val after = graph(node(
            interfaces = listOf(iface(1)),
            vlans = listOf(VlanInfo(10, "users", true)),
            observations = completeObservations().toMutableMap().apply {
                put(TopologyDataTable.INTERFACES, observation(TopologyTableCompleteness.PARTIAL))
            }
        ), truncation = setOf(TopologyTruncationReason.INTERFACE_LIMIT))

        val diff = TopologySnapshotDiffer.compare(before, after)

        assertEquals(TopologyComparisonStatus.INCOMPARABLE,
            diff.changesFor(TopologyDataTable.INTERFACES).status)
        assertEquals(TopologyComparisonStatus.COMPARABLE,
            diff.changesFor(TopologyDataTable.VLANS).status)
        assertEquals(TopologyComparisonStatus.COMPARABLE,
            diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).status)
    }

    @Test
    fun `graph-wide truncation and SNMP errors conservatively suppress absences`() {
        val before = graph(node(interfaces = listOf(iface(1))))
        val truncated = graph(node(interfaces = emptyList()),
            truncation = setOf(TopologyTruncationReason.GRAPH_BYTE_LIMIT))
        val snmpError = graph(node(interfaces = emptyList()), hadSnmpErrors = true)

        listOf(truncated, snmpError).forEach { after ->
            val diff = TopologySnapshotDiffer.compare(before, after)
            assertEquals(TopologyComparisonStatus.INCOMPARABLE,
                diff.changesFor(TopologyDataTable.INTERFACES).status)
            assertTrue(diff.changesFor(TopologyDataTable.INTERFACES).interfaceChanges.isEmpty())
            assertTrue(diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges.isEmpty())
        }
    }

    @Test
    fun `links are protocol-specific deterministic multisets and removals require complete tables`() {
        val lldp = link(LinkProtocol.LLDP)
        val cdp = link(LinkProtocol.CDP)
        val before = graph(node(), links = listOf(cdp, lldp, lldp))
        val after = graph(node(), links = listOf(lldp, cdp))

        val diff = TopologySnapshotDiffer.compare(before, after)

        val lldpChanges = diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges
        assertEquals(1, lldpChanges.size)
        assertEquals(TopologyChangeKind.REMOVED, lldpChanges.single().kind)
        assertEquals(2, lldpChanges.single().occurrence)
        assertTrue(diff.changesFor(TopologyDataTable.CDP_NEIGHBORS).linkChanges.isEmpty())

        val reordered = graph(node(), links = listOf(lldp, cdp, lldp))
        assertEquals(
            TopologySnapshot.from(before).links,
            TopologySnapshot.from(reordered).links
        )
    }

    @Test
    fun `duplicate links remain counted and neighbor sysName changes update the same edge`() {
        val oldPeer = link(LinkProtocol.LLDP)
        val renamedPeer = oldPeer.copy(neighbourSysName = "peer-renamed")
        val before = graph(node(), links = listOf(oldPeer, oldPeer))
        val after = graph(node(), links = listOf(oldPeer, renamedPeer))

        val changes = TopologySnapshotDiffer.compare(before, after)
            .changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges

        assertEquals(1, changes.size)
        assertEquals(TopologyChangeKind.CHANGED, changes.single().kind)
        assertEquals(oldPeer.neighbourSysName, changes.single().before?.neighbourSysName)
        assertEquals(renamedPeer.neighbourSysName, changes.single().link.neighbourSysName)
        assertEquals(2, changes.single().occurrence)
    }

    @Test
    fun `complete LLDP to CDP observation is a protocol change on the same endpoint`() {
        val oldLldp = link(LinkProtocol.LLDP)
        val newCdp = link(LinkProtocol.CDP)
        val diff = TopologySnapshotDiffer.compare(
            graph(node(), links = listOf(oldLldp, oldLldp)),
            graph(node(), links = listOf(newCdp, newCdp))
        )

        assertTrue(diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges.isEmpty())
        val changes = diff.changesFor(TopologyDataTable.CDP_NEIGHBORS).linkChanges
        assertEquals(listOf(1, 2), changes.map { it.occurrence })
        assertTrue(changes.all { it.kind == TopologyChangeKind.CHANGED })
        assertTrue(changes.all { it.before?.protocol == LinkProtocol.LLDP })
        assertTrue(changes.all { it.link.protocol == LinkProtocol.CDP })
    }

    @Test
    fun `incomplete target protocol suppresses unsupported protocol transition removal`() {
        val oldLldp = link(LinkProtocol.LLDP)
        val newCdp = link(LinkProtocol.CDP)
        val afterNode = node(observations = completeObservations().toMutableMap().apply {
            put(TopologyDataTable.CDP_NEIGHBORS, observation(TopologyTableCompleteness.PARTIAL))
        })

        val diff = TopologySnapshotDiffer.compare(
            graph(node(), links = listOf(oldLldp)),
            graph(afterNode, links = listOf(newCdp))
        )

        assertEquals(TopologyComparisonStatus.INCOMPARABLE,
            diff.changesFor(TopologyDataTable.CDP_NEIGHBORS).status)
        assertTrue(diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges.isEmpty())
        assertTrue(diff.changesFor(TopologyDataTable.CDP_NEIGHBORS).linkChanges.isEmpty())
    }

    @Test
    fun `link without source node table observation cannot prove a removal`() {
        val oldLink = link(LinkProtocol.LLDP)
        val diff = TopologySnapshotDiffer.compare(
            graph(links = listOf(oldLink)),
            graph()
        )

        assertEquals(TopologyComparisonStatus.INCOMPARABLE,
            diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).status)
        assertTrue(diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).linkChanges.isEmpty())
        assertTrue(diff.changesFor(TopologyDataTable.LLDP_NEIGHBORS).reasons
            .contains(TopologyIncomparabilityReason.MISSING_TABLE_OBSERVATION))
    }

    @Test
    fun `duplicate IP identity conflict is kept unknown and cannot create a removal`() {
        val before = graph(node(name = "switch-a", interfaces = listOf(iface(1))))
        val conflictingAfter = graph(
            node(name = "switch-b"),
            extraNodes = listOf(node(name = "switch-c"))
        )

        val diff = TopologySnapshotDiffer.compare(before, conflictingAfter)

        assertTrue(diff.unknownNodeAbsences.isEmpty())
        assertTrue(diff.changesFor(TopologyDataTable.INTERFACES).interfaceChanges.isEmpty())
        assertTrue(diff.changesFor(TopologyDataTable.INTERFACES).reasons
            .contains(TopologyIncomparabilityReason.CONFLICTING_NODE_IDENTITY))
        assertFalse(TopologySnapshot.from(conflictingAfter).conflictingNodeIps.isEmpty())
    }

    @Test
    fun `disjoint complete interface MAC evidence flags a reused node identity`() {
        val old = node(interfaces = listOf(iface(1, mac = "00:11:22:33:44:55")))
        val replacement = node(
            name = "replacement",
            interfaces = listOf(iface(1, mac = "00:aa:bb:cc:dd:ee"))
        )

        val diff = TopologySnapshotDiffer.compare(graph(old), graph(replacement))

        assertEquals(setOf("10.0.0.1"), diff.reusedNodeIdentities)
        assertTrue(diff.nodeChanges.isEmpty())
        assertTrue(TopologyDataTable.entries.all { table ->
            diff.changesFor(table).status == TopologyComparisonStatus.INCOMPARABLE
        })
        assertTrue(TopologyDataTable.entries.all { table ->
            diff.changesFor(table).interfaceChanges.isEmpty() &&
                diff.changesFor(table).vlanChanges.isEmpty() &&
                diff.changesFor(table).linkChanges.isEmpty()
        })
    }

    @Test
    fun `snapshot normalizes identity orders rows and includes no credentials`() {
        val source = graph(node(
            ip = " 10.0.0.1 ",
            interfaces = listOf(iface(2), iface(1)),
            vlans = listOf(VlanInfo(20, "b", true), VlanInfo(10, "a", true))
        )).copy(seedIp = " 10.0.0.1 ")

        val snapshot = TopologySnapshot.from(source)

        assertEquals("10.0.0.1", snapshot.seedIp)
        assertEquals("10.0.0.1", snapshot.nodes.single().ip)
        assertEquals(listOf(1, 2), snapshot.nodes.single().interfaces.map { it.index })
        assertEquals(listOf(10, 20), snapshot.nodes.single().vlans.map { it.id })
        assertFalse(snapshot.toString().contains("password", ignoreCase = true))
        assertFalse(snapshot.toString().contains("community", ignoreCase = true))
    }

    @Test
    fun `snapshot canonicalizes numeric IP literals without resolving hostnames`() {
        val before = graph(
            node(ip = "192.168.001.001"),
            node(ip = "2001:0db8:0000:0000:0000:0000:0000:0001")
        )
        val after = graph(node(ip = "192.168.1.1"), node(ip = "2001:db8::1"))
        val beforeSnapshot = TopologySnapshot.from(before)
        val afterSnapshot = TopologySnapshot.from(after)

        assertEquals(beforeSnapshot.nodes.map { it.ip }, afterSnapshot.nodes.map { it.ip })
        assertTrue(beforeSnapshot.nodes.map { it.ip }.contains("192.168.1.1"))
        assertTrue(beforeSnapshot.nodes.map { it.ip }.contains("2001:db8::1"))
        assertEquals(
            "router.example",
            TopologySnapshot.from(graph(node(ip = "Router.Example"))).nodes.single().ip
        )
        assertEquals(
            "fe80::1%En0",
            TopologySnapshot.from(graph(node(ip = "fe80:0:0:0:0:0:0:1%En0"))).nodes.single().ip
        )
    }

    @Test
    fun `direct snapshot comparison detects conflicting duplicate IP records independent of order`() {
        val baseline = TopologySnapshot.from(graph(node(name = "switch-a")))
        val first = baseline.nodes.single()
        val second = first.copy(sysName = "switch-b")
        val conflictA = baseline.copy(nodes = listOf(first, second))
        val conflictB = baseline.copy(nodes = listOf(second, first))

        val diffA = TopologySnapshotDiffer.compare(baseline, conflictA)
        val diffB = TopologySnapshotDiffer.compare(baseline, conflictB)

        assertEquals(diffA, diffB)
        assertTrue(diffA.nodeChanges.isEmpty())
        assertTrue(diffA.changesFor(TopologyDataTable.INTERFACES).reasons
            .contains(TopologyIncomparabilityReason.CONFLICTING_NODE_IDENTITY))
        assertTrue(diffA.changesFor(TopologyDataTable.INTERFACES).interfaceChanges.isEmpty())
    }

    @Test
    fun `full graph diff is stable when node link interface and VLAN lists are permuted`() {
        val nodeA = node(
            ip = "10.0.0.1",
            name = "switch-a",
            interfaces = listOf(iface(1), iface(2)),
            vlans = listOf(VlanInfo(10, "users", true), VlanInfo(20, "voice", true))
        )
        val nodeB = node(
            ip = "10.0.0.2",
            name = "switch-b",
            interfaces = listOf(iface(1, "uplink"), iface(2, "downlink")),
            vlans = listOf(VlanInfo(30, "management", true), VlanInfo(40, "guest", true))
        )
        val lldp = TopologyLink("10.0.0.1", "eth1", "10.0.0.2", "uplink", LinkProtocol.LLDP, "switch-b")
        val cdp = TopologyLink("10.0.0.2", "uplink", "10.0.0.1", "eth1", LinkProtocol.CDP, "switch-a")
        val ordered = graph(nodeA, nodeB, links = listOf(lldp, cdp))
        val permuted = graph(
            nodeB.copy(interfaces = nodeB.interfaces.reversed(), vlans = nodeB.vlans.reversed()),
            nodeA.copy(interfaces = nodeA.interfaces.reversed(), vlans = nodeA.vlans.reversed()),
            links = listOf(cdp, lldp)
        )

        val forward = TopologySnapshotDiffer.compare(ordered, permuted)
        val reverse = TopologySnapshotDiffer.compare(permuted, ordered)

        assertEquals(forward, reverse)
        assertTrue(forward.nodeChanges.isEmpty())
        assertTrue(TopologyDataTable.entries.all { table ->
            val changes = forward.changesFor(table)
            changes.interfaceChanges.isEmpty() &&
                changes.vlanChanges.isEmpty() &&
                changes.linkChanges.isEmpty()
        })

        val changedNodeA = nodeA.copy(
            interfaces = listOf(iface(1, "port-a"), iface(2)),
            vlans = listOf(VlanInfo(10, "users-renamed", true), VlanInfo(20, "voice", true))
        )
        val addedNeighbor = TopologyLink(
            "10.0.0.1", "eth3", "10.0.0.3", "uplink", LinkProtocol.LLDP, "switch-c"
        )
        val changedCandidate = graph(changedNodeA, nodeB, links = listOf(lldp, cdp, addedNeighbor))
        val permutedCandidate = graph(
            nodeB.copy(interfaces = nodeB.interfaces.reversed(), vlans = nodeB.vlans.reversed()),
            changedNodeA.copy(
                interfaces = changedNodeA.interfaces.reversed(),
                vlans = changedNodeA.vlans.reversed()
            ),
            links = listOf(addedNeighbor, cdp, lldp)
        )

        val changedForward = TopologySnapshotDiffer.compare(ordered, changedCandidate)
        val changedPermuted = TopologySnapshotDiffer.compare(ordered, permutedCandidate)
        assertEquals(changedForward, changedPermuted)
        assertEquals(
            TopologyChangeKind.CHANGED,
            changedForward.changesFor(TopologyDataTable.INTERFACES)
                .interfaceChanges.single().kind
        )
        assertEquals(
            TopologyChangeKind.CHANGED,
            changedForward.changesFor(TopologyDataTable.VLANS).vlanChanges.single().kind
        )
        val addedLink = changedForward.changesFor(TopologyDataTable.LLDP_NEIGHBORS)
            .linkChanges.single()
        assertEquals(TopologyChangeKind.ADDED, addedLink.kind)
        assertEquals("10.0.0.3", addedLink.link.toIp)
    }

    @Test
    fun `malformed MAC values and equivalent separators do not signal identity reuse`() {
        val before = node(interfaces = listOf(iface(1, mac = "00:11:22:33:44:55")))
        val sameMac = node(interfaces = listOf(iface(1, mac = "0011.2233.4455")))
        val malformedOld = node(interfaces = listOf(iface(1, mac = "unknown")))
        val malformedNew = node(interfaces = listOf(iface(1, mac = "zz:11:22:33:44:55")))

        assertTrue(TopologySnapshotDiffer.compare(graph(before), graph(sameMac))
            .reusedNodeIdentities.isEmpty())
        assertTrue(TopologySnapshotDiffer.compare(graph(malformedOld), graph(malformedNew))
            .reusedNodeIdentities.isEmpty())
    }

    @Test
    fun `zero and broadcast MAC placeholders are ignored for reused identity evidence`() {
        val valid48Bit = "00:11:22:33:44:55"
        val valid64Bit = "00:11:22:33:44:55:66:77"
        val placeholders = listOf(
            "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff",
            "00:00:00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff:ff:ff"
        )
        placeholders.forEach { placeholder ->
            val old = node(interfaces = listOf(iface(1, mac = placeholder)))
            val new = node(interfaces = listOf(iface(1, mac = valid48Bit)))
            assertTrue(TopologySnapshotDiffer.compare(graph(old), graph(new))
                .reusedNodeIdentities.isEmpty())
        }
        val old64 = node(interfaces = listOf(iface(1, mac = "00-00-00-00-00-00-00-00")))
        val new64 = node(interfaces = listOf(iface(1, mac = valid64Bit)))
        assertTrue(TopologySnapshotDiffer.compare(graph(old64), graph(new64))
            .reusedNodeIdentities.isEmpty())
        val anotherValid64 = node(interfaces = listOf(iface(1, mac = "00:aa:bb:cc:dd:ee:ff:00")))
        assertEquals(setOf("10.0.0.1"), TopologySnapshotDiffer
            .compare(graph(new64), graph(anotherValid64)).reusedNodeIdentities)
    }

    private fun graph(
        vararg nodes: TopologyNode,
        links: List<TopologyLink> = emptyList(),
        truncation: Set<TopologyTruncationReason> = emptySet(),
        hadSnmpErrors: Boolean = false,
        extraNodes: List<TopologyNode> = emptyList()
    ) = TopologyGraph(
        nodes = nodes.toList() + extraNodes,
        links = links,
        seedIp = "10.0.0.1",
        queriedAt = 123,
        truncationReasons = truncation,
        hadSnmpErrors = hadSnmpErrors
    )

    private fun node(
        ip: String = "10.0.0.1",
        name: String? = null,
        interfaces: List<SnmpInterface> = emptyList(),
        vlans: List<VlanInfo> = emptyList(),
        observations: Map<TopologyDataTable, TopologyTableObservation> = completeObservations()
    ) = TopologyNode(
        ip = ip,
        sysName = name,
        sysDescr = null,
        vendor = null,
        model = null,
        firmwareVersion = null,
        sysLocation = null,
        uptimeHuman = null,
        capabilities = emptySet(),
        interfaces = interfaces,
        vlans = vlans,
        snmpReachable = true,
        tableObservations = observations
    )

    private fun iface(index: Int, name: String = "eth$index", mac: String? = null) =
        SnmpInterface(index, name, mac, null, InterfaceStatus.UP)

    private fun link(protocol: LinkProtocol) = TopologyLink(
        "10.0.0.1", "eth0", "10.0.0.2", "eth1", protocol, "peer"
    )

    private fun completeObservations() = TopologyDataTable.entries.associateWith {
        observation(TopologyTableCompleteness.COMPLETE)
    }

    private fun observation(completeness: TopologyTableCompleteness) =
        TopologyTableObservation(
            completeness,
            if (completeness == TopologyTableCompleteness.COMPLETE) emptySet()
            else setOf(TopologyTableFailure.REQUEST_FAILED)
        )
}
