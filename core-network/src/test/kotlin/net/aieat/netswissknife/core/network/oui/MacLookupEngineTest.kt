package net.aieat.netswissknife.core.network.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MacLookupEngine")
class MacLookupEngineTest {
    /** Stands in for the bundled registry: prefix keys mapped to their block assignment. */
    private class FakeRegistry(
        private val blocks: Map<String, OuiMatch>,
    ) : OuiRegistry {
        val queries = mutableListOf<String>()

        override fun match(hexDigits: String): OuiMatch? {
            queries += hexDigits
            return blocks.entries
                .filter { hexDigits.startsWith(it.key) }
                .maxByOrNull { it.key.length }
                ?.value
        }
    }

    private val registry =
        FakeRegistry(
            mapOf(
                "B827EB" to OuiMatch("Raspberry Pi Foundation", OuiBlockSize.MA_L),
                "0055DA0" to OuiMatch("Shinko Technos co.,ltd.", OuiBlockSize.MA_M),
                "001BC5000" to OuiMatch("Converging Systems Inc.", OuiBlockSize.MA_S),
                "FFFFFF" to OuiMatch("Broadcast block", OuiBlockSize.MA_L),
            ),
        )
    private val engine = MacLookupEngine(registry)

    private fun address(input: String): MacLookupEntry.Address {
        val entry = engine.lookup(input)
        return entry as? MacLookupEntry.Address ?: error("expected an address for $input, got $entry")
    }

    @Test
    fun `a universally administered unicast address resolves to its vendor and block`() {
        val entry = address("B8:27:EB:11:22:33")

        assertEquals("B827EB112233", entry.info.hex)
        assertEquals(MacAddressKind.UNICAST, entry.info.kind)
        assertFalse(entry.info.locallyAdministered)
        assertEquals(
            VendorResult.Known("Raspberry Pi Foundation", OuiBlockSize.MA_L),
            entry.info.vendor,
        )
    }

    @Test
    fun `the longest registered block decides the vendor and its size`() {
        val medium = address("00:55:DA:0A:BC:DE").info.vendor
        assertEquals(VendorResult.Known("Shinko Technos co.,ltd.", OuiBlockSize.MA_M), medium)

        val small = address("00-1B-C5-00-0A-BC").info.vendor
        assertEquals(VendorResult.Known("Converging Systems Inc.", OuiBlockSize.MA_S), small)
    }

    @Test
    fun `an address with no registered block is an unknown vendor`() {
        assertEquals(VendorResult.Unknown, address("00:00:5E:00:53:01").info.vendor)
    }

    @Test
    fun `a locally administered address is flagged and never looked up`() {
        val blocked = FakeRegistry(mapOf("020000" to OuiMatch("Would be wrong", OuiBlockSize.MA_L)))
        val entry = MacLookupEngine(blocked).lookup("02:00:00:00:00:01") as MacLookupEntry.Address

        assertTrue(entry.info.locallyAdministered)
        assertEquals(VendorResult.LocallyAdministered, entry.info.vendor)
        assertTrue(blocked.queries.isEmpty())
    }

    @Test
    fun `a multicast address is reported as multicast and looked up like any other block`() {
        val entry = address("01:00:5E:00:00:FB")

        assertEquals(MacAddressKind.MULTICAST, entry.info.kind)
        assertFalse(entry.info.locallyAdministered)
        assertEquals(VendorResult.Unknown, entry.info.vendor)
    }

    @Test
    fun `the broadcast address is reported as broadcast with no vendor`() {
        val entry = address("FF:FF:FF:FF:FF:FF")

        assertEquals(MacAddressKind.BROADCAST, entry.info.kind)
        assertEquals(VendorResult.NotApplicable, entry.info.vendor)
        assertTrue(entry.info.locallyAdministered, "the broadcast address has the U/L bit set")
    }

    @Test
    fun `an IPv6 multicast MAC is multicast and locally administered, so it gets no vendor`() {
        val entry = address("33:33:00:00:00:01")

        assertEquals(MacAddressKind.MULTICAST, entry.info.kind)
        assertTrue(entry.info.locallyAdministered)
        assertEquals(VendorResult.LocallyAdministered, entry.info.vendor)
    }

    @Test
    fun `an OUI prefix on its own resolves through its 24-bit block`() {
        val entry = address("b8-27-eb")

        assertTrue(entry.info.isPrefixOnly)
        assertEquals("B827EB", entry.info.hex)
        assertEquals(VendorResult.Known("Raspberry Pi Foundation", OuiBlockSize.MA_L), entry.info.vendor)
    }

    @Test
    fun `a prefix that matches only a longer block is reported unknown rather than guessed`() {
        // 0055DA is not registered on its own; only the 28-bit block 0055DA0 is.
        assertEquals(VendorResult.Unknown, address("00:55:DA").info.vendor)
    }

    @Test
    fun `malformed input is rejected with the reason`() {
        val entry = engine.lookup("B8:27:EB:ZZ:22:33")

        assertEquals(MacLookupEntry.Rejected("B8:27:EB:ZZ:22:33", MacInputProblem.CHARACTERS), entry)
    }

    @Test
    fun `lookupAll keeps order, rejects bad entries inline and does not drop them`() {
        val entries = engine.lookupAll("B8:27:EB:11:22:33\nnope, 02:00:00:00:00:02")

        assertEquals(3, entries.size)
        assertTrue(entries[0] is MacLookupEntry.Address)
        assertEquals(MacLookupEntry.Rejected("nope", MacInputProblem.CHARACTERS), entries[1])
        assertTrue(entries[2] is MacLookupEntry.Address)
    }

    @Test
    fun `formats the address in colon, dash, dotted and bare notation in either case`() {
        val info = address("b8:27:eb:11:22:33").info

        assertEquals("B8:27:EB:11:22:33", info.format(MacNotation.COLON, uppercase = true))
        assertEquals("b8-27-eb-11-22-33", info.format(MacNotation.HYPHEN, uppercase = false))
        assertEquals("B827.EB11.2233", info.format(MacNotation.DOTTED, uppercase = true))
        assertEquals("b827eb112233", info.format(MacNotation.BARE, uppercase = false))
    }

    @Test
    fun `an OUI prefix formats in the same notations with its six digits`() {
        val info = address("B8:27:EB").info

        assertEquals("B8-27-EB", info.format(MacNotation.HYPHEN, uppercase = true))
        assertEquals("B827.EB", info.format(MacNotation.DOTTED, uppercase = true))
        assertEquals("b827eb", info.format(MacNotation.BARE, uppercase = false))
    }
}
