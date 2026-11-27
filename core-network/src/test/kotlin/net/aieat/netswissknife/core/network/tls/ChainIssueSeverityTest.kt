package net.aieat.netswissknife.core.network.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChainIssueSeverityTest {
    @Test
    fun `only trust, validity, hostname and pin findings are blocking`() {
        val blocking = ChainIssue.entries.filter { it.isBlocking }.toSet()
        assertEquals(
            setOf(
                ChainIssue.UNTRUSTED,
                ChainIssue.EXPIRED,
                ChainIssue.NOT_YET_VALID,
                ChainIssue.HOSTNAME_MISMATCH,
                ChainIssue.PIN_MISMATCH,
            ),
            blocking,
        )
    }

    @Test
    fun `advisory findings never make a trusted chain untrusted`() {
        ChainIssue.entries.filterNot { it.isBlocking }.forEach { issue ->
            val result = connectionResult(chainIssues = listOf(issue))
            assertEquals(true, result.isConnectionTrusted, "advisory $issue must not fail the verdict")
        }
    }

    private fun connectionResult(chainIssues: List<ChainIssue>) =
        TlsInspectorResult(
            host = "example.com",
            port = 443,
            tlsVersion = "TLSv1.3",
            cipherSuite = "TLS_AES_128_GCM_SHA256",
            chain = emptyList(),
            isChainTrusted = true,
            handshakeTimeMs = 10,
            hostnameMatches = true,
            chainIssues = chainIssues,
        )
}
