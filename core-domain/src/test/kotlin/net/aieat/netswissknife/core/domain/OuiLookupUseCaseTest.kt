package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.oui.MacInputProblem
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.OuiBlockSize
import net.aieat.netswissknife.core.network.oui.OuiMatch
import net.aieat.netswissknife.core.network.oui.OuiRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OuiLookupUseCase")
class OuiLookupUseCaseTest {
    private val registry =
        object : OuiRegistry {
            override fun match(hexDigits: String): OuiMatch? {
                if (!hexDigits.startsWith("B827EB")) return null
                return OuiMatch("Raspberry Pi Foundation", OuiBlockSize.MA_L)
            }
        }

    private fun useCase() = OuiLookupUseCase(registry, dispatcher = Dispatchers.Unconfined)

    @Test
    fun `returns one entry per pasted address in order`() =
        runBlocking {
            val report = useCase()("b8:27:eb:11:22:33\n02:00:00:00:00:01")

            assertEquals(2, report.entries.size)
            assertTrue(report.entries[0] is MacLookupEntry.Address)
            assertTrue(report.entries[1] is MacLookupEntry.Address)
            assertFalse(report.truncated)
        }

    @Test
    fun `keeps malformed entries so the user sees what was rejected`() =
        runBlocking {
            val report = useCase()("nonsense")

            val expected = MacLookupEntry.Rejected("nonsense", MacInputProblem.CHARACTERS)
            assertEquals(listOf<MacLookupEntry>(expected), report.entries)
        }

    @Test
    fun `blank input gives an empty report`() =
        runBlocking {
            val report = useCase()("   \n ,")

            assertEquals(emptyList<MacLookupEntry>(), report.entries)
            assertFalse(report.truncated)
        }

    @Test
    fun `caps a very large paste and says it was truncated`() =
        runBlocking {
            val pasted = (1..150).joinToString("\n") { "B8:27:EB:00:00:%02X".format(it) }

            val report = useCase()(pasted)

            assertEquals(OuiLookupUseCase.MAX_ENTRIES, report.entries.size)
            assertTrue(report.truncated)
        }

    @Test
    fun `the same address in four notations is one entry counted four times`() =
        runBlocking {
            val report =
                useCase().invoke("00:1A:2B:3C:4D:5E, 00-1a-2b-3c-4d-5e, 001A.2B3C.4D5E, 001a2b3c4d5e")

            assertEquals(1, report.entries.size)
            assertEquals(4, (report.entries.single() as MacLookupEntry.Address).occurrences)
        }
}
