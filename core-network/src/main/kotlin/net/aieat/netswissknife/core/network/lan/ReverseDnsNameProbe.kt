package net.aieat.netswissknife.core.network.lan

import java.net.InetAddress

/** Reverse-DNS enrichment probe; DNS failure never affects host presence. */
class ReverseDnsNameProbe : NameProbe {
    override suspend fun resolveName(ip: String, timeoutMs: Int): String? = try {
        val hostname = InetAddress.getByName(ip).canonicalHostName
        hostname.takeUnless { it == ip }
    } catch (_: Exception) {
        null
    }
}
