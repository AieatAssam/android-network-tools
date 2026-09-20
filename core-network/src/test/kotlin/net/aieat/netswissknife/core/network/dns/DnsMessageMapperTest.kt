package net.aieat.netswissknife.core.network.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type

class DnsMessageMapperTest {

    @Test
    fun `maps actual answer types rather than requested type`() {
        val response = Message.newQuery(query("www.example.com.", Type.A))
        response.addRecord(
            Record.fromString(
                Name.fromString("www.example.com."), Type.CNAME, DClass.IN, 60,
                "host.example.com.", Name.root
            ), Section.ANSWER
        )
        response.addRecord(
            Record.fromString(
                Name.fromString("host.example.com."), Type.A, DClass.IN, 60,
                "93.184.216.34", Name.root
            ), Section.ANSWER
        )

        val result = map(response, DnsRecordType.A)

        assertEquals(listOf("CNAME", "A"), result.records.map { it.rrTypeName })
        assertEquals(listOf(DnsRecordType.CNAME, DnsRecordType.A), result.records.map { it.type })
    }

    @Test
    fun `maps NXDOMAIN authority and header flags`() {
        val response = Message.newQuery(query("missing.example.com.", Type.A))
        response.header.setRcode(Rcode.NXDOMAIN)
        response.header.setFlag(Flags.AA.toInt())
        response.header.setFlag(Flags.RD.toInt())
        response.header.setFlag(Flags.RA.toInt())
        response.addRecord(
            Record.fromString(
                Name.fromString("example.com."), Type.SOA, DClass.IN, 300,
                "ns1.example.com. hostmaster.example.com. 1 3600 600 86400 300", Name.root
            ), Section.AUTHORITY
        )

        val result = map(response, DnsRecordType.A)

        assertEquals("NXDOMAIN", result.rcode)
        assertEquals(setOf("AA", "RD", "RA"), result.flags)
        assertTrue(result.records.isEmpty())
        assertEquals(DnsSection.AUTHORITY, result.authority.single().section)
    }

    @Test
    fun `maps server used and unknown type`() {
        val response = Message.newQuery(query("example.com.", Type.A))
        response.addRecord(
            Record.fromString(
                Name.fromString("example.com."), 65, DClass.IN, 60,
                "1 2", Name.root
            ), Section.ANSWER
        )

        val result = map(response, DnsRecordType.A)

        assertEquals("8.8.8.8:53", result.serverUsed)
        assertEquals("HTTPS", result.records.single().rrTypeName)
        assertEquals(null, result.records.single().type)
    }

    private fun query(name: String, type: Int): Record =
        Record.newRecord(Name.fromString(name), type, DClass.IN)

    private fun map(response: Message, requestedType: DnsRecordType): DnsResult =
        DnsMessageMapper.toResult(
            domain = "example.com",
            requestedType = requestedType,
            server = DnsServer.Google,
            response = response,
            serverUsed = "8.8.8.8:53",
            queryTimeMs = 12
        )
}
