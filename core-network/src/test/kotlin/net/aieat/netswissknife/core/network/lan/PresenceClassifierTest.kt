package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresenceClassifierTest {
    @Test
    fun `positive endpoint evidence confirms a host`() {
        val decision = PresenceClassifier.classify(
            listOf(PresenceEvidence.TcpConnection(port = 443)),
        )

        assertTrue(decision.confirmed)
        assertEquals(setOf(DiscoveryMethod.TCP_OPEN), decision.methods)
    }

    @Test
    fun `no positive evidence cannot be confirmed even when failures repeat`() {
        // Refusals are deliberately not evidence accepted by this classifier.
        val decision = PresenceClassifier.classify(emptyList())

        assertFalse(decision.confirmed)
        assertTrue(decision.methods.isEmpty())
    }

    @Test
    fun `icmp evidence does not invent tcp refusal metadata`() {
        val decision = PresenceClassifier.classify(
            listOf(PresenceEvidence.IcmpEcho(rttMs = 8)),
        )

        assertTrue(decision.confirmed)
        assertEquals(setOf(DiscoveryMethod.ICMP), decision.methods)
        assertFalse(DiscoveryMethod.TCP_REFUSED in decision.methods)
    }

    @Test
    fun `arbitrary discovery methods cannot confirm through local protocol evidence`() {
        val decision = PresenceClassifier.classify(
            listOf(
                PresenceEvidence.LocalProtocolReply(DiscoveryMethod.RDNS, "stale.example"),
                PresenceEvidence.LocalProtocolReply(DiscoveryMethod.TCP_REFUSED, "not-a-host"),
            ),
        )

        assertFalse(decision.confirmed)
        assertTrue(decision.methods.isEmpty())
    }
}
