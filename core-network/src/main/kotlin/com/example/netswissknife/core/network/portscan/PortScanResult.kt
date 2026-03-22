package com.example.netswissknife.core.network.portscan

/**
 * Result of scanning a single port on a host.
 *
 * @param port            The port number that was scanned.
 * @param status          Whether the port is [PortStatus.OPEN], [PortStatus.CLOSED] or [PortStatus.FILTERED].
 * @param serviceName     Resolved service name (e.g. "HTTP", "SSH"), or null if unknown.
 * @param serviceDescription Human-readable description of the service, or null.
 * @param banner          Protocol banner grabbed from the port (e.g. SSH version string), or null.
 * @param responseTimeMs  Round-trip time for the connection probe in milliseconds.
 */
data class PortScanResult(
    val port: Int,
    val status: PortStatus,
    val serviceName: String?,
    val serviceDescription: String?,
    val banner: String?,
    val responseTimeMs: Long
)
