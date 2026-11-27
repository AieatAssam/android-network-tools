package net.aieat.netswissknife.app.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BssidDedupeTest {
    private data class Entry(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
    )

    private fun keep(entries: List<Entry>): List<Entry> =
        keepOnePerBssid(
            entries,
            bssid = { it.bssid },
            isNamed = { it.ssid.isNotBlank() },
            rssi = { it.rssi },
        )

    @Test
    fun `a hidden copy of a named access point is folded into the named entry`() {
        val named = Entry("e8:9f:80:77:61:60", "Brilliant Radiator!", -46)
        val hiddenCopy = Entry("e8:9f:80:77:61:60", "", -46)

        assertEquals(listOf(named), keep(listOf(hiddenCopy, named)))
    }

    @Test
    fun `between two entries with the same name the stronger signal is kept`() {
        val weaker = Entry("aa:bb:cc:dd:ee:ff", "Office", -70)
        val stronger = Entry("aa:bb:cc:dd:ee:ff", "Office", -50)

        assertEquals(listOf(stronger), keep(listOf(weaker, stronger)))
    }

    @Test
    fun `distinct BSSIDs are all kept`() {
        val first = Entry("aa:bb:cc:dd:ee:01", "Office", -50)
        val second = Entry("aa:bb:cc:dd:ee:02", "Office", -60)

        assertEquals(setOf(first, second), keep(listOf(first, second)).toSet())
    }

    @Test
    fun `entries without a BSSID are never merged with each other`() {
        val first = Entry("", "Office", -50)
        val second = Entry("", "Office", -50)

        assertEquals(2, keep(listOf(first, second)).size)
    }
}
