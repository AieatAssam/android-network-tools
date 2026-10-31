package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("dnsRecordIconLabel")
class DnsRecordIconLabelTest {
    @Test
    fun `known record types use their display name`() {
        val record = DnsRecord(DnsRecordType.MX, "example.com", "10 mail.example.com", 300, "")
        assertEquals("MX", dnsRecordIconLabel(record))
    }

    @Test
    fun `OPT pseudo-record is labelled OPT rather than the TXT fallback`() {
        val opt = DnsRecord(type = null, name = "", value = "", ttl = 0, rawLine = "", rrTypeName = "OPT")
        assertEquals("OPT", dnsRecordIconLabel(opt))
    }
}
