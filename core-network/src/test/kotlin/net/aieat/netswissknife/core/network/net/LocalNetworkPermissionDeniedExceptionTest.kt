package net.aieat.netswissknife.core.network.net

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalNetworkPermissionDeniedExceptionTest {
    @Test
    fun `finds permission denial through wrapped causes`() {
        val denial = LocalNetworkPermissionDeniedException(SecurityException("denied"))

        assertTrue(denial.containsLocalNetworkPermissionDenied())
        assertTrue(IllegalStateException("transport failed", denial).containsLocalNetworkPermissionDenied())
    }

    @Test
    fun `ordinary errors and cyclic causes are safe`() {
        assertFalse(IllegalStateException("offline").containsLocalNetworkPermissionDenied())

        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        first.initCause(second)
        second.initCause(first)

        assertFalse(first.containsLocalNetworkPermissionDenied())
    }
}
