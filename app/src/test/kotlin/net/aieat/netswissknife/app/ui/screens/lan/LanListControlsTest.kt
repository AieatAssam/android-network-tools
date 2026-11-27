package net.aieat.netswissknife.app.ui.screens.lan

import net.aieat.netswissknife.core.network.lan.LanScanDiagnostic
import net.aieat.netswissknife.core.network.lan.LanScanDiagnosticReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanListControlsTest {
    @Test
    fun `pressing the active filter again turns it off`() {
        assertEquals(LanHostFilter.All, lanFilterAfterTap(LanHostFilter.Gateway, LanHostFilter.Gateway))
        assertEquals(LanHostFilter.All, lanFilterAfterTap(LanHostFilter.HasPorts, LanHostFilter.HasPorts))
        assertEquals(LanHostFilter.All, lanFilterAfterTap(LanHostFilter.All, LanHostFilter.All))
    }

    @Test
    fun `pressing another filter selects it`() {
        assertEquals(LanHostFilter.HasPorts, lanFilterAfterTap(LanHostFilter.Gateway, LanHostFilter.HasPorts))
        assertEquals(LanHostFilter.Gateway, lanFilterAfterTap(LanHostFilter.All, LanHostFilter.Gateway))
    }

    @Test
    fun `uncertain probes are listed in address order`() {
        val finishOrder = listOf("10.0.0.5", "10.0.0.4", "10.0.0.36", "10.0.0.2", "10.0.0.107")
        val diagnostics = finishOrder.map { LanScanDiagnostic(it, LanScanDiagnosticReason.TCP_TIMED_OUT, 80) }

        assertEquals(
            listOf("10.0.0.2", "10.0.0.4", "10.0.0.5", "10.0.0.36", "10.0.0.107"),
            lanDiagnosticsInIpOrder(diagnostics).map { it.ip },
        )
    }
}
