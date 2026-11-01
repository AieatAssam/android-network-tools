package net.aieat.netswissknife.app.ui.screens.lan

/**
 * Name shown above a device node in the LAN topology map, or null when the node has no name.
 *
 * The node always shows the last IP octet as its caption, so a name equal to that octet (a host
 * with neither vendor nor hostname falls back to its octet) is dropped: showing it would print the
 * same text twice. Blank names count as missing.
 */
internal fun lanTopologyNodeName(
    vendor: String?,
    hostname: String?,
    ip: String,
): String? {
    val octet = ip.substringAfterLast(".")
    val name =
        vendor?.takeIf { it.isNotBlank() }
            ?: hostname?.split(".")?.firstOrNull()?.takeIf { it.isNotBlank() }
    return name?.takeIf { it != octet }
}
