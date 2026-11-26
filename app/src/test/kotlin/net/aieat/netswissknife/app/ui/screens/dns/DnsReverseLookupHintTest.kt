package net.aieat.netswissknife.app.ui.screens.dns

import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsReverseLookupHintTest {
    private fun result(
        domain: String,
        type: DnsRecordType,
        records: List<DnsRecord> = emptyList(),
        rcode: String = "NXDOMAIN",
    ) = DnsResult(
        domain = domain,
        recordType = type,
        server = DnsServer.Cloudflare,
        records = records,
        queryTimeMs = 5,
        rawResponse = "",
        rcode = rcode,
    )

    @Test
    fun `an IP address with no answer for a forward type suggests a reverse lookup`() {
        assertTrue(dnsSuggestsReverseLookup(result("1.1.1.1", DnsRecordType.A)))
        assertTrue(dnsSuggestsReverseLookup(result("2606:4700:4700::1111", DnsRecordType.AAAA)))
        assertTrue(dnsSuggestsReverseLookup(result("8.8.8.8", DnsRecordType.MX, rcode = "NOERROR")))
    }

    @Test
    fun `a PTR lookup, a name, or an answered query suggests nothing`() {
        assertFalse(dnsSuggestsReverseLookup(result("1.1.1.1", DnsRecordType.PTR)))
        assertFalse(dnsSuggestsReverseLookup(result("example.com", DnsRecordType.A)))
        val answer = DnsRecord(DnsRecordType.A, "1.1.1.1", "1.1.1.1", 60, "")
        assertFalse(dnsSuggestsReverseLookup(result("1.1.1.1", DnsRecordType.A, listOf(answer), "NOERROR")))
    }
}
