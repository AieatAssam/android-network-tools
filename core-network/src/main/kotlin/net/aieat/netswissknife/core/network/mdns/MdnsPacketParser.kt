package net.aieat.netswissknife.core.network.mdns

import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Type

object MdnsPacketParser {

    /**
     * Extracts the human-readable display name from an mDNS instance name.
     * "My Printer._http._tcp.local." → "My Printer"
     */
    fun extractDisplayName(instanceName: String): String {
        val stripped = instanceName.trimEnd('.')
        // Find the first component that starts with underscore — that's the service type start
        val parts = stripped.split(".")
        val typeStart = parts.indexOfFirst { it.startsWith("_") }
        return if (typeStart > 0) parts.take(typeStart).joinToString(".") else stripped
    }

    /**
     * Extracts the service type from an mDNS instance name.
     * "My Printer._http._tcp.local." → "_http._tcp"
     */
    fun extractServiceType(instanceName: String): String {
        val stripped = instanceName.trimEnd('.')
        val parts = stripped.split(".")
        val typeStart = parts.indexOfFirst { it.startsWith("_") }
        return if (typeStart >= 0) {
            val localIdx = parts.lastIndexOf("local")
            val end = if (localIdx > typeStart) localIdx else parts.size
            parts.subList(typeStart, end).joinToString(".")
        } else ""
    }

    /**
     * Parses TXT record strings (each entry is "key=value" or "key") into a map.
     */
    fun parseTxtPairs(entries: List<String>): Map<String, String> {
        return entries.associate { entry ->
            val eqIdx = entry.indexOf('=')
            if (eqIdx >= 0) {
                entry.substring(0, eqIdx) to entry.substring(eqIdx + 1)
            } else {
                entry to ""
            }
        }
    }

    /**
     * Builds a raw mDNS PTR query message for [name].
     */
    fun buildMdnsQuery(name: String, type: Int = Type.PTR): ByteArray {
        val queryName = Name.fromString(if (name.endsWith(".")) name else "$name.")
        val queryRecord = Record.newRecord(queryName, type, DClass.IN)
        return Message.newQuery(queryRecord).toWire()
    }

    /**
     * Parses a raw DNS wire-format packet. Returns null if parsing fails.
     */
    fun parsePacket(bytes: ByteArray): Message? = try {
        Message(bytes)
    } catch (_: Exception) {
        null
    }

    /**
     * Strips the "local." domain suffix from a hostname.
     * "myprinter.local." → "myprinter.local"
     */
    fun normalizeHostname(raw: String): String = raw.trimEnd('.')
}
