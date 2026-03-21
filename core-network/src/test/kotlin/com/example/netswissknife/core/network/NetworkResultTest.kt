package com.example.netswissknife.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD example tests for the NetworkResult sealed class.
 */
class NetworkResultTest {

    @Test
    fun `Success holds data`() {
        val result: NetworkResult<Int> = NetworkResult.Success(42)
        assertTrue(result is NetworkResult.Success)
        assertEquals(42, (result as NetworkResult.Success).data)
    }

    @Test
    fun `Error holds message and optional cause`() {
        val cause = RuntimeException("test")
        val result: NetworkResult<Nothing> = NetworkResult.Error("something went wrong", cause)
        assertTrue(result is NetworkResult.Error)
        assertEquals("something went wrong", (result as NetworkResult.Error).message)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `Error cause is optional`() {
        val result = NetworkResult.Error("no cause")
        assertNull(result.cause)
    }
}
