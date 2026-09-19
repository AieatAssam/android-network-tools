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
    fun `transport timeout returns null`() = runTest {
        val probe = NetBiosNameProbe { _, _, _, _ -> null }
        assertNull(probe.resolveName("192.168.1.2", 100))
    }

    private fun nbstatResponse(name: String): ByteArray {
        val query = NetBiosNameProbe.buildNbstatQuery(0x1234)
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
        buffer.putShort(0)
        return buffer.array()
    }
}
