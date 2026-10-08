package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NetBiosNameProbeTest {
    @Test
    fun `wildcard query uses RFC 1002 first level encoding`() {
        val query = NetBiosNameProbe.buildNbstatQuery(0x1234)
        val encoded = query.copyOfRange(13, 45).toString(Charsets.US_ASCII)
        assertEquals("CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", encoded)
        assertEquals(0x21, query[47].toInt() and 0xFF)
        assertEquals(0x01, query[49].toInt() and 0xFF)
    }

    @Test
    fun `response returns first unique workstation name`() {
        val packet = nbstatResponse("PC-1")
        assertEquals("PC-1", NetBiosNameProbe.parseNbstatResponse(packet))
    }

    @Test
    fun `truncated response returns null`() {
        assertNull(NetBiosNameProbe.parseNbstatResponse(byteArrayOf(0, 1, 0)))
    }

    @Test
    fun `strict presence parser accepts standard response with no question section`() {
        assertEquals("PC-1", NetBiosNameProbe.parseNbstatPresenceResponse(presenceNbstatResponse(0x4E53), 0x4E53))
    }

    @Test
    fun `strict presence parser skips group entries and returns the first unique name`() {
        val packet = presenceNbstatResponse(
            0x4E53,
            entries = listOf("GROUP" to 0x8000, "PC-2" to 0),
        )
        assertEquals("PC-2", NetBiosNameProbe.parseNbstatPresenceResponse(packet, 0x4E53))
    }

    @Test
    fun `strict presence parser does not treat group names as unique hosts`() {
        val packet = presenceNbstatResponse(0x4E53, entries = listOf("GROUP" to 0x8000))
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(packet, 0x4E53))
    }

    @Test
    fun `strict presence parser rejects a stale transaction id`() {
        val packet = presenceNbstatResponse(0x1234)
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(packet, 0x9999))
    }

    @Test
    fun `strict presence parser rejects truncated answers and invalid compression pointers`() {
        val valid = presenceNbstatResponse(0x4E53)
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(valid.copyOf(valid.size - 1), 0x4E53))

        val countMismatch = valid.copyOf().apply {
            this[6] = 0
            this[7] = 2
        }
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(countMismatch, 0x4E53))

        val invalidPointer = valid.copyOf().apply {
            this[12] = 0xC0.toByte()
            this[13] = 0x7F
        }
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(invalidPointer, 0x4E53))
    }

    @Test
    fun `strict presence parser rejects non-response opcode and rcode`() {
        val query = NetBiosNameProbe.buildNbstatQuery()
        assertNull(NetBiosNameProbe.parseNbstatPresenceResponse(query, 0x4E53))
    }

    @Test
    fun `presence probe requires correlated source address and port`() = runTest {
        val response = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
            .putShort(0x4E53)
            .putShort(0x8400.toShort())
            .putShort(0)
            .putShort(1)
            .putShort(0)
            .putShort(0)
            .putShort(0xC00C.toShort())
            .putShort(0x0021)
            .putShort(0x0001)
            .putInt(0)
            .putShort(19)
            .put(1)
            .put("PC-1".padEnd(15, ' ').toByteArray(Charsets.US_ASCII))
            .putShort(0)
            .array()
        val probe = NetBiosNameProbe(object : UdpExchange, CorrelatedUdpExchange {
            override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray = response
            override suspend fun exchangeCorrelated(
                ip: String,
                port: Int,
                payload: ByteArray,
                timeoutMs: Int,
                accepts: (CorrelatedUdpReply) -> Boolean,
            ): CorrelatedUdpReply? = CorrelatedUdpReply("192.168.1.9", 137, response).takeIf(accepts)
        })

        assertNull(probe.probePresence("192.168.1.2", 100))
    }

    @Test
    fun `presence probe accepts a matching target reply and query id`() = runTest {
        val probe = NetBiosNameProbe(object : UdpExchange, CorrelatedUdpExchange {
            override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? = null
            override suspend fun exchangeCorrelated(
                ip: String,
                port: Int,
                payload: ByteArray,
                timeoutMs: Int,
                accepts: (CorrelatedUdpReply) -> Boolean,
            ): CorrelatedUdpReply? {
                val transactionId = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                return CorrelatedUdpReply(
                    sourceIp = ip,
                    sourcePort = 137,
                    payload = presenceNbstatResponse(transactionId),
                ).takeIf(accepts)
            }
        })

        val reply = probe.probePresence("192.168.1.2", 100)
        assertEquals(DiscoveryMethod.NETBIOS, reply?.method)
        assertEquals("PC-1", reply?.name)
    }

    @Test
    fun `presence probe rejects a response from the wrong source port`() = runTest {
        val probe = NetBiosNameProbe(object : UdpExchange, CorrelatedUdpExchange {
            override fun exchange(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? = null
            override suspend fun exchangeCorrelated(
                ip: String,
                port: Int,
                payload: ByteArray,
                timeoutMs: Int,
                accepts: (CorrelatedUdpReply) -> Boolean,
            ): CorrelatedUdpReply? {
                val transactionId = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                val packet = presenceNbstatResponse(transactionId)
                return CorrelatedUdpReply("192.168.1.2", 138, packet).takeIf(accepts)
            }
        })

        assertNull(probe.probePresence("192.168.1.2", 100))
    }

    @Test
    fun `transport timeout returns null`() = runTest {
        val probe = NetBiosNameProbe { _, _, _, _ -> null }
        assertNull(probe.resolveName("192.168.1.2", 100))
    }

    private fun nbstatResponse(name: String, transactionId: Int = 0x1234): ByteArray {
        val query = NetBiosNameProbe.buildNbstatQuery(transactionId)
        val buffer = ByteBuffer.allocate(query.size + 31).order(ByteOrder.BIG_ENDIAN)
        buffer.put(query.copyOfRange(0, 6))
        buffer.putShort(1) // answers
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(query.copyOfRange(12, query.size))
        buffer.putShort(0xC00C.toShort())
        buffer.putShort(0x0021)
        buffer.putShort(0x0001)
        buffer.putInt(0)
        buffer.putShort(19)
        buffer.put(1)
        buffer.put(name.padEnd(15, ' ').take(15).toByteArray(Charsets.US_ASCII))
        buffer.put(0) // NetBIOS name suffix
        buffer.putShort(0)
        return buffer.array()
    }

    private fun presenceNbstatResponse(
        transactionId: Int,
        entries: List<Pair<String, Int>> = listOf("PC-1" to 0),
    ): ByteArray {
        val rdataLength = 1 + entries.size * 18
        val owner = NetBiosNameProbe.buildNbstatQuery(transactionId).copyOfRange(12, 46)
        val buffer = ByteBuffer.allocate(12 + owner.size + 10 + rdataLength).order(ByteOrder.BIG_ENDIAN)
            .putShort(transactionId.toShort())
            .putShort(0x8400.toShort()) // response, authoritative, opcode 0, rcode 0
            .putShort(0) // NBSTAT responses may omit the question section
            .putShort(1) // answers
            .putShort(0)
            .putShort(0)
            .put(owner)
            .putShort(0x0021)
            .putShort(0x0001)
            .putInt(0)
            .putShort(rdataLength.toShort())
            .put(entries.size.toByte())
        entries.forEach { (name, flags) ->
            buffer.put(name.padEnd(15, ' ').take(15).toByteArray(Charsets.US_ASCII))
            buffer.put(0) // NetBIOS name suffix
            buffer.putShort(flags.toShort())
        }
        return buffer.array()
    }
}
