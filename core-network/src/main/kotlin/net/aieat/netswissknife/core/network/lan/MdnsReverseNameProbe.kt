package net.aieat.netswissknife.core.network.lan

import org.xbill.DNS.Message
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Section
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** mDNS reverse-PTR lookup for IPv4 LAN hostnames. */
class MdnsReverseNameProbe(
    private val transport: UdpExchange = DefaultUdpExchange,
) : NameProbe {
    override suspend fun resolveName(ip: String, timeoutMs: Int): String? {
        val response = transport.exchange("224.0.0.251", 5353, buildPtrQuery(ip), timeoutMs.coerceAtMost(300))
            ?: return null
        return parsePtrResponse(response, reverseName(ip))
    }

    companion object {
        fun reverseName(ip: String): String {
            val parts = ip.split('.')
            require(parts.size == 4 && parts.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }) {
                "Expected an IPv4 address: $ip"
            }
            return parts.asReversed().joinToString(".") + ".in-addr.arpa."
        }

        /** Builds a zero-ID mDNS PTR query with the QU (unicast-response) bit set. */
        fun buildPtrQuery(ip: String): ByteArray {
            val labels = reverseName(ip).trimEnd('.').split('.')
            val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
            val query = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
            query.putShort(0)
            query.putShort(0)
            query.putShort(1)
            query.putShort(0)
            query.putShort(0)
            query.putShort(0)
            labels.forEach { label ->
                query.put(label.length.toByte())
                query.put(label.toByteArray(Charsets.US_ASCII))
            }
            query.put(0)
            query.putShort(12) // PTR
            query.putShort(0x8001.toShort()) // IN + unicast-response request
            return query.array()
        }

        fun parsePtrResponse(packet: ByteArray, expectedOwner: String): String? = try {
            val message = Message(packet)
            message.getSectionArray(Section.ANSWER)
                .asSequence()
                .filterIsInstance<PTRRecord>()
                .firstOrNull { it.name.toString().equals(expectedOwner, ignoreCase = true) }
                ?.target
                ?.toString()
                ?.trimEnd('.')
        } catch (_: Exception) {
            null
        }
    }
}
