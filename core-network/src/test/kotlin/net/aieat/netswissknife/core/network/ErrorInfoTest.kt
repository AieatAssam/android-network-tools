package net.aieat.netswissknife.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorInfoTest {
    @Test
    fun `factory retains typed arguments and interpolates developer message`() {
        val error = NetworkResult.error(ErrorCode.TIMEOUT_OUT_OF_RANGE, 100, 30_000)

        assertEquals(ErrorCode.TIMEOUT_OUT_OF_RANGE, error.info?.code)
        assertEquals(listOf(100, 30_000), error.info?.args)
        assertTrue(error.message.contains("100"))
        assertTrue(error.message.contains("30000"))
    }

    @Test
    fun `legacy constructor keeps info null`() {
        val error = NetworkResult.Error("x")

        assertEquals("x", error.message)
        assertNull(error.info)
    }

    @Test
    fun `typed factory preserves existing developer copy`() {
        val error = NetworkResult.error(
            ErrorCode.SUBNET_INVALID,
            developerMessage = "Invalid input: host bits out of range",
        )

        assertEquals(ErrorCode.SUBNET_INVALID, error.info?.code)
        assertEquals("Invalid input: host bits out of range", error.message)
        assertEquals(error.message, error.info?.developerMessage)
    }

    @Test
    fun `factory retains cause and legacy metadata`() {
        val cause = IllegalStateException("connection closed")
        val error = NetworkResult.error(
            code = ErrorCode.TLS_PIN_INVALID,
            developerMessage = "Invalid SHA-256 pin",
            cause = cause,
            legacyCode = "TLS_PIN_INVALID_LEGACY",
            descriptionKey = "tls_pin_invalid",
        )

        assertEquals(cause, error.cause)
        assertEquals("TLS_PIN_INVALID_LEGACY", error.code)
        assertEquals("tls_pin_invalid", error.descriptionKey)
        assertEquals(ErrorCode.TLS_PIN_INVALID, error.info?.code)
    }
}
