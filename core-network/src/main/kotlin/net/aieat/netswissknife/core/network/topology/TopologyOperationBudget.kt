package net.aieat.netswissknife.core.network.topology

/**
 * Estimates a useful SNMP deadline from the request instead of imposing one fixed duration.
 *
 * A device query has four scalar GETs and up to nine initial table walks (including the LLDP/CDP
 * walks), plus an empty-VLAN fallback. Each table walk is stopped after a small fixed number of
 * response pages so the request-derived deadline can bound sparse GETNEXT as well as GETBULK
 * responses. Since a topology can branch at every hop, the deadline also caps admitted devices.
 */
object TopologyOperationBudget {
    const val HARD_CEILING_MILLIS = 10 * 60 * 1_000L
    const val OVER_CEILING_MESSAGE =
        "Topology discovery settings exceed the 10 minute limit. Reduce max hops, timeout, or retries."

    const val MAX_GRAPH_NODES = 512
    const val DEFAULT_MAX_PAGES_PER_WALK = 3
    private const val SCALAR_GET_WINDOWS_PER_NODE = 4L
    private const val INITIAL_TABLE_WALKS_PER_NODE = 9L
    private const val MAX_CONCURRENT_REQUESTS = 4
    private const val SCHEDULING_MARGIN_PERCENT = 25L
    private const val FINAL_CLEANUP_ALLOWANCE_MILLIS = 1_000L

    data class Estimate(val maxNodes: Int, val timeoutMillis: Long)

    fun estimatedTimeoutMillis(params: TopologyParams): Long {
        return estimate(params)?.timeoutMillis ?: Long.MAX_VALUE
    }

    fun timeoutMillisOrNull(params: TopologyParams): Long? =
        estimate(params)?.timeoutMillis

    fun estimate(
        params: TopologyParams,
        configuredMaxNodes: Int = MAX_GRAPH_NODES,
        sessionConcurrency: Int = MAX_CONCURRENT_REQUESTS,
        maxPagesPerWalk: Int = DEFAULT_MAX_PAGES_PER_WALK,
    ): Estimate? {
        if (configuredMaxNodes <= 0 || sessionConcurrency <= 0 || maxPagesPerWalk <= 0) return null
        val attempts = (params.retries.toLong() + 1L).coerceAtLeast(1L)
        val effectiveConcurrency = minOf(sessionConcurrency, MAX_CONCURRENT_REQUESTS)
        // The repository holds one semaphore permit for a full table walk. Nine initial walk jobs
        // can require ceil(9 / concurrency) batches, followed by one conditional VLAN fallback.
        // Add one response page because the collector must observe an extra page to confirm that
        // the configured cap truncated the walk. SNMPv3 also discovers each responder's engine ID.
        val walkBatches = (INITIAL_TABLE_WALKS_PER_NODE + effectiveConcurrency - 1) / effectiveConcurrency + 1
        val pagesPerWalk = maxPagesPerWalk.toLong() + 1L
        val walkWindows = saturatingMultiply(walkBatches, pagesPerWalk)
        val engineDiscoveryWindows = if (params.snmpVersion == SnmpVersion.V3) 1L else 0L
        val requestWindows = saturatingAdd(
            saturatingAdd(SCALAR_GET_WINDOWS_PER_NODE, engineDiscoveryWindows),
            walkWindows,
        )
        val perNodeTimeout = saturatingMultiply(
            saturatingMultiply(requestWindows, attempts),
            params.timeoutMs.toLong().coerceAtLeast(1L),
        )
        val perNodeWithMargin = saturatingAdd(
            perNodeTimeout,
            ceilPercent(perNodeTimeout, SCHEDULING_MARGIN_PERCENT),
        )
        val availableForNodes = HARD_CEILING_MILLIS - FINAL_CLEANUP_ALLOWANCE_MILLIS
        val nodeCapacity = (availableForNodes / perNodeWithMargin).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (nodeCapacity < 1) return null
        val maxNodes = minOf(configuredMaxNodes, nodeCapacity)
        val rawWork = saturatingMultiply(perNodeTimeout, maxNodes.toLong())
        val timeoutMillis = saturatingAdd(
            saturatingAdd(rawWork, ceilPercent(rawWork, SCHEDULING_MARGIN_PERCENT)),
            FINAL_CLEANUP_ALLOWANCE_MILLIS,
        )
        if (timeoutMillis > HARD_CEILING_MILLIS) return null
        return Estimate(maxNodes, timeoutMillis)
    }

    private fun ceilPercent(value: Long, percent: Long): Long =
        saturatingAdd(saturatingMultiply(value, percent), 99L) / 100L

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun saturatingMultiply(left: Long, right: Long): Long =
        if (right != 0L && left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right
}
