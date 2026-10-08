package net.aieat.netswissknife.core.network.lan

import org.xbill.DNS.Message
import org.xbill.DNS.Opcode
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Section
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Rcode
import org.xbill.DNS.Type
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** mDNS reverse-PTR lookup for IPv4 LAN hostnames. */
class MdnsReverseNameProbe(
    private val transport: UdpExchange = DefaultUdpExchange,
) : PresenceNameProbe {
    override suspend fun resolveName(ip: String, timeoutMs: Int): String? = probePresence(ip, timeoutMs)?.name

    override suspend fun probePresence(ip: String, timeoutMs: Int): LocalProtocolReply? {
        val correlated = transport as? CorrelatedUdpExchange ?: return null
        val owner = reverseName(ip)
        var name: String? = null
        val reply = correlated.exchangeCorrelated(
            "224.0.0.251",
            5353,
            buildPtrQuery(ip),
            timeoutMs.coerceAtMost(300),
        ) { candidate ->
            if (!candidate.sourceIp.equals(ip, ignoreCase = true) || candidate.sourcePort != 5353) {
                false
            } else {
                name = parsePtrPresenceResponse(candidate.payload, owner)
                name != null
            }
        } ?: return null
        if (!reply.sourceIp.equals(ip, ignoreCase = true) || reply.sourcePort != 5353) return null
        return name?.let { LocalProtocolReply(DiscoveryMethod.MDNS, it) }
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

        fun parsePtrPresenceResponse(packet: ByteArray, expectedOwner: String): String? = try {
            val message = Message(packet)
            val questionCount = message.header.getCount(Section.QUESTION)
            if (
                !message.header.getFlag(Flags.QR.toInt()) ||
                    message.header.id != 0 ||
                    message.header.getOpcode() != Opcode.QUERY ||
                    message.header.getRcode() != Rcode.NOERROR
            ) {
                return null
            }
            if (questionCount > 1) return null
            if (questionCount == 1) {
                val question = message.getQuestion() ?: return null
                if (!question.name.toString().equals(expectedOwner, ignoreCase = true)) return null
                if (question.type != Type.PTR || question.dClass and 0x7FFF != DClass.IN) return null
            }
            message.getSectionArray(Section.ANSWER)
                .asSequence()
                .filterIsInstance<PTRRecord>()
                .firstOrNull {
                    it.name.toString().equals(expectedOwner, ignoreCase = true) &&
                        it.dClass and 0x7FFF == DClass.IN && it.ttl > 0
                }
                ?.target
                ?.toString()
                ?.trimEnd('.')
        } catch (_: Exception) {
            null
        }
    }
}
