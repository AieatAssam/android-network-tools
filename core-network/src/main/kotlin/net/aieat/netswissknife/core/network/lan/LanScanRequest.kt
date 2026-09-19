package net.aieat.netswissknife.core.network.lan

data class LanScanRequest(
    val subnet: String,
    val timeoutMs: Int = 1_000,
    val concurrency: Int = 50,
    val gatewayIp: String? = null,
    val presencePorts: List<Int> = DEFAULT_PRESENCE_PORTS,
    val enableNameProbes: Boolean = true,
)
