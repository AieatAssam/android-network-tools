package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type

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

    @Test
    fun `strict parser accepts matching answer without an echoed question`() {
        assertEquals(
            "myhost.local",
            MdnsReverseNameProbe.parsePtrPresenceResponse(response(question = false), "2.1.168.192.in-addr.arpa."),
        )
    }

    @Test
    fun `presence probe accepts a live matching multicast responder`() = runTest {
        val probe = MdnsReverseNameProbe(object : UdpExchange, CorrelatedUdpExchange {
            override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? = null
            override suspend fun exchangeCorrelated(
                ip: String,
                port: Int,
                payload: ByteArray,
                timeoutMs: Int,
                accepts: (CorrelatedUdpReply) -> Boolean,
            ): CorrelatedUdpReply? = CorrelatedUdpReply(
                sourceIp = "192.168.1.2",
                sourcePort = 5353,
                payload = response(question = true),
            ).takeIf(accepts)
        })

        val reply = probe.probePresence("192.168.1.2", 100)
        assertEquals(DiscoveryMethod.MDNS, reply?.method)
        assertEquals("myhost.local", reply?.name)
    }

    @Test
    fun `strict parser rejects goodbye records with zero ttl`() {
        assertNull(
            MdnsReverseNameProbe.parsePtrPresenceResponse(
                response(question = true, ttlSeconds = 0),
                "4.3.2.1.in-addr.arpa.",
            ),
        )
    }

    @Test
    fun `strict parser rejects DNS error responses`() {
        val message = Message(response(question = true))
        message.header.setRcode(Rcode.SERVFAIL)
        assertNull(
            MdnsReverseNameProbe.parsePtrPresenceResponse(
                message.toWire(),
                "2.1.168.192.in-addr.arpa.",
            ),
        )
    }

    @Test
    fun `presence probe requires scanned endpoint as responder`() = runTest {
        val probe = MdnsReverseNameProbe(object : UdpExchange, CorrelatedUdpExchange {
            override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? = null
            override suspend fun exchangeCorrelated(
                ip: String,
                port: Int,
                payload: ByteArray,
                timeoutMs: Int,
                accepts: (CorrelatedUdpReply) -> Boolean,
            ): CorrelatedUdpReply? = CorrelatedUdpReply(
                sourceIp = "192.168.1.3",
                sourcePort = 5353,
                payload = response(question = true),
            ).takeIf(accepts)
        })

        assertNull(probe.probePresence("192.168.1.2", 100))
    }

    private fun response(question: Boolean, ttlSeconds: Long = 60): ByteArray {
        val owner = Name.fromString("2.1.168.192.in-addr.arpa.")
        val message = Message(0)
        message.header.setFlag(Flags.QR.toInt())
        if (question) message.addRecord(Record.newRecord(owner, Type.PTR, DClass.IN), Section.QUESTION)
        message.addRecord(
            PTRRecord(owner, DClass.IN, ttlSeconds, Name.fromString("myhost.local.")),
            Section.ANSWER,
        )
        return message.toWire()
    }
}
