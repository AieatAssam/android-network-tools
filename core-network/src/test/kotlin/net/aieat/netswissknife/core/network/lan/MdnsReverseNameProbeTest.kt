package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Section

class MdnsReverseNameProbeTest {
    @Test
    fun `query contains reverse owner and QU bit`() {
        val query = MdnsReverseNameProbe.buildPtrQuery("1.2.3.4")
        val expectedOwner = byteArrayOf(
            1, '4'.code.toByte(), 1, '3'.code.toByte(), 1, '2'.code.toByte(), 1, '1'.code.toByte(),
            7, 'i'.code.toByte(), 'n'.code.toByte(), '-'.code.toByte(), 'a'.code.toByte(),
            'd'.code.toByte(), 'd'.code.toByte(), 'r'.code.toByte(), 4, 'a'.code.toByte(),
            'r'.code.toByte(), 'p'.code.toByte(), 'a'.code.toByte(), 0,
        )
        assertEquals(expectedOwner.toList(), query.sliceArray(12 until 12 + expectedOwner.size).toList())
        assertEquals(0x80, query[query.size - 2].toInt() and 0xFF)
        assertEquals(0x01, query[query.size - 1].toInt() and 0xFF)
    }

    @Test
    fun `reverse owner is formatted correctly`() {
        assertEquals("4.3.2.1.in-addr.arpa.", MdnsReverseNameProbe.reverseName("1.2.3.4"))
    }

    @Test
    fun `parser returns matching PTR target and ignores other owners`() {
        val message = Message()
        message.addRecord(
            PTRRecord(Name.fromString("other.in-addr.arpa."), DClass.IN, 60, Name.fromString("wrong.local.")),
            Section.ANSWER,
        )
        message.addRecord(
            PTRRecord(Name.fromString("4.3.2.1.in-addr.arpa."), DClass.IN, 60, Name.fromString("myhost.local.")),
            Section.ANSWER,
        )
        assertEquals(
            "myhost.local",
            MdnsReverseNameProbe.parsePtrResponse(message.toWire(), "4.3.2.1.in-addr.arpa."),
        )
    }
}
