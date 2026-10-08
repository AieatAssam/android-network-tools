package net.aieat.netswissknife.core.network.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DnsServer")
class DnsServerTest {

    @Nested
    @DisplayName("well-known server IPs")
    inner class ServerIps {

        @Test
        fun `Google primary is 8_8_8_8`() {
            assertEquals("8.8.8.8", DnsServer.Google.PRIMARY)
        }

        @Test
        fun `Google secondary is 8_8_4_4`() {
            assertEquals("8.8.4.4", DnsServer.Google.SECONDARY)
        }

        @Test
        fun `Cloudflare primary is 1_1_1_1`() {
            assertEquals("1.1.1.1", DnsServer.Cloudflare.PRIMARY)
        }

        @Test
        fun `Cloudflare secondary is 1_0_0_1`() {
            assertEquals("1.0.0.1", DnsServer.Cloudflare.SECONDARY)
        }

        @Test
        fun `OpenDns primary is 208_67_222_222`() {
            assertEquals("208.67.222.222", DnsServer.OpenDns.PRIMARY)
        }

        @Test
        fun `Quad9 primary is 9_9_9_9`() {
            assertEquals("9.9.9.9", DnsServer.Quad9.PRIMARY)
        }
    }

    @Nested
    @DisplayName("presets list")
    inner class Presets {

        @Test
        fun `presets contains exactly five entries`() {
            assertEquals(5, DnsServer.presets.size)
        }

        @Test
        fun `presets includes System`() {
            assertTrue(DnsServer.presets.any { it is DnsServer.System })
        }

        @Test
        fun `presets includes Google`() {
            assertTrue(DnsServer.presets.any { it is DnsServer.Google })
        }

        @Test
        fun `presets includes Cloudflare`() {
            assertTrue(DnsServer.presets.any { it is DnsServer.Cloudflare })
        }

        @Test
        fun `presets includes OpenDns`() {
            assertTrue(DnsServer.presets.any { it is DnsServer.OpenDns })
        }

        @Test
        fun `presets includes Quad9`() {
            assertTrue(DnsServer.presets.any { it is DnsServer.Quad9 })
        }

    }

    @Nested
    @DisplayName("Custom equality")
    inner class CustomEquality {

        @Test
        fun `two Custom instances with same address are equal`() {
            assertEquals(DnsServer.Custom("1.2.3.4"), DnsServer.Custom("1.2.3.4"))
        }

        @Test
        fun `Custom address is accessible`() {
            val custom = DnsServer.Custom("10.10.10.10")
            assertEquals("10.10.10.10", custom.address)
        }
    }
}
