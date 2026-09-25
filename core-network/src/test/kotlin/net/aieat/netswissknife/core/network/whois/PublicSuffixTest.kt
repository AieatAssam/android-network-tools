package net.aieat.netswissknife.core.network.whois

import java.net.IDN
import java.text.Normalizer
import java.util.Locale
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
        val lines = javaClass.getResourceAsStream("/psl/public_suffix_list.dat")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readLines() }

        assertTrue(lines.first().contains("Mozilla Public"))
        assertTrue(lines.any { it.startsWith("// VERSION:") })
        assertTrue(lines.any { it == "// ===BEGIN ICANN DOMAINS===" })
        assertTrue(lines.any { it == "// ===BEGIN PRIVATE DOMAINS===" })
        assertTrue(lines.count { it.isNotBlank() && !it.trimStart().startsWith("//") } > 10_000)
        val unicodeRules = lines.mapNotNull { line ->
            var rule = line.substringBefore("//").trim().removePrefix("!")
            if (rule.startsWith("*.")) rule = rule.removePrefix("*.")
            rule.takeIf { it.isNotEmpty() && it.any { char -> char.code > 127 } }
        }
        assertTrue(unicodeRules.all { it == it.lowercase(Locale.ROOT) })
        assertTrue(unicodeRules.all { Normalizer.isNormalized(it, Normalizer.Form.NFC) })
    }
}
