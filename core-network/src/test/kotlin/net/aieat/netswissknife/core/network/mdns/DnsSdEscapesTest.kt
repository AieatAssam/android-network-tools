package net.aieat.netswissknife.core.network.mdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DnsSdEscapesTest {
    // ── decode: pass-through ──────────────────────────────────────────────────

    @Test
    fun `plain text without escapes is returned unchanged`() {
        assertEquals("My Printer", DnsSdEscapes.decode("My Printer"))
    }

    @Test
    fun `empty input is returned unchanged`() {
        assertEquals("", DnsSdEscapes.decode(""))
    }

    // ── decode: decimal byte escapes ──────────────────────────────────────────

    @Test
    fun `decimal space escape becomes a space`() {
        assertEquals("HP LaserJet", DnsSdEscapes.decode("""HP\032LaserJet"""))
    }

    @Test
    fun `multi-byte UTF-8 sequence recombines into one character`() {
        // \226\128\153 is bytes E2 80 99, which is U+2019 RIGHT SINGLE QUOTATION MARK.
        assertEquals("Aieat’s MacBook Air", DnsSdEscapes.decode("""Aieat\226\128\153s\032MacBook\032Air"""))
    }

    @Test
    fun `four-byte UTF-8 sequence decodes to a supplementary character`() {
        // F0 9F 8E B5 is U+1F3B5 MUSICAL NOTE.
        assertEquals("Speaker 🎵", DnsSdEscapes.decode("""Speaker\032\240\159\142\181"""))
    }

    @Test
    fun `decimal escape at the very end of the input decodes`() {
        assertEquals("Name ", DnsSdEscapes.decode("""Name\032"""))
    }

    @Test
    fun `parentheses escaped with backslash are literal`() {
        assertEquals("HP M375nw (615DC8)", DnsSdEscapes.decode("""HP\032M375nw\032\(615DC8\)"""))
    }

    @Test
    fun `escaped dot is a literal dot`() {
        assertEquals("Office.Floor2", DnsSdEscapes.decode("""Office\.Floor2"""))
    }

    @Test
    fun `escaped backslash is a literal backslash`() {
        assertEquals("""back\slash""", DnsSdEscapes.decode("""back\\slash"""))
    }

    @Test
    fun `decimal escape for a control byte decodes to that character`() {
        assertEquals("a\u0007b", DnsSdEscapes.decode("""a\007b"""))
    }

    // ── decode: malformed escapes are left exactly as written ─────────────────

    @Test
    fun `decimal value above 255 is left as written`() {
        assertEquals("""bad\999x""", DnsSdEscapes.decode("""bad\999x"""))
    }

    @Test
    fun `two-digit decimal escape is left as written`() {
        assertEquals("""bad\25x""", DnsSdEscapes.decode("""bad\25x"""))
    }

    @Test
    fun `trailing backslash is left as written`() {
        assertEquals("""trailing\""", DnsSdEscapes.decode("""trailing\"""))
    }

    @Test
    fun `truncated UTF-8 sequence is left as written`() {
        assertEquals("""lone\226\128""", DnsSdEscapes.decode("""lone\226\128"""))
    }

    @Test
    fun `invalid UTF-8 byte is left as written`() {
        assertEquals("""bad\255""", DnsSdEscapes.decode("""bad\255"""))
    }

    @Test
    fun `malformed escape does not stop later valid escapes from decoding`() {
        assertEquals("""bad\25 x""", DnsSdEscapes.decode("""bad\25\032x"""))
    }

    // ── splitLabels ───────────────────────────────────────────────────────────

    @Test
    fun `splitLabels splits on unescaped dots and keeps escapes raw`() {
        assertEquals(
            listOf("""Office\.Floor2""", "_ipp", "_tcp", "local"),
            DnsSdEscapes.splitLabels("""Office\.Floor2._ipp._tcp.local."""),
        )
    }

    @Test
    fun `splitLabels drops the empty label after a trailing dot`() {
        assertEquals(listOf("a", "b"), DnsSdEscapes.splitLabels("a.b."))
    }

    @Test
    fun `splitLabels keeps an escaped trailing dot as part of the label`() {
        assertEquals(listOf("""a\."""), DnsSdEscapes.splitLabels("""a\."""))
    }
}
