package net.aieat.netswissknife.core.network.portscan

import net.aieat.netswissknife.core.network.tls.TlsCertificateParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.security.auth.x500.X500Principal

class TlsSubjectSanitizerTest {
    @Test
    fun `decoded certificate CN line controls cannot inject result or report lines`() {
        val hostileCn = TlsCertificateParser.parseCN(X500Principal("CN=server\\0AInjected\\09row").name)

        assertEquals("server Injected row", TlsSubjectSanitizer.sanitize(hostileCn))
        assertFalse(TlsSubjectSanitizer.sanitize(hostileCn).any { Character.isISOControl(it) })
    }

    @Test
    fun `format separators collapse and visible unicode is retained within display cap`() {
        val sanitized = TlsSubjectSanitizer.sanitize("例え\u2028host\u200Bname")

        assertEquals("例え host name", sanitized)
        assertTrue(TlsSubjectSanitizer.sanitize("x".repeat(300)).length <= 200)
    }

    @Test
    fun `truncation never splits a supplementary unicode character`() {
        val atBoundary = TlsSubjectSanitizer.sanitize("a".repeat(199) + "😀")
        val fittingPair = TlsSubjectSanitizer.sanitize("a".repeat(198) + "😀")

        assertEquals("a".repeat(199), atBoundary)
        assertEquals(199, atBoundary.length)
        assertEquals("a".repeat(198) + "😀", fittingPair)
        assertTrue(atBoundary.none { Character.getType(it).toInt() == Character.SURROGATE.toInt() })
    }
}
