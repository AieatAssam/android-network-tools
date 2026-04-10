package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.NetworkResult

/**
 * Computes subnet information from various input notations.
 *
 * Supported input formats:
 * - CIDR:            "192.168.1.0/24"
 * - IP + prefix:     "192.168.1.0/255.255.255.0"
 * - IP + space mask: "192.168.1.0 255.255.255.0"
 * - Bare IP:         "192.168.1.1"  (treated as /32)
 */
interface SubnetCalculatorRepository {
    fun calculate(input: String): NetworkResult<SubnetInfo>
}
