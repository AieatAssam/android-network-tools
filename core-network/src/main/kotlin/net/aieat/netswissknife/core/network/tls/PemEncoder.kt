package net.aieat.netswissknife.core.network.tls

import java.security.cert.X509Certificate
import java.util.Base64

/** Deterministic RFC 7468 PEM encoding for X.509 certificates. */
object PemEncoder {
    private const val BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----"
    private const val END_CERTIFICATE = "-----END CERTIFICATE-----"

    fun encode(certificate: X509Certificate): String {
        val body = Base64.getMimeEncoder(LINE_WIDTH, NEWLINE_BYTES).encodeToString(certificate.encoded)
        return "$BEGIN_CERTIFICATE\n$body\n$END_CERTIFICATE\n"
    }

    fun encode(chain: List<X509Certificate>): String =
        chain.joinToString(separator = "\n", postfix = if (chain.isEmpty()) "" else "\n") {
            encode(it).trimEnd('\n')
        }

    private const val LINE_WIDTH = 64
    private val NEWLINE_BYTES = byteArrayOf('\n'.code.toByte())
}
