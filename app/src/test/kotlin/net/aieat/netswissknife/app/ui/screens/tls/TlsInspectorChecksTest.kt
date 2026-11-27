package net.aieat.netswissknife.app.ui.screens.tls

import net.aieat.netswissknife.app.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TlsInspectorChecksTest {
    @Test
    fun `a URL gets the scheme message instead of an IPv6 message`() {
        assertEquals(R.string.tls_error_host_scheme, tlsHostErrorRes("http://x"))
        assertEquals(R.string.tls_error_host_scheme, tlsHostErrorRes("https://example.com"))
    }

    @Test
    fun `other rejected hosts keep the host problem message`() {
        assertEquals(R.string.error_invalid_host, tlsHostErrorRes("exa mple.com"))
    }

    @Test
    fun `an accepted TLS 1 0 or TLS 1 1 needs the legacy advisory`() {
        assertTrue(legacyProtocolAdvisoryNeeded(mapOf("TLSv1" to true, "TLSv1.2" to true)))
        assertTrue(legacyProtocolAdvisoryNeeded(mapOf("TLSv1.1" to true)))
    }

    @Test
    fun `refused legacy versions and modern versions alone need no legacy advisory`() {
        assertFalse(legacyProtocolAdvisoryNeeded(mapOf("TLSv1" to false, "TLSv1.1" to false, "TLSv1.2" to true)))
        assertFalse(legacyProtocolAdvisoryNeeded(mapOf("TLSv1.2" to true, "TLSv1.3" to true)))
        assertFalse(legacyProtocolAdvisoryNeeded(null))
    }
}
