package net.aieat.netswissknife.core.network.netinfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddressPreferenceTest {
    @Test
    fun `deprecated flag is read from the interface address flags`() {
        val permanent = 0x80
        val deprecated = 0x20

        assertTrue(InterfaceAddressFlags.isDeprecated(permanent or deprecated))
        assertFalse(InterfaceAddressFlags.isDeprecated(permanent))
    }

    @Test
    fun `addresses in use come before deprecated ones and keep their order`() {
        val oldTemporary = address("2001:db8::1", deprecated = true)
        val current = address("2001:db8::2")
        val stable = address("2001:db8::3")

        val ordered = listOf(oldTemporary, current, stable).preferredFirst()

        assertEquals(listOf(current, stable, oldTemporary), ordered)
    }

    private fun address(
        text: String,
        deprecated: Boolean = false,
    ) = LocalAddress(text, IpFamily.IPV6, 64, IpAddressScope.GLOBAL, deprecated)
}
