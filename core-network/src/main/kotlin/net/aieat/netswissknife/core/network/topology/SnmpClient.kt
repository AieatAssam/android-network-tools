package net.aieat.netswissknife.core.network.topology

/**
 * Abstraction over SNMP4J to allow testability without real network I/O.
 */
interface SnmpClient : AutoCloseable {
    /**
     * Perform an SNMP GET for a single OID.
     * @param target contains ip, port, community/v3 params
     * @param oid the OID string
     * @return string value or null if not found
     */
    suspend fun get(target: SnmpTarget, oid: String): String?

    /**
     * Perform an SNMP WALK starting at the given OID prefix.
     * @param budget shared walk-entry and byte budget for this device
     * @return retained rows plus any resource limits that stopped the walk
     */
    suspend fun walk(target: SnmpTarget, oidPrefix: String, budget: SnmpWalkBudget): SnmpWalkResult

    override fun close()
}

data class SnmpTarget(
    val ip: String,
    val port: Int = 161,
    val params: TopologyParams
)
