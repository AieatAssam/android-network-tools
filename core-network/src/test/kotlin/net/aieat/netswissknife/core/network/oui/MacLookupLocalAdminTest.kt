package net.aieat.netswissknife.core.network.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MAC local administration rule")
class MacLookupLocalAdminTest {
    private val engine = MacLookupEngine(NoBlocks)

    private fun addressOf(input: String) = engine.lookup(input) as MacLookupEntry.Address

    private fun info(text: String): MacAddressInfo = addressOf(text).info

    @Test
    fun `the broadcast address is not applicable rather than locally administered`() {
        val info = info("FF:FF:FF:FF:FF:FF")

        assertEquals(LocalAdministration.NOT_APPLICABLE, info.localAdministration)
        assertEquals(false, info.locallyAdministered)
        assertEquals(VendorResult.NotApplicable, info.vendor)
    }

    @Test
    fun `an IPv6 multicast MAC is not applicable and has no locally administered vendor`() {
        val info = info("33:33:00:00:00:01")

        assertEquals(MacAddressKind.MULTICAST, info.kind)
        assertEquals(LocalAdministration.NOT_APPLICABLE, info.localAdministration)
        assertEquals(VendorResult.Unknown, info.vendor)
    }

    @Test
    fun `an IPv4 multicast MAC is looked up with no local administration claim`() {
        val info = info("01:00:5E:00:00:FB")

        assertEquals(LocalAdministration.NOT_APPLICABLE, info.localAdministration)
    }

    @Test
    fun `a unicast address with the U L bit set is locally administered`() {
        val info = info("02:00:00:00:00:01")

        assertEquals(LocalAdministration.LOCAL, info.localAdministration)
        assertEquals(VendorResult.LocallyAdministered, info.vendor)
    }

    @Test
    fun `a universally administered unicast address is universal`() {
        assertEquals(LocalAdministration.UNIVERSAL, info("00:1A:2B:3C:4D:5E").localAdministration)
    }

    private object NoBlocks : OuiRegistry {
        override fun match(hexDigits: String): OuiMatch? = null
    }
}
