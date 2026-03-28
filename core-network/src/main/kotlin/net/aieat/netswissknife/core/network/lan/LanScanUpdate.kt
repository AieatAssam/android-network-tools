package net.aieat.netswissknife.core.network.lan

/** Events emitted by [LanScanRepository] during a scan. */
sealed interface LanScanUpdate {

    /**
     * Emitted each time a host responds to the reachability probe.
     *
     * @param host         The discovered host details.
     * @param scannedCount Number of IPs checked so far (alive + dead combined).
     * @param totalCount   Total number of IPs in the subnet.
     */
    data class HostFound(
        val host: LanHost,
        val scannedCount: Int,
        val totalCount: Int,
    ) : LanScanUpdate

    /**
     * Emitted periodically to report progress even when no host was found.
     *
     * @param scannedCount Number of IPs checked so far.
     * @param totalCount   Total number of IPs in the subnet.
     */
    data class ScanProgress(
        val scannedCount: Int,
        val totalCount: Int,
    ) : LanScanUpdate

    /** Emitted once when the scan finishes (always the last event). */
    data class ScanComplete(val summary: LanScanSummary) : LanScanUpdate
}
