package net.aieat.netswissknife.core.network.topology

enum class SnmpVersion { V1, V2C, V3 }
enum class V3AuthProtocol { NONE, MD5, SHA, SHA256, SHA512 }
enum class V3PrivProtocol { NONE, DES, AES128, AES192, AES256 }
enum class DeviceCapability { ROUTER, SWITCH, AP, PHONE, OTHER }
enum class InterfaceStatus { UP, DOWN, UNKNOWN }
enum class LinkProtocol { LLDP, CDP }

/** A table whose completeness matters when comparing topology observations. */
enum class TopologyDataTable { INTERFACES, VLANS, LLDP_NEIGHBORS, CDP_NEIGHBORS }

enum class TopologyTableCompleteness { COMPLETE, PARTIAL, FAILED }

/** Typed reasons a table cannot be treated as a complete observation. */
enum class TopologyTableFailure {
    TIMEOUT,
    AUTHENTICATION,
    REQUEST_FAILED,
    SNMP_RESPONSE,
    TRUNCATED
}

data class TopologyTableObservation(
    val completeness: TopologyTableCompleteness,
    val failures: Set<TopologyTableFailure> = emptySet()
) {
    init {
        require((completeness == TopologyTableCompleteness.COMPLETE) == failures.isEmpty())
    }
}

enum class TopologyTruncationReason {
    NODE_LIMIT,
    LINK_LIMIT,
    PENDING_TARGET_LIMIT,
    INTERFACE_LIMIT,
    VLAN_LIMIT,
    WALK_ENTRY_LIMIT,
    WALK_PAGE_LIMIT,
    WALK_BYTE_LIMIT,
    WALK_VALUE_LIMIT,
    SCALAR_VALUE_LIMIT,
    DEVICE_ENTRY_LIMIT,
    DEVICE_BYTE_LIMIT,
    GRAPH_BYTE_LIMIT
}

/** Resource ceilings enforced while discovery is producing results. */
data class TopologyResourceLimits(
    val maxNodes: Int = 256,
    val maxLinks: Int = 512,
    val maxPendingTargets: Int = 128,
    val maxInterfacesPerNode: Int = 512,
    val maxVlansPerNode: Int = 512,
    val maxEntriesPerWalk: Int = 2_048,
    val maxBytesPerWalk: Int = 256 * 1024,
    val maxEntriesPerDevice: Int = 8_192,
    val maxBytesPerDevice: Int = 1024 * 1024,
    val maxBytesPerGraph: Int = 8 * 1024 * 1024,
    val maxValueChars: Int = 4_096,
    val maxRepetitions: Int = 10,
    val maxPagesPerWalk: Int = TopologyOperationBudget.DEFAULT_MAX_PAGES_PER_WALK,
) {
    init {
        require(maxNodes in 1..512 && maxLinks in 1..2_048 && maxPendingTargets in 1..256)
        require(maxInterfacesPerNode in 1..4_096 && maxVlansPerNode in 1..4_096)
        require(maxEntriesPerWalk in 1..4_096 && maxBytesPerWalk in 1..1024 * 1024)
        require(maxEntriesPerDevice in 1..16_384 && maxBytesPerDevice in 1..2 * 1024 * 1024)
        require(maxBytesPerGraph in 1..16 * 1024 * 1024)
        require(maxValueChars in 1..4_096 && maxRepetitions in 1..25)
        require(maxPagesPerWalk in 1..64)
    }
}

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
    val retries: Int = 1,
    /**
     * Optional opaque, non-secret profile token. Never derive it from credentials; null means
     * snapshots cannot be safely compared across credential scopes.
     */
    val credentialScopeId: String? = null
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
    val snmpReachable: Boolean,
    /** Missing entries mean that this source was not queried by an older producer. */
    val tableObservations: Map<TopologyDataTable, TopologyTableObservation> = emptyMap()
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
    val queriedAt: Long,
    val truncationReasons: Set<TopologyTruncationReason> = emptySet(),
    val hadSnmpErrors: Boolean = false,
    /** Null for graphs produced before comparison context was captured. */
    val scanContext: TopologyScanContext? = null
)
