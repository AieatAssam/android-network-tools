package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanHost
import net.aieat.netswissknife.core.network.lan.LanScanSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LanScanExpandTest {
    private val summary =
        LanScanSummary(
            subnet = "192.168.1.0/24",
            totalScanned = 254,
            aliveHosts = 1,
            scanDurationMs = 900,
            hosts = listOf(LanHost("192.168.1.7", null, null, null, emptyList(), pingTimeMs = 4L)),
        )

    @Test
    fun `revealing a collapsed host expands it`() {
        val state = expandHostState(LanScanUiState.Finished(summary), ip = "192.168.1.7")

        assertEquals("192.168.1.7", (state as LanScanUiState.Finished).expandedHostIp)
    }

    @Test
    fun `revealing a host that is already expanded keeps it expanded`() {
        val expanded = LanScanUiState.Finished(summary, expandedHostIp = "192.168.1.7")

        assertSame(expanded, expandHostState(expanded, ip = "192.168.1.7"))
    }

    @Test
    fun `revealing another host moves the expansion`() {
        val canceled = LanScanUiState.Canceled(summary, expandedHostIp = "192.168.1.2")
        val state = expandHostState(canceled, ip = "192.168.1.7")

        assertEquals("192.168.1.7", (state as LanScanUiState.Canceled).expandedHostIp)
    }

    @Test
    fun `states without a host list are returned unchanged`() {
        assertSame(LanScanUiState.Idle, expandHostState(LanScanUiState.Idle, ip = "192.168.1.7"))
    }
}
