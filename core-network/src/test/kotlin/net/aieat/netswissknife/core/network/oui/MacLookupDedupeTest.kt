package net.aieat.netswissknife.core.network.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MacLookupDedupe")
class MacLookupDedupeTest {
    private val engine = MacLookupEngine(NoBlocks)

    @Test
    fun `four notations of one address collapse to one entry that counts the occurrences`() {
        val entries = engine.lookupAll("00:1A:2B:3C:4D:5E, 00-1a-2b-3c-4d-5e, 001A.2B3C.4D5E, 001a2b3c4d5e")

        val collapsed = MacLookupDedupe.collapse(entries)

        assertEquals(1, collapsed.size)
        val address = collapsed.single() as MacLookupEntry.Address
        assertEquals(4, address.occurrences)
        assertEquals("00:1A:2B:3C:4D:5E", address.input)
    }

    @Test
    fun `distinct addresses keep their order and count of one`() {
        val entries = engine.lookupAll("00:1A:2B:3C:4D:5E 00:1A:2B:3C:4D:5F 00:1A:2B:3C:4D:5E")

        val collapsed = MacLookupDedupe.collapse(entries)

        assertEquals(2, collapsed.size)
        assertEquals(listOf(2, 1), collapsed.map { (it as MacLookupEntry.Address).occurrences })
    }

    @Test
    fun `rejected entries are kept in place and are never merged`() {
        val entries = engine.lookupAll("zz:zz 00:1A:2B:3C:4D:5E zz:zz 00-1a-2b-3c-4d-5e")

        val collapsed = MacLookupDedupe.collapse(entries)

        assertEquals(3, collapsed.size)
        assertTrue(collapsed[0] is MacLookupEntry.Rejected)
        assertEquals(2, (collapsed[1] as MacLookupEntry.Address).occurrences)
        assertTrue(collapsed[2] is MacLookupEntry.Rejected)
    }

    @Test
    fun `an OUI prefix is not merged with a full address that starts with it`() {
        val entries = engine.lookupAll("00:1A:2B 00:1A:2B:3C:4D:5E")

        val collapsed = MacLookupDedupe.collapse(entries)

        assertEquals(2, collapsed.size)
    }

    private object NoBlocks : OuiRegistry {
        override fun match(hexDigits: String): OuiMatch? = null
    }
}
