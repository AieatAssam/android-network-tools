package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PingRunOutcomeTest {
    private fun unresolved(): PingPacketResult =
        PingPacketResult(
            sequence = 1,
            host = "nosuchhost.invalid",
            rtTimeMs = null,
            status = PingStatus.ERROR,
            errorMessage = "No address associated with hostname",
            unresolvedHost = true,
        )

    private fun timeout(sequence: Int): PingPacketResult =
        PingPacketResult(
            sequence = sequence,
            host = "example.com",
            rtTimeMs = null,
            status = PingStatus.TIMEOUT,
            fromIp = "192.0.2.10",
        )

    @Test
    fun `a run whose only packet is an unresolved host is a resolution failure`() {
        assertTrue(isUnresolvedHostRun(listOf(unresolved())))
    }

    @Test
    fun `timeouts against a resolved address are a loss result, not a resolution failure`() {
        assertFalse(isUnresolvedHostRun(listOf(timeout(1), timeout(2))))
    }

    @Test
    fun `an empty run is not a resolution failure`() {
        assertFalse(isUnresolvedHostRun(emptyList()))
    }

    @Test
    fun `an unresolved packet followed by a resolved probe is not a resolution failure`() {
        assertFalse(isUnresolvedHostRun(listOf(unresolved(), timeout(2))))
    }

    @Test
    fun `an unresolved host is not remembered in recents`() {
        assertFalse(shouldRememberPingHost(unresolved()))
        assertTrue(shouldRememberPingHost(timeout(1)))
    }
}
