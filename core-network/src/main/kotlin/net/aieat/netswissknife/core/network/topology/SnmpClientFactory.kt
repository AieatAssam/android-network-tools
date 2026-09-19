package net.aieat.netswissknife.core.network.topology

fun interface SnmpClientFactory {
    fun create(params: TopologyParams): SnmpClient
}
