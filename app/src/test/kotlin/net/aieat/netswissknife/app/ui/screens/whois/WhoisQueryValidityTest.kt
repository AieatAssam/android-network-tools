package net.aieat.netswissknife.app.ui.screens.whois

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhoisQueryValidityTest {
    @Test
    fun `a blank query is not reported as invalid before the user types`() {
        assertFalse(isWhoisQueryInvalid(""))
        assertFalse(isWhoisQueryInvalid("   "))
    }

    @Test
    fun `domains, addresses and ASNs are valid`() {
        assertFalse(isWhoisQueryInvalid("example.com"))
        assertFalse(isWhoisQueryInvalid("8.8.8.8"))
        assertFalse(isWhoisQueryInvalid("AS15169"))
    }

    @Test
    fun `a query with spaces or symbols is invalid as the user types`() {
        assertTrue(isWhoisQueryInvalid("bad domain!!"))
    }
}
