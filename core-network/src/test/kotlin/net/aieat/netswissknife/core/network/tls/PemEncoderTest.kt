package net.aieat.netswissknife.core.network.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class PemEncoderTest {
    @Test
    fun `encodes certificate DER as 64 column PEM`() {
        val certificate = TlsTestCertificates.read("valid-leaf")
        val pem = PemEncoder.encode(certificate)
        val lines = pem.trimEnd().lines()
        val body = lines.drop(1).dropLast(1)

        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertEquals("-----END CERTIFICATE-----", lines.last())
        assertTrue(body.all { it.length <= 64 })
        assertTrue(body.dropLast(1).all { it.length == 64 })
        assertTrue(certificate.encoded.contentEquals(Base64.getMimeDecoder().decode(body.joinToString(""))))
    }

    @Test
    fun `encodes chains in order separated by a newline`() {
        val chain = listOf(TlsTestCertificates.read("valid-leaf"), TlsTestCertificates.read("root"))
        val pem = PemEncoder.encode(chain)

        assertEquals(2, "-----BEGIN CERTIFICATE-----".toRegex().findAll(pem).count())
        assertTrue(pem.contains("-----END CERTIFICATE-----\n-----BEGIN CERTIFICATE-----"))
        assertTrue(pem.endsWith("-----END CERTIFICATE-----\n"))
        assertEquals("", PemEncoder.encode(emptyList()))
        val decodedCertificates = Regex(
            "-----BEGIN CERTIFICATE-----\\n([A-Za-z0-9+/=\\n]+)\\n-----END CERTIFICATE-----",
        ).findAll(pem).map { Base64.getMimeDecoder().decode(it.groupValues[1]) }.toList()
        assertEquals(chain.size, decodedCertificates.size)
        chain.indices.forEach { index -> assertTrue(chain[index].encoded.contentEquals(decodedCertificates[index])) }
    }
}
