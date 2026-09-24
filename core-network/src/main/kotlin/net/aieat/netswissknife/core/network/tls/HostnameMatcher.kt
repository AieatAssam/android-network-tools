package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.HostValidator
import java.security.cert.X509Certificate

/** Pure certificate identity matching for a DNS name or IP literal. */
object HostnameMatcher {

    /**
     * Returns whether [host] matches the certificate's subject alternative names.
     * Common-name fallback is used only if the certificate has no DNS SAN entries.
     */
    fun matches(host: String, certificate: X509Certificate): Boolean {
        val normalizedHost = HostValidator.normalize(host) ?: return false
        val hostBytes = ipAddressBytes(normalizedHost)
        val isIpLiteral = hostBytes != null || normalizedHost.contains(':') ||
            HostValidator.isValidIpv4(normalizedHost)

        val subjectAlternativeNames = try {
            certificate.subjectAlternativeNames.orEmpty()
        } catch (_: Exception) {
            // If SAN data exists but cannot be read, CN fallback could accept a
            // name that conflicts with a DNS SAN. Treat the identity as unknown.
            return false
        }

        val dnsNames = mutableListOf<String>()
        val ipAddresses = mutableListOf<ByteArray>()
        var hasDnsSan = false
        for (entry in subjectAlternativeNames) {
            val type = entry.getOrNull(0) as? Int ?: continue
            if (type == DNS_SAN_TYPE) hasDnsSan = true
            val value = entry.getOrNull(1) ?: continue
            when (type) {
                DNS_SAN_TYPE -> {
                    (value as? String)?.let(dnsNames::add)
                }
                IP_SAN_TYPE -> ipAddressBytes(value)?.let(ipAddresses::add)
            }
        }

        if (isIpLiteral) {
            return hostBytes != null && ipAddresses.any(hostBytes::contentEquals)
        }

        // Per certificate identity rules, the presence of any dNSName SAN suppresses CN fallback.
        if (hasDnsSan) {
            return dnsNames.any { matchesDnsName(normalizedHost, it) }
        }
        return matchesDnsName(normalizedHost, TlsCertificateParser.parseCN(certificate.subjectX500Principal.name))
    }

    internal fun matchesDnsName(host: String, pattern: String): Boolean {
        val normalizedHost = normalizeDnsName(host) ?: return false
        val normalizedPattern = normalizeDnsPattern(pattern) ?: return false
        if (!normalizedPattern.startsWith("*.")) return normalizedHost == normalizedPattern

        val suffix = normalizedPattern.removePrefix("*.")
        val suffixLabels = suffix.split('.')
        // Require a multi-label suffix, so a wildcard cannot sit directly below ".com".
        if (suffixLabels.size < 2) return false
        val hostLabels = normalizedHost.split('.')
        return hostLabels.size == suffixLabels.size + 1 &&
            hostLabels.drop(1) == suffixLabels &&
            hostLabels.first().isNotEmpty()
    }

    private fun normalizeDnsPattern(input: String): String? {
        // Host input is normalized for convenience, but certificate identities
        // must not gain a match by trimming malformed SAN/CN whitespace or by
        // removing more than one DNS root dot.
        if (input.any { it.isWhitespace() } || input.endsWith("..")) return null
        val value = input.removeSuffix(".")
        if (value.startsWith("*.")) {
            val suffix = normalizeDnsName(value.drop(2)) ?: return null
            if ('*' in suffix) return null
            return "*.$suffix"
        }
        if ('*' in value) return null
        return normalizeDnsName(value)
    }

    private fun normalizeDnsName(input: String): String? {
        val normalized = HostValidator.normalize(input) ?: return null
        if (normalized.contains(':') || HostValidator.isValidIpv4(normalized)) return null
        return normalized
    }

    private fun ipAddressBytes(value: Any): ByteArray? = when (value) {
        is ByteArray -> value.takeIf { it.size == IPV4_BYTES || it.size == IPV6_BYTES }?.copyOf()
        is String -> parseIpLiteral(value)
        else -> null
    }

    /** Parse only syntax-validated IP literals; this never invokes a name resolver. */
    private fun parseIpLiteral(input: String): ByteArray? {
        val value = input.trim().removeSurrounding("[", "]")
        if (value.contains('%')) return null // A scope ID is local routing metadata, not certificate identity.
        if (HostValidator.isValidIpv4(value)) {
            return value.split('.').map { it.toInt().toByte() }.toByteArray()
        }
        if (!HostValidator.isValidIpv6(value)) return null

        val address = if (value.contains('.')) {
            val lastColon = value.lastIndexOf(':')
            if (lastColon < 0) return null
            val ipv4Tail = parseIpLiteral(value.substring(lastColon + 1)) ?: return null
            if (ipv4Tail.size != IPV4_BYTES) return null
            val high = ((ipv4Tail[0].toInt() and 0xff) shl 8) or (ipv4Tail[1].toInt() and 0xff)
            val low = ((ipv4Tail[2].toInt() and 0xff) shl 8) or (ipv4Tail[3].toInt() and 0xff)
            value.substring(0, lastColon + 1) + "%04x:%04x".format(high, low)
        } else {
            value
        }

        val compression = address.indexOf("::")
        val groups = if (compression >= 0) {
            val left = address.substring(0, compression).split(':').filter(String::isNotEmpty)
            val right = address.substring(compression + 2).split(':').filter(String::isNotEmpty)
            val zeroCount = IPV6_GROUPS - left.size - right.size
            if (zeroCount < 1) return null
            left + List(zeroCount) { "0" } + right
        } else {
            address.split(':')
        }
        if (groups.size != IPV6_GROUPS) return null

        val bytes = ByteArray(IPV6_BYTES)
        groups.forEachIndexed { index, group ->
            val word = group.toIntOrNull(16)?.takeIf { it in 0..0xffff } ?: return null
            bytes[index * 2] = (word ushr 8).toByte()
            bytes[index * 2 + 1] = word.toByte()
        }
        return bytes
    }

    private const val DNS_SAN_TYPE = 2
    private const val IP_SAN_TYPE = 7
    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val IPV6_GROUPS = 8
}
