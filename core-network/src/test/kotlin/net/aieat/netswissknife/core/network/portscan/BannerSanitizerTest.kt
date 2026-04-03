package net.aieat.netswissknife.core.network.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("BannerSanitizer")
class BannerSanitizerTest {

    @Nested
    @DisplayName("sanitizeBanner")
    inner class SanitizeBanner {

        @Test
        fun `ASCII-only banner passes through unchanged`() {
            val banner = "SSH-2.0-OpenSSH_9.0"
            assertEquals(banner, BannerSanitizer.sanitize(banner))
        }

        @Test
        fun `leading and trailing whitespace is trimmed`() {
            assertEquals("hello", BannerSanitizer.sanitize("  hello  "))
        }

        @Test
        fun `unicode letters are stripped`() {
            val banner = "caf\u00e9"         // café
            assertFalse(BannerSanitizer.sanitize(banner).contains('\u00e9'))
        }

        @Test
        fun `control characters are stripped`() {
            val banner = "hello\u0000world"   // embedded NUL
            assertFalse(BannerSanitizer.sanitize(banner).contains('\u0000'))
            assertEquals("helloworld", BannerSanitizer.sanitize(banner))
        }

        @Test
        fun `newline characters are stripped`() {
            val banner = "line1\nline2"
            assertFalse(BannerSanitizer.sanitize(banner).contains('\n'))
        }

        @Test
        fun `carriage return characters are stripped`() {
            val banner = "line1\r\nline2"
            assertFalse(BannerSanitizer.sanitize(banner).contains('\r'))
        }

        @Test
        fun `tab characters are stripped`() {
            val banner = "col1\tcol2"
            assertFalse(BannerSanitizer.sanitize(banner).contains('\t'))
        }

        @Test
        fun `banner is truncated to 200 characters`() {
            val longBanner = "A".repeat(300)
            assertTrue(BannerSanitizer.sanitize(longBanner).length <= 200)
        }

        @Test
        fun `common service banner characters are preserved`() {
            val banner = "SSH-2.0 (Ubuntu) / OpenSSH_9.0p1 @ host:22"
            val result = BannerSanitizer.sanitize(banner)
            assertTrue(result.contains("SSH-2.0"))
            assertTrue(result.contains("/"))
            assertTrue(result.contains(":"))
            assertTrue(result.contains("@"))
        }

        @Test
        fun `empty string returns empty string`() {
            assertEquals("", BannerSanitizer.sanitize(""))
        }

        @Test
        fun `whitespace-only string returns empty string after trim`() {
            assertEquals("", BannerSanitizer.sanitize("   "))
        }

        @Test
        fun `DEL character is stripped`() {
            val banner = "hello\u007Fworld"  // DEL (0x7F)
            assertFalse(BannerSanitizer.sanitize(banner).contains('\u007F'))
        }
    }
}
