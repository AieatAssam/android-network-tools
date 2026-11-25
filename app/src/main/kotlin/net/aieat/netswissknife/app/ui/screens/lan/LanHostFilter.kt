package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.lan.DiscoveryMethod
import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanOperationBudget

/** Device list filter. The choice is saved like the sort order. */
enum class LanHostFilter {
    All,
    Gateway,
    HasPorts,
    ;

    companion object {
        fun fromStoredName(name: String?): LanHostFilter = entries.firstOrNull { it.name == name } ?: All
    }
}

/** Visible hosts: filtered by the search and filter, then sorted. The list and the map both use it. */
internal fun lanVisibleHosts(
    hosts: List<LanHost>,
    searchQuery: String,
    activeFilter: LanHostFilter,
    order: LanHostSortOrder,
): List<LanHost> = sortLanHosts(filterLanHosts(hosts, searchQuery, activeFilter), order)

internal fun filterLanHosts(
    hosts: List<LanHost>,
    searchQuery: String,
    activeFilter: LanHostFilter,
): List<LanHost> {
    val query = searchQuery.trim()
    return hosts.filter { host ->
        val passesFilter =
            when (activeFilter) {
                LanHostFilter.All -> true
                LanHostFilter.Gateway -> host.isGateway
                LanHostFilter.HasPorts -> host.openPorts.isNotEmpty()
            }
        val passesSearch =
            query.isEmpty() ||
                listOfNotNull(host.ip, host.hostname, host.vendor, host.macAddress)
                    .any { it.contains(query, ignoreCase = true) }
        passesFilter && passesSearch
    }
}

/**
 * Labels for the probes that proved the host present, in probe order. Reverse DNS only names a
 * host that was already found, so it is not listed.
 */
internal fun lanPresenceLabels(methods: Set<DiscoveryMethod>): List<Int> =
    DiscoveryMethod.entries
        .filter { it in methods }
        .mapNotNull { method ->
            when (method) {
                DiscoveryMethod.ICMP -> R.string.lan_discovery_icmp
                DiscoveryMethod.TCP_OPEN -> R.string.lan_discovery_tcp_open
                DiscoveryMethod.TCP_REFUSED -> R.string.lan_discovery_tcp_refused
                DiscoveryMethod.NETBIOS -> R.string.lan_discovery_netbios
                DiscoveryMethod.MDNS -> R.string.lan_discovery_mdns
                DiscoveryMethod.RDNS -> null
            }
        }

private const val MAX_SCAN_CONCURRENCY = 500

/**
 * [current] when [targetCount] addresses already finish within the scan time limit, otherwise the
 * smallest higher concurrency slider position that does, or null when no position can.
 */
internal fun lanConcurrencyToFit(
    targetCount: Int,
    timeoutMs: Int,
    current: Int,
): Int? {
    val fits = { concurrency: Int ->
        !LanScanOperationBudget.estimate(targetCount, timeoutMs, concurrency).exceedsHardCeiling
    }
    if (fits(current)) return current
    return (current..MAX_SCAN_CONCURRENCY)
        .asSequence()
        .map { snapConcurrency(it.toFloat()) }
        .filter { it > current }
        .distinct()
        .firstOrNull(fits)
}
