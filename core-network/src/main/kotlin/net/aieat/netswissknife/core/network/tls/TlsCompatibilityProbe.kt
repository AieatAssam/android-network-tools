package net.aieat.netswissknife.core.network.tls

import net.aieat.netswissknife.core.network.HostValidator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import javax.net.ssl.SSLHandshakeException

/** What a TLS 1.2 server sent before the handshake turned encrypted. */
internal class TlsCapturedFlight(
    val protocol: String,
    val cipherSuite: String,
    val certificates: List<ByteArray>,
)

/**
 * A hand-built TLS 1.2 ClientHello for servers that refuse the platform provider's hello.
 *
 * Android's TLS stack cannot change its supported-groups list, and some servers (for example a
 * gateway with a P-521 ECDSA certificate) answer a hello without secp521r1 with a
 * handshake_failure alert before sending anything. This hello offers P-521 next to the usual
 * curves. It never completes a handshake: the caller only reads the unencrypted certificates.
 */
internal object TlsCompatibilityHello {
    private const val GROUP_X25519 = 0x001d
    private const val GROUP_P256 = 0x0017
    private const val GROUP_P384 = 0x0018
    private const val GROUP_P521 = 0x0019
    private val groups = listOf(GROUP_X25519, GROUP_P256, GROUP_P384, GROUP_P521)

    private val cipherSuites =
        listOf(
            0xc02c, 0xc02b, 0xc030, 0xc02f, 0xcca9, 0xcca8, 0xc024, 0xc023, 0xc028, 0xc027,
            0xc00a, 0xc009, 0xc014, 0xc013, 0x009d, 0x009c, 0x003d, 0x003c, 0x0035, 0x002f,
            0x00ff, // TLS_EMPTY_RENEGOTIATION_INFO_SCSV
        )
    private val signatureAlgorithms =
        listOf(
            0x0403, 0x0503, 0x0603, 0x0804, 0x0805, 0x0806, 0x0807,
            0x0401, 0x0501, 0x0601, 0x0203, 0x0201,
        )

    fun build(
        host: String,
        random: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) },
    ): ByteArray {
        require(random.size == 32) { "ClientHello random must be 32 bytes" }
        val extensions = ByteArrayOutputStream()
        serverName(host)?.let { extensions.extension(0, it) }
        extensions.extension(11, byteArrayOf(1, 0))
        extensions.extension(10, u16(groups.size * 2) + groups.flatMap { u16(it).toList() }.toByteArray())
        extensions.extension(
            13,
            u16(signatureAlgorithms.size * 2) + signatureAlgorithms.flatMap { u16(it).toList() }.toByteArray(),
        )
        extensions.extension(23, ByteArray(0))
        extensions.extension(0xff01, byteArrayOf(0))
        val extensionBytes = extensions.toByteArray()

        val suites = cipherSuites.flatMap { u16(it).toList() }.toByteArray()
        val body =
            ByteArrayOutputStream().apply {
                write(byteArrayOf(3, 3))
                write(random)
                write(0) // empty session id
                write(u16(suites.size))
                write(suites)
                write(byteArrayOf(1, 0)) // null compression only
                write(u16(extensionBytes.size))
                write(extensionBytes)
            }.toByteArray()
        val handshake = byteArrayOf(1) + u24(body.size) + body
        return byteArrayOf(0x16, 3, 1) + u16(handshake.size) + handshake
    }

    private fun serverName(host: String): ByteArray? {
        val name = HostValidator.normalize(host) ?: return null
        if (name.contains(':') || HostValidator.isValidIpv4(name)) return null
        val bytes = name.toByteArray(Charsets.US_ASCII)
        val entry = byteArrayOf(0) + u16(bytes.size) + bytes
        return u16(entry.size) + entry
    }

    private fun ByteArrayOutputStream.extension(
        type: Int,
        data: ByteArray,
    ) {
        write(u16(type))
        write(u16(data.size))
        write(data)
    }

    /** Test helper: the supported-groups ids in [hello]. */
    fun parseGroups(hello: ByteArray): List<Int> {
        val data = extensions(hello)[10] ?: return emptyList()
        return (2 until data.size step 2).map { ((data[it].toInt() and 0xff) shl 8) or (data[it + 1].toInt() and 0xff) }
    }

    /** Test helper: the extension types present in [hello]. */
    fun extensionTypes(hello: ByteArray): Set<Int> = extensions(hello).keys

    private fun extensions(hello: ByteArray): Map<Int, ByteArray> {
        var p = 5 + 4 + 2 + 32
        p += 1 + (hello[p].toInt() and 0xff)
        p += 2 + (((hello[p].toInt() and 0xff) shl 8) or (hello[p + 1].toInt() and 0xff))
        p += 1 + (hello[p].toInt() and 0xff)
        val end = p + 2 + (((hello[p].toInt() and 0xff) shl 8) or (hello[p + 1].toInt() and 0xff))
        p += 2
        val result = linkedMapOf<Int, ByteArray>()
        while (p < end) {
            val type = ((hello[p].toInt() and 0xff) shl 8) or (hello[p + 1].toInt() and 0xff)
            val length = ((hello[p + 2].toInt() and 0xff) shl 8) or (hello[p + 3].toInt() and 0xff)
            result[type] = hello.copyOfRange(p + 4, p + 4 + length)
            p += 4 + length
        }
        return result
    }
}

private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())

private fun u24(value: Int) = byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

/** Reads a TLS 1.2 server flight up to the Certificate message, refusing anything encrypted or oversized. */
internal object TlsServerFlightReader {
    private const val MAX_TOTAL_BYTES = 256 * 1024
    private const val MAX_RECORD_BYTES = 18 * 1024
    private const val MAX_MESSAGE_BYTES = 128 * 1024
    private const val RECORD_ALERT = 0x15
    private const val RECORD_HANDSHAKE = 0x16
    private const val HANDSHAKE_SERVER_HELLO = 2
    private const val HANDSHAKE_CERTIFICATE = 11

    fun read(input: InputStream): TlsCapturedFlight {
        val pending = ByteArrayOutputStream()
        var total = 0
        var protocol: String? = null
        var cipher: String? = null
        while (true) {
            val header = readFully(input, 5)
            total += 5
            val type = header[0].toInt() and 0xff
            val length = ((header[3].toInt() and 0xff) shl 8) or (header[4].toInt() and 0xff)
            if (length > MAX_RECORD_BYTES) throw IOException("TLS record too large")
            total += length
            if (total > MAX_TOTAL_BYTES) throw IOException("TLS server flight too large")
            val payload = readFully(input, length)
            when (type) {
                RECORD_ALERT ->
                    throw SSLHandshakeException("Received fatal alert: ${alertName(payload.getOrNull(1)?.toInt()?.and(0xff))}")
                RECORD_HANDSHAKE -> pending.write(payload)
                else -> throw IOException("Server continued with an encrypted handshake")
            }
            var buffered = pending.toByteArray()
            var offset = 0
            while (buffered.size - offset >= 4) {
                val messageType = buffered[offset].toInt() and 0xff
                val messageLength = readU24(buffered, offset + 1)
                if (messageLength > MAX_MESSAGE_BYTES) throw IOException("TLS handshake message too large")
                if (buffered.size - offset - 4 < messageLength) break
                val message = buffered.copyOfRange(offset + 4, offset + 4 + messageLength)
                offset += 4 + messageLength
                when (messageType) {
                    HANDSHAKE_SERVER_HELLO -> {
                        val hello = parseServerHello(message)
                        protocol = hello.first
                        cipher = hello.second
                    }
                    HANDSHAKE_CERTIFICATE ->
                        return TlsCapturedFlight(
                            protocol ?: throw IOException("Certificate before ServerHello"),
                            cipher ?: throw IOException("Certificate before ServerHello"),
                            parseCertificates(message),
                        )
                }
            }
            pending.reset()
            pending.write(buffered, offset, buffered.size - offset)
        }
    }

    private fun parseServerHello(message: ByteArray): Pair<String, String> {
        if (message.size < 2 + 32 + 1) throw IOException("Malformed ServerHello")
        val version = ((message[0].toInt() and 0xff) shl 8) or (message[1].toInt() and 0xff)
        val sessionIdLength = message[34].toInt() and 0xff
        val cipherOffset = 35 + sessionIdLength
        if (message.size < cipherOffset + 2) throw IOException("Malformed ServerHello")
        val suite = ((message[cipherOffset].toInt() and 0xff) shl 8) or (message[cipherOffset + 1].toInt() and 0xff)
        val protocol =
            when (version) {
                0x0303 -> "TLSv1.2"
                0x0302 -> "TLSv1.1"
                0x0301 -> "TLSv1"
                else -> throw IOException("Unexpected protocol version 0x${version.toString(16)}")
            }
        return protocol to cipherSuiteName(suite)
    }

    private fun parseCertificates(message: ByteArray): List<ByteArray> {
        if (message.size < 3) throw IOException("Malformed Certificate message")
        val listLength = readU24(message, 0)
        if (listLength > message.size - 3) throw IOException("Malformed Certificate message")
        val certificates = mutableListOf<ByteArray>()
        var p = 3
        while (p < 3 + listLength) {
            if (p + 3 > message.size) throw IOException("Malformed Certificate message")
            val length = readU24(message, p)
            p += 3
            if (p + length > message.size) throw IOException("Malformed Certificate message")
            certificates += message.copyOfRange(p, p + length)
            p += length
        }
        if (certificates.isEmpty()) throw IOException("Server sent no certificate")
        return certificates
    }

    private fun readU24(data: ByteArray, offset: Int) =
        ((data[offset].toInt() and 0xff) shl 16) or ((data[offset + 1].toInt() and 0xff) shl 8) or
            (data[offset + 2].toInt() and 0xff)

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) throw IOException("Connection closed during TLS handshake")
            read += n
        }
        return buffer
    }

    private fun alertName(description: Int?): String =
        when (description) {
            40 -> "handshake_failure"
            70 -> "protocol_version"
            71 -> "insufficient_security"
            80 -> "internal_error"
            112 -> "unrecognized_name"
            else -> "alert ${description ?: "unknown"}"
        }

    private fun cipherSuiteName(id: Int): String =
        when (id) {
            0xc02c -> "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
            0xc02b -> "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
            0xc030 -> "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
            0xc02f -> "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
            0xcca9 -> "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256"
            0xcca8 -> "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
            0xc024 -> "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384"
            0xc023 -> "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"
            0xc028 -> "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384"
            0xc027 -> "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"
            0xc00a -> "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA"
            0xc009 -> "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA"
            0xc014 -> "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"
            0xc013 -> "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"
            0x009d -> "TLS_RSA_WITH_AES_256_GCM_SHA384"
            0x009c -> "TLS_RSA_WITH_AES_128_GCM_SHA256"
            0x0035 -> "TLS_RSA_WITH_AES_256_CBC_SHA"
            0x002f -> "TLS_RSA_WITH_AES_128_CBC_SHA"
            else -> "TLS_0x${id.toString(16).padStart(4, '0')}"
        }
}
