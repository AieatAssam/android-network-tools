package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.flow.Flow

/** Contract for discovering live hosts on a local network segment. */
interface LanScanRepository {

    fun scan(request: LanScanRequest): Flow<LanScanUpdate>

    /**
     * Scans [subnet] (CIDR notation) for live hosts.
     *
     * @param subnet      Target network in CIDR notation (e.g. "192.168.1.0/24").
     * @param timeoutMs   Per-host reachability timeout in milliseconds.
     * @param concurrency Maximum number of concurrent host probes.
     * @return A [Flow] of [LanScanUpdate] events, always ending with [LanScanUpdate.ScanComplete].
     */
    @Deprecated("Use scan(LanScanRequest) so the real gateway can be supplied")
    fun scan(subnet: String, timeoutMs: Int, concurrency: Int): Flow<LanScanUpdate> =
        scan(LanScanRequest(subnet, timeoutMs, concurrency))
}
