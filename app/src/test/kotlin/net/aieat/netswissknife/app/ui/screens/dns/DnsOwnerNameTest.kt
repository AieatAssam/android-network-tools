package net.aieat.netswissknife.app.ui.screens.dns

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DnsOwnerNameTest {
    @Test
    @DisplayName("Owner equal to the queried name is hidden")
    fun sameNameIsHidden() {
        assertFalse(dnsShowsOwner("example.com", "example.com"))
    }

    @Test
    @DisplayName("Case and trailing root dot are ignored")
    fun caseAndTrailingDotAreIgnored() {
        assertFalse(dnsShowsOwner("Example.com.", "example.com"))
    }

    @Test
    @DisplayName("Different owner name is shown")
    fun differentNameIsShown() {
        assertTrue(dnsShowsOwner("www.example.com", "example.com"))
    }
}
