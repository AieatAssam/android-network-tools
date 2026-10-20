package net.aieat.netswissknife.core.network.whois

import java.net.IDN
import java.text.Normalizer
import java.util.Locale
import java.util.HexFormat
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicSuffixTest {

    @Test
    fun `uses longest matching exact suffix and includes private section`() {
        assertEquals("example.co.uk", PublicSuffix.registrableDomain("www.shop.example.co.uk"))
        assertEquals("example.co.uk", PublicSuffix.registrableDomain("sub.example.co.uk"))
        assertEquals("foo.github.io", PublicSuffix.registrableDomain("foo.github.io"))
        assertEquals("foo.github.io", PublicSuffix.registrableDomain("a.foo.github.io"))
        assertEquals("github.io", PublicSuffix.domainForWhois("a.foo.github.io"))
        assertEquals("customer.blogspot.com", PublicSuffix.registrableDomain("customer.blogspot.com"))
        assertEquals("customer.blogspot.com", PublicSuffix.registrableDomain("a.customer.blogspot.com"))
        assertEquals(
            "tenant.host.compute.amazonaws.com",
            PublicSuffix.registrableDomain("tenant.host.compute.amazonaws.com"),
        )
        assertEquals("amazonaws.com", PublicSuffix.domainForWhois("tenant.host.compute.amazonaws.com"))
    }

    @Test
    fun `applies wildcard and exception rules`() {
        assertEquals("a.ck", PublicSuffix.registrableDomain("a.ck"))
        assertEquals("b.a.ck", PublicSuffix.registrableDomain("b.a.ck"))
        assertEquals("www.ck", PublicSuffix.registrableDomain("www.ck"))
        assertEquals("www.ck", PublicSuffix.registrableDomain("x.www.ck"))
    }

    @Test
    fun `canonicalizes case IDN and trailing root dot`() {
        assertEquals("example.com", PublicSuffix.registrableDomain("WWW.Example.COM."))
        assertEquals("example.com", PublicSuffix.registrableDomain("example.com."))
        val expectedChineseRule = IDN.toASCII("b.公司.cn")
        assertEquals(expectedChineseRule, PublicSuffix.registrableDomain("a.b.公司.cn"))
        assertEquals(expectedChineseRule, PublicSuffix.registrableDomain(IDN.toASCII("a.b.公司.cn")))
        assertEquals(
            IDN.toASCII("bücher.de"),
            PublicSuffix.registrableDomain("www.bücher.de"),
        )
    }

    @Test
    fun `uses implicit wildcard for unknown suffixes and handles suffix-only inputs`() {
        assertEquals("bar.unknown-tld", PublicSuffix.registrableDomain("foo.bar.unknown-tld"))
        assertEquals("co.uk", PublicSuffix.registrableDomain("co.uk"))
        assertEquals("localhost", PublicSuffix.registrableDomain("LOCALHOST."))
    }

    @Test
    fun `rejects malformed ASCII labels using the WHOIS STD3 validation policy`() {
        assertEquals("foo_bar.CO.UK", PublicSuffix.registrableDomain("foo_bar.CO.UK"))
        assertEquals("bad-.example.com", PublicSuffix.registrableDomain("bad-.example.com"))
    }

    @Test
    fun `matches Unicode PSL rules across composed and decomposed forms`() {
        val composed = "x.foo.aéroport.ci"
        val decomposed = "x.foo.ae\u0301roport.ci"
        val expected = IDN.toASCII("foo.aéroport.ci")

        assertEquals(expected, PublicSuffix.registrableDomain(composed))
        assertEquals(expected, PublicSuffix.registrableDomain(decomposed))
    }

    @Test
    fun `bundled list retains upstream provenance and both rule sections`() {
        val upstreamBytes = javaClass.getResourceAsStream("/psl/public_suffix_list.dat")!!
            .use { it.readBytes() }
        assertEquals(
            "257b298daca42f6d8ec964e238c2a55518e14f09d3117917ec8acee6f188503e",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(upstreamBytes)),
        )
        val lines = upstreamBytes.toString(Charsets.UTF_8).lines()
        val expectedRuntimeBytes = encodeRuntimeIndex(lines)
        val runtimeBytes = javaClass.getResourceAsStream("/psl/public_suffix_list.index")!!
            .use { it.readBytes() }
        assertArrayEquals(expectedRuntimeBytes, runtimeBytes)

        assertTrue(lines.first().contains("Mozilla Public"))
        assertTrue(lines.any { it.startsWith("// VERSION:") })
        assertTrue(lines.any { it == "// ===BEGIN ICANN DOMAINS===" })
        assertTrue(lines.any { it == "// ===BEGIN PRIVATE DOMAINS===" })
        assertTrue(lines.count { it.isNotBlank() && !it.trimStart().startsWith("//") } > 10_000)
        val rules = lines.filter { it.isNotBlank() && !it.trimStart().startsWith("//") }
        assertTrue(rules.none { it.contains("//") })
        assertTrue(rules.all { it == it.lowercase(Locale.ROOT) })
        val unicodeRules = lines.mapNotNull { line ->
            var rule = line.substringBefore("//").trim().removePrefix("!")
            if (rule.startsWith("*.")) rule = rule.removePrefix("*.")
            rule.takeIf { it.isNotEmpty() && it.any { char -> char.code > 127 } }
        }
        assertTrue(unicodeRules.all { it == it.lowercase(Locale.ROOT) })
        assertTrue(unicodeRules.all { Normalizer.isNormalized(it, Normalizer.Form.NFC) })
    }

    private fun encodeRuntimeIndex(sourceLines: List<String>): ByteArray {
        val output = StringBuilder(RUNTIME_INDEX_HEADER)
        var section = 'I'
        for (line in sourceLines) {
            when (line) {
                "// ===BEGIN PRIVATE DOMAINS===" -> {
                    section = 'P'
                    continue
                }
                "// ===END PRIVATE DOMAINS===" -> {
                    section = 'I'
                    continue
                }
            }
            if (line.isBlank() || line.trimStart().startsWith("//")) continue
            val (kind, rule) = when {
                line.startsWith('!') -> '!' to line.substring(1)
                line.startsWith("*.") -> '*' to line.substring(2)
                else -> '=' to line
            }
            output.append(section).append(kind).append(rule).append('\n')
        }
        return output.toString().toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val RUNTIME_INDEX_HEADER = "# PSL-RUNTIME-INDEX/1\n" +
            "# Mozilla Public Suffix List, MPL-2.0\n" +
            "# Version: 2026-09-24_13-26-36_UTC\n" +
            "# Upstream commit: a179a48c465e818cfd8d626691cb317985da87fb\n" +
            "# Source SHA-256: 257b298daca42f6d8ec964e238c2a55518e14f09d3117917ec8acee6f188503e\n"
    }
}
