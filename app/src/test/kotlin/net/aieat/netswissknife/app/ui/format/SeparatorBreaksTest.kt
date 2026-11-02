package net.aieat.netswissknife.app.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeparatorBreaksTest {
    private val zwsp = "​"

    @Test
    fun `cipher suite breaks after each underscore`() {
        assertEquals(
            "TLS_${zwsp}ECDHE_${zwsp}ECDSA_${zwsp}WITH_${zwsp}AES_${zwsp}128_${zwsp}GCM_${zwsp}SHA256",
            withSeparatorBreaks("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"),
        )
    }

    @Test
    fun `header name breaks after each hyphen`() {
        assertEquals("Strict-${zwsp}Transport-${zwsp}Security", withSeparatorBreaks("Strict-Transport-Security"))
    }

    @Test
    fun `text without separators is unchanged`() {
        assertEquals("Cloudflare TLS Issuing ECC CA 3", withSeparatorBreaks("Cloudflare TLS Issuing ECC CA 3"))
    }
}
