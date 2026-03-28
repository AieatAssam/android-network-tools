package net.aieat.netswissknife.core.network.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DnsRecord")
class DnsRecordTest {

    @Test
    fun `all fields are accessible`() {
        val record = DnsRecord(
            type    = DnsRecordType.A,
            name    = "example.com",
            value   = "93.184.216.34",
            ttl     = 300L,
            rawLine = "example.com. 300 IN A 93.184.216.34"
        )

        assertEquals(DnsRecordType.A, record.type)
        assertEquals("example.com", record.name)
        assertEquals("93.184.216.34", record.value)
        assertEquals(300L, record.ttl)
        assertEquals("example.com. 300 IN A 93.184.216.34", record.rawLine)
    }

    @Test
    fun `data class equality works`() {
        val a = DnsRecord(DnsRecordType.AAAA, "ipv6.test", "::1", 60L, "raw")
        val b = DnsRecord(DnsRecordType.AAAA, "ipv6.test", "::1", 60L, "raw")
        assertEquals(a, b)
    }

    @Test
    fun `copy produces distinct instance with modified field`() {
        val original = DnsRecord(DnsRecordType.MX, "mail.example.com", "10 mx.example.com", 3600L, "raw")
        val copy = original.copy(ttl = 7200L)
        assertEquals(7200L, copy.ttl)
        assertEquals(original.value, copy.value)
    }
}
