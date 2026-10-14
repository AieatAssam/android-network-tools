package net.aieat.netswissknife.app.ui.screens.httprobe

import net.aieat.netswissknife.core.domain.validateHttpProbeUrl
import net.aieat.netswissknife.core.network.HostValidator
import java.net.URI
import java.util.Locale

/**
 * Returns a validated, credential-free HTTP origin suitable for recent history.
 * Paths, queries, fragments, and user info are deliberately discarded.
 */
internal fun safeHttpRecentOrigin(rawUrl: String): String? {
    if (validateHttpProbeUrl(rawUrl) != null) return null

    return runCatching {
        val input = rawUrl.trim()
        val uri = URI(input)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val rawHost = uri.host ?: uri.toURL().host
        val normalizedHost = HostValidator.normalize(rawHost) ?: return null
        val host = normalizedHost.removeSurrounding("[", "]")
        val canonicalHost = if (host.contains(':')) {
            val zoneStart = host.indexOf('%')
            if (zoneStart < 0) {
                host.lowercase(Locale.ROOT)
            } else {
                host.substring(0, zoneStart).lowercase(Locale.ROOT) + host.substring(zoneStart)
            }
        } else {
            host
        }
        val port = uri.port.takeUnless {
            it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443)
        } ?: -1

        URI(scheme, null, canonicalHost, port, null, null, null).toASCIIString()
    }.getOrNull()
}
