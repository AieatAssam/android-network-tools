package net.aieat.netswissknife.app.ui.screens.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LanTopologyLabelTest {
    @Test
    fun `vendor is preferred over hostname`() {
        assertEquals("Apple", lanTopologyNodeName(vendor = "Apple", hostname = "mac.local", ip = "192.168.1.20"))
    }

    @Test
    fun `hostname first label is used when vendor is missing`() {
        assertEquals("printer", lanTopologyNodeName(vendor = null, hostname = "printer.lan", ip = "192.168.1.20"))
    }

    @Test
    fun `no vendor and no hostname gives no name so the octet is shown once`() {
        assertNull(lanTopologyNodeName(vendor = null, hostname = null, ip = "192.168.1.3"))
    }

    @Test
    fun `blank vendor and hostname count as missing`() {
        assertNull(lanTopologyNodeName(vendor = "  ", hostname = "", ip = "192.168.1.107"))
    }

    @Test
    fun `name equal to the last octet is dropped`() {
        assertNull(lanTopologyNodeName(vendor = null, hostname = "107.lan", ip = "192.168.1.107"))
    }

    @Test
    fun `long vendor is returned unchanged for the one-line ellipsis`() {
        val vendor = "Hon Hai Precision Industry Co., Ltd."
        assertEquals(vendor, lanTopologyNodeName(vendor = vendor, hostname = null, ip = "10.0.0.5"))
    }
}
