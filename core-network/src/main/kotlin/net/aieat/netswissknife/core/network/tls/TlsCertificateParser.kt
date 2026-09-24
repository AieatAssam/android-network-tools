package net.aieat.netswissknife.core.network.tls

import java.security.MessageDigest
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import java.security.cert.X509Certificate
import java.net.InetAddress
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8

object TlsCertificateParser {

    fun isExpired(notAfterMs: Long): Boolean = notAfterMs < System.currentTimeMillis()

    fun isSelfSigned(subjectDn: String, issuerDn: String): Boolean = subjectDn == issuerDn

    fun sha256Fingerprint(encoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun parseCN(dn: String): String = extractComponent(dn, "CN") ?: ""

    fun parseOrg(dn: String): String? = extractComponent(dn, "O")

    /**
     * Parses an X.500 distinguished name string and extracts the value for [key].
     *
     * Handles common DN formats:
     *   CN=example.com,O=Example Inc,C=US
     *   CN=*.example.com, O=Example Inc, C=US  (spaces after commas)
     */
    private fun extractComponent(dn: String, key: String): String? {
        // Split on commas that are not inside quoted strings
        val parts = splitDn(dn)
        for (part in parts) {
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex < 0) continue
            val attrKey = trimmed.substring(0, eqIndex).trim()
            if (attrKey.equals(key, ignoreCase = true)) {
                var value = unescapeDnValue(trimmed.substring(eqIndex + 1).trim())
                // Strip surrounding quotes if present
                if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                    value = value.substring(1, value.length - 1)
                }
                return value.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun unescapeDnValue(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\') {
                output.append(value[index++])
                continue
            }

            if (index + 2 < value.length && value[index + 1].digitToIntOrNull(16) != null &&
                value[index + 2].digitToIntOrNull(16) != null
            ) {
                val escapedBytes = ByteArrayOutputStream()
                while (index + 2 < value.length && value[index] == '\\') {
                    val high = value[index + 1].digitToIntOrNull(16) ?: break
                    val low = value[index + 2].digitToIntOrNull(16) ?: break
                    escapedBytes.write((high shl 4) or low)
                    index += 3
                }
                output.append(String(escapedBytes.toByteArray(), UTF_8))
            } else if (index + 1 < value.length) {
                output.append(value[index + 1])
                index += 2
            } else {
                output.append('\\')
                index++
            }
        }
        return output.toString()
    }

    /** Splits a DN string on commas, respecting quoted values. */
    private fun splitDn(dn: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false
        for (ch in dn) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escaped = true
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch == ',' && !inQuotes -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    /** Parses a full [X509Certificate] into a [TlsCertificate]. */
    fun parse(cert: X509Certificate): TlsCertificate {
        val subjectDn = cert.subjectX500Principal.name
        val issuerDn  = cert.issuerX500Principal.name

        val notBefore = cert.notBefore.time
        val notAfter  = cert.notAfter.time

        val sans = try {
            cert.subjectAlternativeNames.orEmpty().mapNotNull { san ->
                val type = san.getOrNull(0) as? Int ?: return@mapNotNull null
                val rawValue = san.getOrNull(1) ?: return@mapNotNull null
                when (type) {
                    2 -> (rawValue as? String)?.let { "DNS:$it" }
                    7 -> parseIpSanValue(rawValue)?.let { "IP:$it" }
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        val publicKeyBits = when (val key = cert.publicKey) {
            is RSAKey -> key.modulus.bitLength()
            is ECKey  -> key.params.curve.field.fieldSize
            else      -> 0
        }

        return TlsCertificate(
            subjectCN           = parseCN(subjectDn),
            subjectOrg          = parseOrg(subjectDn),
            issuerCN            = parseCN(issuerDn),
            issuerOrg           = parseOrg(issuerDn),
            notBefore           = notBefore,
            notAfter            = notAfter,
            isExpired           = isExpired(notAfter),
            isSelfSigned        = isSelfSigned(subjectDn, issuerDn),
            sans                = sans,
            serialNumber        = cert.serialNumber.toString(16).uppercase(),
            signatureAlgorithm  = cert.sigAlgName,
            publicKeyAlgorithm  = cert.publicKey.algorithm,
            publicKeyBits       = publicKeyBits,
            sha256Fingerprint   = sha256Fingerprint(cert.encoded)
        )
    }

    private fun parseIpSanValue(value: Any): String? {
        return when (value) {
            is String -> value
            is ByteArray -> {
                if (value.size == IPV4_BYTES || value.size == IPV6_BYTES) {
                    InetAddress.getByAddress(value).hostAddress
                } else null
            }
            else -> null
        }
    }

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
}
