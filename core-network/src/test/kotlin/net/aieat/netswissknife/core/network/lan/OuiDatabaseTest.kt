package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OuiDatabase")
class OuiDatabaseTest {

    @Test
    fun `resolves a known OUI prefix to its vendor`() {
        assertEquals("Raspberry Pi Foundation", OuiDatabase.lookup("B8:27:EB:11:22:33"))
    }

    @Test
    fun `is case-insensitive on the MAC address`() {
        assertEquals("Raspberry Pi Foundation", OuiDatabase.lookup("b8:27:eb:11:22:33"))
    }

    @Test
    fun `returns null for an unknown OUI prefix`() {
        assertNull(OuiDatabase.lookup("00:00:00:11:22:33"))
    }

    @Test
    fun `returns null for a MAC address shorter than an OUI prefix`() {
        assertNull(OuiDatabase.lookup("B8:27"))
    }

    @Test
    fun `ignores octets beyond the OUI prefix`() {
        // Two devices from the same vendor differ only past the first three octets.
        assertEquals(
            OuiDatabase.lookup("B8:27:EB:AA:AA:AA"),
            OuiDatabase.lookup("B8:27:EB:BB:BB:BB")
        )
    }
}
