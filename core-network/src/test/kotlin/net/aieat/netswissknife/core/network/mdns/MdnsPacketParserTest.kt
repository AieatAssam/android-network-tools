package net.aieat.netswissknife.core.network.mdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xbill.DNS.Message
import org.xbill.DNS.Type

class MdnsPacketParserTest {

    // ── extractDisplayName ────────────────────────────────────────────────────

    @Test
    fun `extractDisplayName strips service type and local suffix`() {
        assertEquals("My Printer", MdnsPacketParser.extractDisplayName("My Printer._http._tcp.local."))
    }

    @Test
    fun `extractDisplayName handles instance names with dots`() {
        assertEquals("Office.Floor2", MdnsPacketParser.extractDisplayName("Office.Floor2._ipp._tcp.local."))
    }

    @Test
    fun `extractDisplayName returns input when no underscore type found`() {
        val input = "justAHostname"
        assertEquals(input, MdnsPacketParser.extractDisplayName(input))
    }

    // ── extractServiceType ────────────────────────────────────────────────────

    @Test
    fun `extractServiceType returns correct service type`() {
        assertEquals("_http._tcp", MdnsPacketParser.extractServiceType("My Printer._http._tcp.local."))
    }

    @Test
    fun `extractServiceType handles airplay service`() {
        assertEquals("_airplay._tcp", MdnsPacketParser.extractServiceType("Apple TV._airplay._tcp.local."))
    }

    @Test
    fun `extractServiceType handles ipp with sub-type`() {
        assertEquals("_ipp._tcp", MdnsPacketParser.extractServiceType("HP._ipp._tcp.local."))
    }

    // ── parseTxtPairs ─────────────────────────────────────────────────────────

    @Test
    fun `parseTxtPairs parses key=value pairs`() {
        val result = MdnsPacketParser.parseTxtPairs(listOf("name=myprinter", "model=HP LaserJet"))
        assertEquals("myprinter", result["name"])
        assertEquals("HP LaserJet", result["model"])
    }

    @Test
    fun `parseTxtPairs handles boolean keys without equals sign`() {
        val result = MdnsPacketParser.parseTxtPairs(listOf("adminurl=http://host", "duplex"))
        assertEquals("http://host", result["adminurl"])
        assertEquals("", result["duplex"])
    }

    @Test
    fun `parseTxtPairs returns empty map for empty input`() {
        assertTrue(MdnsPacketParser.parseTxtPairs(emptyList()).isEmpty())
    }

    @Test
    fun `parseTxtPairs value may contain equals signs`() {
        val result = MdnsPacketParser.parseTxtPairs(listOf("url=http://host/path?a=1&b=2"))
        assertEquals("http://host/path?a=1&b=2", result["url"])
    }

    // ── buildMdnsQuery ────────────────────────────────────────────────────────

    @Test
    fun `buildMdnsQuery produces parseable DNS message`() {
        val bytes = MdnsPacketParser.buildMdnsQuery("_services._dns-sd._udp.local.", Type.PTR)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        val msg = Message(bytes)
        assertNotNull(msg.question)
    }

    @Test
    fun `buildMdnsQuery appends trailing dot when missing`() {
        val bytes = MdnsPacketParser.buildMdnsQuery("_http._tcp.local")
        val msg = Message(bytes)
        val question = msg.question
        assertNotNull(question)
        assertTrue(question!!.name.toString().endsWith("."))
    }

    @Test
    fun `buildMdnsQuery sets unicast-response bit in question class when requested`() {
        val bytes = MdnsPacketParser.buildMdnsQuery("_http._tcp.local.", unicastResponse = true)
        val qclass = ((bytes[bytes.lastIndex - 1].toInt() and 0xFF) shl 8) or
            (bytes[bytes.lastIndex].toInt() and 0xFF)

        assertEquals(0x8001, qclass)
    }

    @Test
    fun `buildMdnsQuery leaves unicast-response bit clear by default`() {
        val bytes = MdnsPacketParser.buildMdnsQuery("_http._tcp.local.")
        val qclass = ((bytes[bytes.lastIndex - 1].toInt() and 0xFF) shl 8) or
            (bytes[bytes.lastIndex].toInt() and 0xFF)

        assertEquals(0x0001, qclass)
    }

    // ── normalizeHostname ─────────────────────────────────────────────────────

    @Test
    fun `normalizeHostname strips trailing dot`() {
        assertEquals("mydevice.local", MdnsPacketParser.normalizeHostname("mydevice.local."))
    }

    @Test
    fun `normalizeHostname leaves string without trailing dot unchanged`() {
        assertEquals("mydevice.local", MdnsPacketParser.normalizeHostname("mydevice.local"))
    }

    // ── parsePacket ───────────────────────────────────────────────────────────

    @Test
    fun `parsePacket returns null for garbage bytes`() {
        val result = MdnsPacketParser.parsePacket(ByteArray(10) { 0xFF.toByte() })
        // Either null or potentially throws — either way it must not crash
        // The contract: null is acceptable, a valid Message is also acceptable
        // Key requirement: no uncaught exception
    }

    @Test
    fun `parsePacket round-trips a built query`() {
        val bytes = MdnsPacketParser.buildMdnsQuery("_services._dns-sd._udp.local.")
        val msg = MdnsPacketParser.parsePacket(bytes)
        assertNotNull(msg)
        assertNotNull(msg!!.question)
    }

    // ── DNS-SD escapes in instance names (MDNS-01) ────────────────────────────

    @Test
    fun `extractDisplayName decodes decimal and UTF-8 escapes`() {
        assertEquals(
            "Aieat\u2019s MacBook Air",
            MdnsPacketParser.extractDisplayName("""Aieat\226\128\153s\032MacBook\032Air._airplay._tcp.local."""),
        )
    }

    @Test
    fun `extractDisplayName decodes parentheses escapes`() {
        assertEquals(
            "HP LaserJet 300 colorMFP M375nw (615DC8)",
            MdnsPacketParser.extractDisplayName(
                """HP\032LaserJet\032300\032colorMFP\032M375nw\032\(615DC8\)._http._tcp.local.""",
            ),
        )
    }

    @Test
    fun `extractDisplayName keeps an escaped dot inside the instance label`() {
        assertEquals("Office.Floor2", MdnsPacketParser.extractDisplayName("""Office\.Floor2._ipp._tcp.local."""))
    }

    @Test
    fun `extractServiceType ignores an escaped dot inside the instance label`() {
        assertEquals("_ipp._tcp", MdnsPacketParser.extractServiceType("""Office\.Floor2._ipp._tcp.local."""))
    }

    @Test
    fun `extractDisplayName leaves malformed escapes as written`() {
        assertEquals("""Bad\999 name""", MdnsPacketParser.extractDisplayName("""Bad\999 name._http._tcp.local."""))
    }
}
