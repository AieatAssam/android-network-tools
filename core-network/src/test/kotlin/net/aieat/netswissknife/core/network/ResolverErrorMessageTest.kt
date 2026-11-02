package net.aieat.netswissknife.core.network

import java.io.IOException
import java.net.UnknownHostException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResolverErrorMessageTest {
    @Test
    fun `unknown host gives the plain wording shared with Ping`() {
        assertEquals(
            "Could not find an address for no-such-host.invalid. Check the name, then try again.",
            resolverErrorMessage(UnknownHostException("no-such-host.invalid"), "no-such-host.invalid"),
        )
    }

    @Test
    fun `other failures are not treated as resolver errors`() {
        assertNull(resolverErrorMessage(IOException("socket closed"), "example.com"))
    }
}
