package net.aieat.netswissknife.app.ui.screens.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WHOIS status labels")
class WhoisLabelsTest {
    @Test
    @DisplayName("RDAP statuses with spaces, as served by rdap.verisign.com, get labels")
    fun `RDAP spaced statuses get human labels`() {
        assertEquals("Delete Locked", whoisStatusLabel("client delete prohibited"))
        assertEquals("Transfer Locked", whoisStatusLabel("client transfer prohibited"))
        assertEquals("Update Locked", whoisStatusLabel("client update prohibited"))
    }

    @Test
    @DisplayName("WHOIS EPP codes with the trailing ICANN URL get labels")
    fun `EPP camel case statuses get human labels`() {
        assertEquals(
            "Delete Locked",
            whoisStatusLabel("clientDeleteProhibited https://icann.org/epp#clientDeleteProhibited"),
        )
        assertEquals("Transfer Locked", whoisStatusLabel("clientTransferProhibited"))
        assertEquals("Server Transfer Locked", whoisStatusLabel("serverTransferProhibited"))
    }

    @Test
    @DisplayName("unlisted statuses keep their words, never an empty label")
    fun `unknown statuses keep their words`() {
        assertEquals("Pending transfer", whoisStatusLabel("pending transfer"))
        assertEquals("Delete prohibited", whoisStatusLabel("serverDeleteProhibited"))
        assertEquals("Ok", whoisStatusLabel("ok"))
    }

    @Test
    @DisplayName("a client status with spaces is labelled by its full phrase")
    fun `client hold with spaces`() {
        assertEquals("Client Hold", whoisStatusLabel("client hold"))
    }
}
