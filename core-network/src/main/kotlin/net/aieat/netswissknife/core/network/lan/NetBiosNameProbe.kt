package net.aieat.netswissknife.core.network.lan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom

/** RFC 1002 NBSTAT name query against a host's UDP/137 endpoint. */
class NetBiosNameProbe(
    private val transport: UdpExchange = DefaultUdpExchange,
) : PresenceNameProbe {
    override suspend fun resolveName(ip: String, timeoutMs: Int): String? = probePresence(ip, timeoutMs)?.name

    override suspend fun probePresence(ip: String, timeoutMs: Int): LocalProtocolReply? {
        val correlated = transport as? CorrelatedUdpExchange ?: return null
        val transactionId = ThreadLocalRandom.current().nextInt(0x10000)
        val query = buildNbstatQuery(transactionId)
        var name: String? = null
        correlated.exchangeCorrelated(
            ip,
            137,
            query,
            timeoutMs.coerceAtMost(300),
        ) { candidate ->
            if (!candidate.sourceIp.equals(ip, ignoreCase = true) || candidate.sourcePort != 137) {
                false
            } else {
                name = parseNbstatPresenceResponse(candidate.payload, transactionId)
                name != null
            }
        } ?: return null
        return name?.let { LocalProtocolReply(DiscoveryMethod.NETBIOS, it) }
    }

    companion object {
        fun buildNbstatQuery(transactionId: Int = 0x4E53): ByteArray {
            val query = ByteBuffer.allocate(50).order(ByteOrder.BIG_ENDIAN)
            query.putShort(transactionId.toShort())
            query.putShort(0) // request flags
            query.putShort(1) // questions
            query.putShort(0) // answers
            query.putShort(0) // authority
            query.putShort(0) // additional
            query.put(32) // encoded NetBIOS name length
            query.put(encodeFirstLevelName("*"))
            query.put(0) // name terminator
            query.putShort(0x0021) // NBSTAT
            query.putShort(0x0001) // IN
            return query.array()
        }

        /** Encodes a 16-byte NetBIOS name into the RFC 1002 first-level alphabet. */
        fun encodeFirstLevelName(name: String): ByteArray {
            val padded = if (name == "*") {
                byteArrayOf('*'.code.toByte()) + ByteArray(15)
            } else {
                name.padEnd(16, ' ').take(16).toByteArray(StandardCharsets.US_ASCII)
            }
            return ByteArray(32) { index ->
                val value = if (index % 2 == 0) {
                    (padded[index / 2].toInt() ushr 4) and 0x0F
                } else {
                    padded[index / 2].toInt() and 0x0F
                }
                ('A'.code + value).toByte()
            }
        }

        /** Extracts the first unique workstation/server name from an NBSTAT response. */
        fun parseNbstatResponse(packet: ByteArray): String? {
            return parseNbstat(packet, expectedTransactionId = null, requireResponse = false)
        }

        /** Strict parser used as target-presence evidence. */
        fun parseNbstatPresenceResponse(packet: ByteArray, expectedTransactionId: Int): String? {
            return parseNbstat(packet, expectedTransactionId = expectedTransactionId, requireResponse = true)
        }

        private fun parseNbstat(
            packet: ByteArray,
            expectedTransactionId: Int?,
            requireResponse: Boolean,
        ): String? {
            if (packet.size < 12) return null
            if (expectedTransactionId != null && u16(packet, 0) != expectedTransactionId) return null
            val flags = u16(packet, 2)
            if (
                requireResponse &&
                    (flags and 0x8000 == 0 || flags and 0x7800 != 0 || flags and 0x0200 != 0 || flags and 0x000F != 0)
            ) {
                return null
            }
            val questionCount = u16(packet, 4)
            val answerCount = u16(packet, 6)
            if (questionCount < 0 || answerCount < 0) return null
            var cursor = 12
            var firstUniqueName: String? = null
            repeat(questionCount) {
                cursor = skipDnsName(packet, cursor) ?: return null
                if (cursor + 4 > packet.size) return null
                if (u16(packet, cursor) != 0x0021 || u16(packet, cursor + 2) != 0x0001) return null
                cursor += 4
            }
            repeat(answerCount) {
                cursor = skipDnsName(packet, cursor) ?: return null
                if (cursor + 10 > packet.size) return null
                val type = u16(packet, cursor)
                val recordClass = u16(packet, cursor + 2)
                val dataLength = u16(packet, cursor + 8)
                cursor += 10
                if (cursor + dataLength > packet.size) return null
                if (type == 0x0021 && recordClass == 0x0001 && dataLength >= 1) {
                    val names = packet[cursor].toInt() and 0xFF
                    if (names > (dataLength - 1) / 18) return null
                    var nameCursor = cursor + 1
                    repeat(names) {
                        val rawName = packet.copyOfRange(nameCursor, nameCursor + 15)
                            .toString(StandardCharsets.US_ASCII)
                            .trim()
                        val flags = u16(packet, nameCursor + 16)
                        if (firstUniqueName == null && rawName.isNotBlank() && flags and 0x8000 == 0) {
                            firstUniqueName = rawName
                        }
                        nameCursor += 18
                    }
                }
                cursor += dataLength
            }
            return firstUniqueName
        }

        private fun skipDnsName(packet: ByteArray, start: Int): Int? {
            var cursor = start
            var labels = 0
            while (cursor < packet.size) {
                val length = packet[cursor].toInt() and 0xFF
                if (length == 0) return cursor + 1
                if (length and 0xC0 == 0xC0) {
                    if (cursor + 1 >= packet.size) return null
                    val target = ((length and 0x3F) shl 8) or (packet[cursor + 1].toInt() and 0xFF)
                    if (target >= cursor) return null
                    return cursor + 2
                }
                if (length > 63 || cursor + length >= packet.size) return null
                cursor += length + 1
                if (++labels > 128) return null
            }
            return null
        }

        private fun u16(bytes: ByteArray, offset: Int): Int =
            if (offset + 1 >= bytes.size) -1
            else ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
