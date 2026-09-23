package net.aieat.netswissknife.app.ui.screens.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HttpRecentOriginTest {

    @Test
    fun `drops credentials path query fragment and default port`() {
        assertEquals(
            "https://example.com",
            safeHttpRecentOrigin(" https://user:secret@EXAMPLE.COM:443/private?token=secret#section ")
        )
    }

    @Test
    fun `keeps a non-default port`() {
        assertEquals(
            "http://example.com:8080",
            safeHttpRecentOrigin("http://example.com:8080/private?token=secret")
        )
    }

    @Test
    fun `normalizes internationalized hostnames`() {
        assertEquals(
            "https://xn--bcher-kva.example",
            safeHttpRecentOrigin("https://Bücher.example/private")
        )
    }

    @Test
    fun `normalizes ipv6 address and preserves custom port`() {
        assertEquals(
            "https://[2001:db8::1]:8443",
            safeHttpRecentOrigin("https://[2001:DB8::1]:8443/path")
        )
    }

    @Test
    fun `rejects invalid and non-http urls`() {
        listOf(
            "",
            "https:///missing-host?token=secret",
            "https://example.com:0/secret",
            "ftp://example.com/private?token=secret",
            "not a URL"
        ).forEach { raw -> assertNull(safeHttpRecentOrigin(raw), raw) }
    }
}
