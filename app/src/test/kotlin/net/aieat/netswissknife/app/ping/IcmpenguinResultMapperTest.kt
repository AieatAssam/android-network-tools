package net.aieat.netswissknife.app.ping

import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IcmpenguinResultMapperTest {
    @Test
    fun `success keeps microsecond timing and reply metadata`() {
        val packet = IcmpenguinResultMapper.toPacket(
            "example.com",
            IcmpProbe.Success(1, "192.0.2.1", 56, 12_345, 57)
        )

        assertEquals(PingStatus.SUCCESS, packet.status)
        assertEquals(12L, packet.rtTimeMs)
        assertEquals(12_345, packet.rtTimeMicros)
        assertEquals(57, packet.replyTtl)
        assertEquals("192.0.2.1", packet.fromIp)
    }

    @Test
    fun `timeout and unreachable map to honest statuses`() {
        val timeout = IcmpenguinResultMapper.toPacket(
            "host", IcmpProbe.Timeout(1, "192.0.2.1", 56)
        )
        val unreachable = IcmpenguinResultMapper.toPacket(
            "host", IcmpProbe.Unreachable(2, "192.0.2.254", 56, "TTL exceeded")
        )

        assertEquals(PingStatus.TIMEOUT, timeout.status)
        assertEquals(PingStatus.UNREACHABLE, unreachable.status)
        assertEquals("TTL exceeded", unreachable.errorMessage)
    }
}
