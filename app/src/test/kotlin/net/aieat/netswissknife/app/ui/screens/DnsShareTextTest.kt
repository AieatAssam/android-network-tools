package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DnsShareTextTest {
    @Test
    fun `golden text lists type, domain, server, query time and each record with TTL`() {
        val record =
            DnsRecord(
                type = DnsRecordType.A,
                name = "example.com",
                value = "93.184.216.34",
                ttl = 3600,
                rawLine = "",
            )
        val result =
            DnsResult(
                domain = "example.com",
                recordType = DnsRecordType.A,
                server = DnsServer.System(),
                records = listOf(record),
                queryTimeMs = 12,
                rawResponse = "",
            )
        val expected =
            listOf(
                "DNS A – example.com",
                "Server: System resolver",
                "Query time: 12ms",
                "",
                "A\t93.184.216.34\tTTL: 3600s",
            ).joinToString(separator = "\n", postfix = "\n")

        assertEquals(expected, buildDnsShareText(result, "Server: System resolver"))
    }

    @Test
    fun `golden text says so when there are no records`() {
        val result =
            DnsResult(
                domain = "missing.example",
                recordType = DnsRecordType.AAAA,
                server = DnsServer.System(),
                records = emptyList(),
                queryTimeMs = 4,
                rawResponse = "",
            )
        val expected =
            listOf(
                "DNS AAAA – missing.example",
                "Server: System resolver",
                "Query time: 4ms",
                "",
                "No records found.",
            ).joinToString(separator = "\n", postfix = "\n")

        assertEquals(expected, buildDnsShareText(result, "Server: System resolver"))
    }
}
