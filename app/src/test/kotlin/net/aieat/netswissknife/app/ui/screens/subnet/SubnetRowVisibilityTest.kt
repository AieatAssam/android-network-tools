package net.aieat.netswissknife.app.ui.screens.subnet

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubnetRowVisibilityTest {
    @Test
    fun `ordinary subnets show broadcast and the host range`() {
        assertTrue(showsBroadcastRow(24))
        assertTrue(showsHostRange(24))
        assertTrue(showsBroadcastRow(30))
        assertTrue(showsHostRange(30))
    }

    @Test
    fun `slash 31 has no broadcast row but keeps both hosts`() {
        assertFalse(showsBroadcastRow(31))
        assertTrue(showsHostRange(31))
    }

    @Test
    fun `slash 32 shows neither broadcast nor a host range`() {
        assertFalse(showsBroadcastRow(32))
        assertFalse(showsHostRange(32))
    }
}
