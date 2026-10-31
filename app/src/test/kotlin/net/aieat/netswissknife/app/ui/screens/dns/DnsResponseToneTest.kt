package net.aieat.netswissknife.app.ui.screens.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DNS response tone")
class DnsResponseToneTest {
    @Test
    fun `NOERROR is a success`() {
        assertEquals(DnsResponseTone.SUCCESS, dnsResponseTone("NOERROR"))
    }

    @Test
    fun `NXDOMAIN is a neutral not-found, never a success`() {
        assertEquals(DnsResponseTone.NOT_FOUND, dnsResponseTone("NXDOMAIN"))
    }

    @Test
    fun `server failures and unknown codes are failures`() {
        assertEquals(DnsResponseTone.FAILURE, dnsResponseTone("SERVFAIL"))
        assertEquals(DnsResponseTone.FAILURE, dnsResponseTone("REFUSED"))
        assertEquals(DnsResponseTone.FAILURE, dnsResponseTone("SOMETHING_NEW"))
    }
}
