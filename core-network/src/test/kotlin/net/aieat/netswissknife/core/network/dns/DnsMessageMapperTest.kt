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
import org.xbill.DNS.TXTRecord
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

    @Test
    fun `concatenates split TXT strings without changing raw presentation`() {
        val response = Message.newQuery(query("example.com.", Type.TXT))
        val txt = TXTRecord(
            Name.fromString("example.com."), DClass.IN, 60,
            listOf("v=DKIM1; k=rsa; p=ABC", "DEF"),
        )
        response.addRecord(txt, Section.ANSWER)

        val mapped = map(response, DnsRecordType.TXT).records.single()

        assertEquals("v=DKIM1; k=rsa; p=ABCDEF", mapped.value)
        assertEquals(txt.toString(), mapped.rawLine)
    }

    @Test
    fun `decodes TXT bytes as UTF8 while preserving escaped presentation`() {
        val response = Message.newQuery(query("example.com.", Type.TXT))
        val txt = TXTRecord(
            Name.fromString("example.com."), DClass.IN, 60,
            listOf("escaped=\"quote\";", "café"),
        )
        response.addRecord(txt, Section.ANSWER)

        val mapped = map(response, DnsRecordType.TXT).records.single()

        assertEquals("escaped=\"quote\";café", mapped.value)
        assertEquals(txt.toString(), mapped.rawLine)
        assertTrue(mapped.rawLine.contains("\\\"quote\\\""))
        assertTrue(mapped.rawLine.contains("caf\\195\\169"))
    }

    @Test
    fun `replaces malformed UTF8 in TXT character strings`() {
        // One TXT character-string contains C3 28: an incomplete UTF-8 sequence followed by '('.
        val wire = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(), // header ID, response flags
            0, 0, 0, 1, 0, 0, 0, 0, // no question, one answer, no authority/additional records
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0, Type.TXT.toByte(), 0, DClass.IN.toByte(),
            0, 0, 0, 60, // TTL
            0, 3, // RDLENGTH: one length byte and two string octets
            2, 0xC3.toByte(), 0x28,
        )

        val response = Message(wire)
        val parsedTxt = response.getSection(Section.ANSWER).single()
        val result = map(response, DnsRecordType.TXT)

        assertEquals("\uFFFD(", result.records.single().value)
        assertEquals(parsedTxt.toString(), result.records.single().rawLine)
    }

    @Test
    fun `decodes UTF8 character split across TXT string boundary`() {
        // The UTF-8 bytes for é are deliberately divided between the two TXT strings.
        val wire = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(), // header ID, response flags
            0, 0, 0, 1, 0, 0, 0, 0, // no question, one answer, no authority/additional records
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0, Type.TXT.toByte(), 0, DClass.IN.toByte(),
            0, 0, 0, 60, // TTL
            0, 4, // RDLENGTH: two length bytes and two string octets
            1, 0xC3.toByte(), 1, 0xA9.toByte(),
        )

        val result = map(Message(wire), DnsRecordType.TXT)

        assertEquals("é", result.records.single().value)
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
