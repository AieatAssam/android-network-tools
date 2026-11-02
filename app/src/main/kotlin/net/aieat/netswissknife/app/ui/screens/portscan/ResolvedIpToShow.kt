package net.aieat.netswissknife.app.ui.screens.portscan

/**
 * The resolved address to show under the host, or null when it would only repeat the host
 * (an IP target resolves to itself). The host line already says it; a second copy adds nothing.
 */
fun resolvedIpToShow(
    host: String,
    resolvedIp: String?,
): String? {
    val ip = resolvedIp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (ip.equals(host.trim(), ignoreCase = true)) null else ip
}
