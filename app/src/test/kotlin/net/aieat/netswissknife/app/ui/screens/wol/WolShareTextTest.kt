package net.aieat.netswissknife.app.ui.screens.wol

import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant

class WolShareTextTest {
    private val report =
        WolSendReport(
            macAddress = "AA:BB:CC:DD:EE:FF",
            broadcastAddress = "192.168.1.255",
            port = 9,
            packetsSent = 3,
        )

    @Test
    fun `golden text lists target, broadcast, port, packets and the injected time`() {
        val text = buildWolShareText(report, Instant.parse("2026-11-01T10:15:30.987Z"))
        val expected =
            listOf(
                "Wake-on-LAN – magic packet sent",
                "Target MAC: AA:BB:CC:DD:EE:FF",
                "Broadcast: 192.168.1.255",
                "UDP port: 9",
                "Packets sent: 3",
                "Generated: 2026-11-01T10:15:30Z",
                "",
                "Wake-on-LAN is fire-and-forget: no delivery confirmation is returned.",
                "",
                "Net Swiss Knife",
            ).joinToString(separator = "\n", postfix = "\n")

        assertEquals(expected, text)
    }

    @Test
    fun `text carries no credential or SecureOn field`() {
        val text = buildWolShareText(report, Instant.EPOCH)

        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("secureon", ignoreCase = true))
    }
}
