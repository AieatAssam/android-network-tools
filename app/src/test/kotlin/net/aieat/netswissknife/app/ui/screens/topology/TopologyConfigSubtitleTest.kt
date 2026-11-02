package net.aieat.netswissknife.app.ui.screens.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopologyConfigSubtitleTest {
    @Test
    fun `collapsed header shows the target and SNMP version`() {
        assertEquals("10.109.208.1 · v2c", topologyConfigSubtitle("10.109.208.1", "v2c"))
    }

    @Test
    fun `no subtitle is shown until a target is entered`() {
        assertNull(topologyConfigSubtitle("  ", "v2c"))
    }
}
