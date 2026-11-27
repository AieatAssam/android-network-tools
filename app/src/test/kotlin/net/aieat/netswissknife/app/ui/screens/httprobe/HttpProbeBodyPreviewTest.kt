package net.aieat.netswissknife.app.ui.screens.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpProbeBodyPreviewTest {
    @Test
    fun `a body within the limit is shown whole and not marked truncated`() {
        val preview = previewBody("{\"a\":1}", limit = 100)

        assertEquals("{\"a\":1}", preview.text)
        assertFalse(preview.truncated)
    }

    @Test
    fun `a body over the limit is cut to the limit and marked truncated`() {
        val preview = previewBody("x".repeat(40), limit = 25)

        assertEquals(25, preview.text?.length)
        assertTrue(preview.truncated)
    }

    @Test
    fun `the default limit is 32000 characters`() {
        assertEquals(32_000, BODY_PREVIEW_LIMIT_CHARS)
        assertEquals(32_000, previewBody("y".repeat(32_001)).text?.length)
        assertFalse(previewBody("y".repeat(32_000)).truncated)
    }

    @Test
    fun `a missing body previews as null`() {
        val preview = previewBody(null)

        assertNull(preview.text)
        assertFalse(preview.truncated)
    }

    @Test
    fun `JSON is detected from the media type and ignores parameters and case`() {
        assertTrue(isJsonResponse(mapOf("Content-Type" to listOf("application/json; charset=utf-8"))))
        assertTrue(isJsonResponse(mapOf("content-type" to listOf("Application/JSON"))))
        assertTrue(isJsonResponse(mapOf("Content-Type" to listOf("application/problem+json"))))
        assertTrue(isJsonResponse(mapOf("Content-Type" to listOf("text/json"))))
    }

    @Test
    fun `non JSON and missing content types are not JSON`() {
        assertFalse(isJsonResponse(mapOf("Content-Type" to listOf("text/plain"))))
        assertFalse(isJsonResponse(mapOf("Content-Type" to listOf("text/html; note=json"))))
        assertFalse(isJsonResponse(emptyMap()))
    }

    @Test
    fun `a Content-Encoding other than identity is detected`() {
        assertTrue(hasContentEncoding(mapOf("Content-Encoding" to listOf("gzip"))))
        assertTrue(hasContentEncoding(mapOf("content-encoding" to listOf("br"))))
        assertFalse(hasContentEncoding(mapOf("Content-Encoding" to listOf("identity"))))
        assertFalse(hasContentEncoding(emptyMap()))
    }

    @Test
    fun `a complete read is exact whatever Content-Length says`() {
        assertEquals(
            ResponseSizeKind.EXACT,
            responseSizeKind(truncated = false, declaredBytes = 10, contentEncoded = true),
        )
    }

    @Test
    fun `a truncated uncompressed read uses the declared length`() {
        assertEquals(
            ResponseSizeKind.DECLARED,
            responseSizeKind(truncated = true, declaredBytes = 900, contentEncoded = false),
        )
    }

    @Test
    fun `a truncated compressed read says at least because Content-Length counts wire bytes`() {
        assertEquals(
            ResponseSizeKind.AT_LEAST,
            responseSizeKind(truncated = true, declaredBytes = 900, contentEncoded = true),
        )
    }

    @Test
    fun `a truncated read with no declared length says at least`() {
        assertEquals(
            ResponseSizeKind.AT_LEAST,
            responseSizeKind(truncated = true, declaredBytes = null, contentEncoded = false),
        )
    }
}
