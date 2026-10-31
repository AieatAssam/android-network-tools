package net.aieat.netswissknife.core.domain

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HTTP Probe URL whitespace handling")
class HttpProbeUrlWhitespaceTest {
    @Test
    fun `trailing space after a valid HTTPS URL is not a validation error`() {
        assertNull(validateHttpProbeUrlInfo("https://google.com "))
        assertNull(validateHttpProbeUrl("https://google.com "))
    }

    @Test
    fun `leading and trailing whitespace around a valid HTTPS URL is ignored`() {
        assertNull(validateHttpProbeUrlInfo("  https://example.com/path  "))
    }
}
