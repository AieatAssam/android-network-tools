package net.aieat.netswissknife.core.network.httprobe

import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimingBreakdown
import net.aieat.netswissknife.core.network.httprobe.engine.HttpTimings
import net.aieat.netswissknife.core.network.httprobe.engine.tcpConnectMs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HTTP Probe timing phases")
class HttpTimingPhasesTest {
    private val ms = 1_000_000L

    @Test
    fun `connect phase ends where TLS starts when the connect window contains the handshake`() {
        // Connect window 0..90 ms contains the TLS handshake 60..90 ms: TCP connect is 60 ms.
        assertEquals(60L, tcpConnectMs(connectStartNs = 0, connectEndNs = 90 * ms, tlsStartNs = 60 * ms))
    }

    @Test
    fun `connect phase is unchanged when TLS starts after TCP connect ends`() {
        assertEquals(50L, tcpConnectMs(connectStartNs = 0, connectEndNs = 50 * ms, tlsStartNs = 50 * ms))
        assertEquals(50L, tcpConnectMs(connectStartNs = 0, connectEndNs = 50 * ms, tlsStartNs = null))
    }

    @Test
    fun `connect phase is null without both connect events`() {
        assertNull(tcpConnectMs(connectStartNs = null, connectEndNs = 50 * ms, tlsStartNs = null))
        assertNull(tcpConnectMs(connectStartNs = 0, connectEndNs = null, tlsStartNs = null))
    }

    @Test
    fun `setup phases never sum above time to first byte`() {
        // Regression: example.com reported DNS 59 + Connect 209 + TLS 105 against a 319 ms TTFB.
        val timings = HttpTimings(dnsMs = 59, connectMs = 209, tlsMs = 105, ttfbMs = 319, transferMs = 8)
        val breakdown = HttpTimingBreakdown.from(timings)
        val setup = listOfNotNull(breakdown.dnsMs, breakdown.connectMs, breakdown.tlsMs, breakdown.serverWaitMs).sum()
        assertEquals(319L, setup)
        assertEquals(8L, breakdown.transferMs)
    }

    @Test
    fun `phases that fit within time to first byte are shown unchanged`() {
        val timings = HttpTimings(dnsMs = 5, connectMs = 10, tlsMs = 15, ttfbMs = 40, transferMs = 7)
        val breakdown = HttpTimingBreakdown.from(timings)
        assertEquals(5L, breakdown.dnsMs)
        assertEquals(10L, breakdown.connectMs)
        assertEquals(15L, breakdown.tlsMs)
        assertEquals(10L, breakdown.serverWaitMs)
    }
}
