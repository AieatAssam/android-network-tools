package net.aieat.netswissknife.core.network.tls

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TlsInspectorVerdictTest {
    private fun result(
        trusted: Boolean = true,
        hostnameMatches: Boolean? = true,
        pinMatch: Boolean? = null,
        issues: List<ChainIssue> = emptyList(),
    ) = TlsInspectorResult(
        host = "example.com",
        port = 443,
        tlsVersion = "TLSv1.3",
        cipherSuite = "TLS_AES_128_GCM_SHA256",
        chain = emptyList(),
        isChainTrusted = trusted,
        handshakeTimeMs = 1,
        hostnameMatches = hostnameMatches,
        chainIssues = issues,
        pinMatch = pinMatch,
    )

    @Test
    fun `trusted chain with matching hostname and no blocking findings is trusted`() {
        assertTrue(result().isConnectionTrusted)
    }

    @Test
    fun `hostname mismatch overrides a trusted chain`() {
        val mismatch = result(hostnameMatches = false, issues = listOf(ChainIssue.HOSTNAME_MISMATCH))

        assertTrue(mismatch.isChainTrusted)
        assertFalse(mismatch.isConnectionTrusted)
    }

    @Test
    fun `pin mismatch overrides a trusted chain`() {
        val pinned = result(pinMatch = false, issues = listOf(ChainIssue.PIN_MISMATCH))

        assertFalse(pinned.isConnectionTrusted)
    }

    @Test
    fun `matching pin keeps the connection trusted`() {
        assertTrue(result(pinMatch = true).isConnectionTrusted)
    }

    @Test
    fun `expired and not yet valid certificates override a trusted chain`() {
        assertFalse(result(issues = listOf(ChainIssue.EXPIRED)).isConnectionTrusted)
        assertFalse(result(issues = listOf(ChainIssue.NOT_YET_VALID)).isConnectionTrusted)
    }

    @Test
    fun `untrusted chain is never trusted`() {
        assertFalse(result(trusted = false, issues = listOf(ChainIssue.UNTRUSTED)).isConnectionTrusted)
    }

    @Test
    fun `expiring soon and weak-but-valid findings are advisory`() {
        assertTrue(result(issues = listOf(ChainIssue.EXPIRES_SOON)).isConnectionTrusted)
        assertTrue(result(issues = listOf(ChainIssue.WEAK_SIGNATURE)).isConnectionTrusted)
    }
}
