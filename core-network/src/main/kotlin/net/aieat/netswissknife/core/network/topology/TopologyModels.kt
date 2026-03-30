package net.aieat.netswissknife.core.network.topology

enum class SnmpVersion { V1, V2C, V3 }
enum class V3AuthProtocol { MD5, SHA, NONE }
enum class V3PrivProtocol { DES, AES128, NONE }
enum class DeviceCapability { ROUTER, SWITCH, AP, PHONE, OTHER }
enum class InterfaceStatus { UP, DOWN, UNKNOWN }
enum class LinkProtocol { LLDP, CDP }

data class TopologyParams(
    val targetIp: String,
    val snmpVersion: SnmpVersion = SnmpVersion.V2C,
    val communityString: String = "public",
    val v3Username: String? = null,
    val v3AuthPassword: String? = null,
    val v3PrivPassword: String? = null,
    val v3AuthProtocol: V3AuthProtocol = V3AuthProtocol.NONE,
    val v3PrivProtocol: V3PrivProtocol = V3PrivProtocol.NONE,
    val maxHops: Int = 3,
    val timeoutMs: Int = 3000,
    val retries: Int = 1
)

data class SnmpInterface(
    val index: Int,
    val name: String,
    val macAddress: String?,
    val speedBps: Long?,
    val operStatus: InterfaceStatus
)

data class VlanInfo(val id: Int, val name: String, val active: Boolean)

data class TopologyNode(
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
    val snmpReachable: Boolean
)

data class TopologyLink(
    val fromIp: String,
    val fromPort: String?,
    val toIp: String,
    val toPort: String?,
    val protocol: LinkProtocol,
    val neighbourSysName: String?
)

data class TopologyGraph(
    val nodes: List<TopologyNode>,
    val links: List<TopologyLink>,
    val seedIp: String,
    val queriedAt: Long
)
