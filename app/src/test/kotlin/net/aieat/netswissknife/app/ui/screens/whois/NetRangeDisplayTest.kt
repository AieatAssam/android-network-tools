package net.aieat.netswissknife.app.ui.screens.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetRangeDisplayTest {
    @Test
    fun `a single ASN range shows the one number once`() {
        assertEquals("15169", netRangeDisplay("15169-15169"))
    }

    @Test
    fun `a single IP address range shows the address once`() {
        assertEquals("1.1.1.1", netRangeDisplay("1.1.1.1-1.1.1.1"))
    }

    @Test
    fun `a multi-number ASN range is kept whole`() {
        assertEquals("15169-15170", netRangeDisplay("15169-15170"))
    }

    @Test
    fun `an IP block is kept whole`() {
        assertEquals("8.8.8.0-8.8.8.255", netRangeDisplay("8.8.8.0-8.8.8.255"))
    }
}
