package net.aieat.netswissknife.app.platform

import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkStatusProviderTest {
    @Test
    fun `classifies a direct local-network permission denial`() {
        assertEquals(
            NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED,
            LocalNetworkPermissionDeniedException(SecurityException("denied")).toNetworkErrorKind(),
        )
    }

    @Test
    fun `classifies a typed denial nested in a repository wrapper`() {
        val typed = LocalNetworkPermissionDeniedException(SecurityException("denied"))
        assertEquals(NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED, IllegalStateException("wrapper", typed).toNetworkErrorKind())
    }

    @Test
    fun `does not classify unrelated errors as a permission denial`() {
        assertEquals(NetworkErrorKind.GENERAL, IllegalStateException("network failed").toNetworkErrorKind())
    }
}
