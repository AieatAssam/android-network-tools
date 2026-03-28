package net.aieat.netswissknife.core.network.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PingPacketResult")
class PingPacketResultTest {

    @Nested
    @DisplayName("construction")
    inner class Construction {

        @Test
        fun `success packet has non-null rtTimeMs`() {
            val packet = PingPacketResult(
                sequence = 1,
                host = "8.8.8.8",
                rtTimeMs = 12L,
                status = PingStatus.SUCCESS
            )
            assertEquals(12L, packet.rtTimeMs)
            assertEquals(PingStatus.SUCCESS, packet.status)
            assertNull(packet.errorMessage)
        }

        @Test
        fun `timeout packet has null rtTimeMs`() {
            val packet = PingPacketResult(
                sequence = 2,
                host = "8.8.8.8",
                rtTimeMs = null,
                status = PingStatus.TIMEOUT
            )
            assertNull(packet.rtTimeMs)
            assertEquals(PingStatus.TIMEOUT, packet.status)
        }

        @Test
        fun `error packet carries errorMessage`() {
            val packet = PingPacketResult(
                sequence = 3,
                host = "invalid.host",
                rtTimeMs = null,
                status = PingStatus.ERROR,
                errorMessage = "unknown host"
            )
            assertNull(packet.rtTimeMs)
            assertEquals(PingStatus.ERROR, packet.status)
            assertEquals("unknown host", packet.errorMessage)
        }

        @Test
        fun `sequence numbers are preserved`() {
            val packet = PingPacketResult(
                sequence = 42,
                host = "example.com",
                rtTimeMs = 5L,
                status = PingStatus.SUCCESS
            )
            assertEquals(42, packet.sequence)
        }

        @Test
        fun `host address is preserved`() {
            val packet = PingPacketResult(
                sequence = 1,
                host = "192.168.1.1",
                rtTimeMs = 1L,
                status = PingStatus.SUCCESS
            )
            assertEquals("192.168.1.1", packet.host)
        }
    }

    @Nested
    @DisplayName("equality")
    inner class Equality {

        @Test
        fun `two packets with same data are equal`() {
            val a = PingPacketResult(1, "host", 10L, PingStatus.SUCCESS)
            val b = PingPacketResult(1, "host", 10L, PingStatus.SUCCESS)
            assertEquals(a, b)
        }
    }
}
