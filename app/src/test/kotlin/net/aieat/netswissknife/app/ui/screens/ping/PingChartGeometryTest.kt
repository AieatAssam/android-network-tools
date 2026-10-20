package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingChartGeometryTest {
    @Test
    fun `adjacent successful samples share a line segment`() {
        val packets = listOf(
            packet(1, 12L, PingStatus.SUCCESS),
            packet(2, 24L, PingStatus.SUCCESS),
            packet(3, 48L, PingStatus.SUCCESS),
        )

        assertEquals(
            listOf(listOf(PingChartSample(0, 12L), PingChartSample(1, 24L), PingChartSample(2, 48L))),
            pingChartSuccessSegments(packets),
        )
    }

    @Test
    fun `failed or missing samples split successful line segments without shifting packet positions`() {
        val packets = listOf(
            packet(1, 12L, PingStatus.SUCCESS),
            packet(2, null, PingStatus.TIMEOUT),
            packet(3, 48L, PingStatus.SUCCESS),
            packet(4, 60L, PingStatus.UNREACHABLE),
            packet(5, null, PingStatus.SUCCESS),
            packet(6, 72L, PingStatus.SUCCESS),
            packet(7, null, PingStatus.ERROR),
        )

        assertEquals(
            listOf(
                listOf(PingChartSample(0, 12L)),
                listOf(PingChartSample(2, 48L)),
                listOf(PingChartSample(5, 72L)),
            ),
            pingChartSuccessSegments(packets),
        )
    }

    private fun packet(index: Int, rttMs: Long?, status: PingStatus) =
        PingPacketResult(index, "example.com", rttMs, status)
}
