package net.aieat.netswissknife.core.network.tls

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class TlsCompatibilityProbeTest {
    private val certificate = TlsTestCertificates.read("valid-leaf")

    @Test
    fun `hello offers P-521 with the other curves and is TLS 1_2 only`() {
        val hello = TlsCompatibilityHello.build("printer.local", ByteArray(32))

        val groups = TlsCompatibilityHello.parseGroups(hello)
        assertEquals(listOf(0x001d, 0x0017, 0x0018, 0x0019), groups)
        assertFalse(TlsCompatibilityHello.extensionTypes(hello).contains(43), "must not offer TLS 1.3")
        assertEquals(0x16, hello[0].toInt())
        assertEquals(0x01, hello[5].toInt(), "handshake type client_hello")
    }

    @Test
    fun `SNI is sent for hostnames but not for IP literals`() {
        fun hasSni(host: String): Boolean {
            val hello = TlsCompatibilityHello.build(host, ByteArray(32))
            return TlsCompatibilityHello.extensionTypes(hello).contains(0)
        }

        assertTrue(hasSni("router.lan"))
        assertFalse(hasSni("10.109.208.1"))
        assertFalse(hasSni("fe80::1"))
    }

    @Test
    fun `flight reader returns version cipher and certificates across fragmented records`() {
        val bytes = serverFlight(fragmentSize = 7)

        val flight = TlsServerFlightReader.read(ByteArrayInputStream(bytes))

        assertEquals("TLSv1.2", flight.protocol)
        assertEquals("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", flight.cipherSuite)
        assertEquals(1, flight.certificates.size)
        assertArrayEquals(certificate.encoded, flight.certificates.single())
    }

    @Test
    fun `fatal alert before the certificate is reported as a handshake failure`() {
        val alert = byteArrayOf(0x15, 3, 3, 0, 2, 2, 40)

        val failure =
            assertThrows(SSLHandshakeException::class.java) {
                TlsServerFlightReader.read(ByteArrayInputStream(alert))
            }
        assertTrue(failure.message.orEmpty().contains("handshake_failure"))
    }

    @Test
    fun `encrypted handshake or oversized flight is refused`() {
        val ccs = byteArrayOf(0x14, 3, 3, 0, 1, 1)
        assertThrows(IOException::class.java) { TlsServerFlightReader.read(ByteArrayInputStream(ccs)) }

        val huge = ByteArrayOutputStream()
        repeat(40) { huge.write(byteArrayOf(0x16, 3, 3, 0x40, 0x00) + ByteArray(0x4000)) }
        assertThrows(IOException::class.java) { TlsServerFlightReader.read(ByteArrayInputStream(huge.toByteArray())) }
    }

    private fun serverFlight(fragmentSize: Int): ByteArray {
        val cipher = byteArrayOf(0xC0.toByte(), 0x2C)
        val helloBody = byteArrayOf(3, 3) + ByteArray(32) + byteArrayOf(0) + cipher + byteArrayOf(0)
        val serverHello = handshake(2, helloBody)
        val der = certificate.encoded
        val certList = u24(der.size) + der
        val certificates = handshake(11, u24(certList.size) + certList)
        val done = handshake(14, ByteArray(0))
        val stream = serverHello + certificates + done
        val out = ByteArrayOutputStream()
        stream.toList().chunked(fragmentSize).forEach { chunk ->
            out.write(byteArrayOf(0x16, 3, 3, 0, chunk.size.toByte()))
            out.write(chunk.toByteArray())
        }
        return out.toByteArray()
    }

    private fun handshake(
        type: Int,
        body: ByteArray,
    ) = byteArrayOf(type.toByte()) + u24(body.size) + body

    private fun u24(n: Int) = byteArrayOf((n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
}
