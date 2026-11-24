package net.aieat.netswissknife.core.network.lan

/** Events emitted by [LanScanRepository] during a scan. */
sealed interface LanScanUpdate {

    /**
     * Emitted once, before any probe, with whether this device can read MAC addresses. The same
     * value reaches the final summary, so the list can show MAC state from the start of the scan.
     */
    data class ScanStarted(
        val macResolutionSupported: Boolean,
    ) : LanScanUpdate

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
        val uncertainCount: Int = 0,
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
        val uncertainCount: Int = 0,
        /** One bounded diagnostic sample for partial results; null when none was recorded. */
        val diagnostic: LanScanDiagnostic? = null,
    ) : LanScanUpdate

    /** Emitted once when the scan finishes (always the last event). */
    data class ScanComplete(val summary: LanScanSummary) : LanScanUpdate
}
