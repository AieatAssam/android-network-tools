package net.aieat.netswissknife.core.domain

/** Preset port ranges for the port scanner. */
enum class PortScanPreset(
    val portsProvider: () -> List<Int>
) {
    COMMON(
        portsProvider = {
            listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 465, 587,
                993, 995, 3306, 3389, 5432, 5900, 6379, 8080, 8443, 8888, 9200, 27017)
        }
    ),
    WELL_KNOWN(
        portsProvider = { (1..1024).toList() }
    ),
    WEB(
        portsProvider = { listOf(80, 443, 8000, 8080, 8081, 8443, 8888, 3000, 4000, 5000) }
    ),
    DATABASE(
        portsProvider = { listOf(1433, 1521, 3306, 5432, 5433, 6379, 9200, 9300, 11211, 27017, 27018, 28015) }
    ),
    MAIL(
        portsProvider = { listOf(25, 110, 143, 465, 587, 993, 995) }
    ),
    REMOTE(
        portsProvider = { listOf(22, 23, 3389, 5900, 5901, 1194, 1723) }
    ),
    CUSTOM(
        portsProvider = { emptyList() }
    );

    val ports: List<Int> get() = portsProvider()
}

/**
 * Parameters for a port scan operation.
 *
 * @param host        Hostname or IP to scan.
 * @param preset      The [PortScanPreset] to use. If [PortScanPreset.CUSTOM], [startPort]/[endPort] are used.
 * @param startPort   Custom range start (only used when preset is [PortScanPreset.CUSTOM]).
 * @param endPort     Custom range end (only used when preset is [PortScanPreset.CUSTOM]).
 * @param timeoutMs   Per-port connection timeout in milliseconds.
 * @param concurrency Max simultaneous port probes.
 * @param aggressiveProbes Whether open ports receive one bounded protocol greeting or TLS peek.
 */
data class PortScanParams(
    val host: String,
    val preset: PortScanPreset = PortScanPreset.COMMON,
    val startPort: Int = 1,
    val endPort: Int = 1024,
    val timeoutMs: Int = 2000,
    val concurrency: Int = 100,
    val aggressiveProbes: Boolean = true,
)
