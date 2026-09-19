package net.aieat.netswissknife.core.domain

/**
 * Input parameters for a LAN scan.
 *
 * @param subnet      Target subnet in CIDR notation (e.g. "192.168.1.0/24").
 *                    Use [net.aieat.netswissknife.core.network.lan.SubnetUtils.getCurrentSubnet]
 *                    to populate this with the device's current network.
 * @param timeoutMs   Per-host reachability timeout (100–10 000 ms). Default 1 s.
 * @param concurrency Concurrent host probes (1–500). Default 50.
 * @param gatewayIp   Active network gateway, if known; values outside [subnet] are ignored.
 * @param enableNameProbes Whether NetBIOS/mDNS probes may establish presence. Default true.
 */
data class LanScanParams(
    val subnet: String,
    val timeoutMs: Int = 1_000,
    val concurrency: Int = 50,
    val gatewayIp: String? = null,
    val enableNameProbes: Boolean = true,
)
