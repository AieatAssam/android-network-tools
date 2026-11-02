package net.aieat.netswissknife.app.ui.screens.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResolvedIpToShowTest {
    @Test
    fun `an IP target is not repeated as its own resolved address`() {
        assertNull(resolvedIpToShow(host = "10.109.208.1", resolvedIp = "10.109.208.1"))
    }

    @Test
    fun `a hostname shows the address it resolved to`() {
        assertEquals("93.184.216.34", resolvedIpToShow(host = "example.com", resolvedIp = "93.184.216.34"))
    }

    @Test
    fun `no resolved address means nothing to show`() {
        assertNull(resolvedIpToShow(host = "example.com", resolvedIp = null))
    }

    @Test
    fun `case and surrounding spaces do not make a host look different`() {
        assertNull(resolvedIpToShow(host = " Example.COM ", resolvedIp = "example.com"))
    }
}
