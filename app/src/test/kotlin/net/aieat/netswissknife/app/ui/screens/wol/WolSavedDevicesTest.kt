package net.aieat.netswissknife.app.ui.screens.wol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WolSavedDevicesTest {
    private val a = WolSavedDevice("AA:BB:CC:DD:EE:01", "A")
    private val b = WolSavedDevice("AA:BB:CC:DD:EE:02", "B")

    @Test
    fun `upsert appends a new device and keeps existing order`() {
        assertEquals(listOf(a, b), WolSavedDevices.upsert(listOf(a), b.mac, b.name))
    }

    @Test
    fun `upsert of an already saved MAC updates the name instead of duplicating`() {
        assertEquals(
            listOf(WolSavedDevice(a.mac, "Renamed"), b),
            WolSavedDevices.upsert(listOf(a, b), a.mac, "Renamed"),
        )
    }

    @Test
    fun `re-saving a saved MAC with a blank name keeps the saved name`() {
        assertEquals(listOf(a, b), WolSavedDevices.upsert(listOf(a, b), a.mac, "   "))
    }

    @Test
    fun `save notice reports a kept name when a saved MAC is re-saved without a new name`() {
        assertEquals(WolSaveNotice.KeptName("A"), WolSavedDevices.saveNotice(listOf(a, b), a.mac, ""))
        assertEquals(WolSaveNotice.KeptName("A"), WolSavedDevices.saveNotice(listOf(a, b), a.mac, "A"))
    }

    @Test
    fun `save notice reports the rename when a saved MAC gets a new name`() {
        assertEquals(
            WolSaveNotice.Renamed(previousName = "A", name = "Renamed"),
            WolSavedDevices.saveNotice(listOf(a, b), a.mac, "Renamed"),
        )
    }

    @Test
    fun `save notice is absent for a MAC that is not saved yet`() {
        assertEquals(null, WolSavedDevices.saveNotice(listOf(a), b.mac, "B"))
    }

    @Test
    fun `blank name is stored empty and displays as the MAC`() {
        val stored = WolSavedDevices.upsert(emptyList(), a.mac, "   ")
        assertEquals("", stored.single().name)
        assertEquals(a.mac, WolSavedDevices.displayName(stored.single()))
    }

    @Test
    fun `names are single-line, trimmed and capped`() {
        val stored = WolSavedDevices.upsert(emptyList(), a.mac, "  Living\troom\nTV  " + "x".repeat(80))
        val name = stored.single().name
        assertTrue(name.length <= WolSavedDevices.MAX_NAME_LENGTH)
        assertTrue(name.startsWith("Living room TV"), name)
        assertTrue(name.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `remove drops only the matching MAC`() {
        assertEquals(listOf(b), WolSavedDevices.remove(listOf(a, b), a.mac))
        assertEquals(listOf(a, b), WolSavedDevices.remove(listOf(a, b), "AA:BB:CC:DD:EE:99"))
    }

    @Test
    fun `edit renames in place without a conflict`() {
        val outcome = WolSavedDevices.edit(listOf(a, b), a.mac, a.mac, "Renamed")
        assertEquals(WolSaveOutcome.Saved(listOf(WolSavedDevice(a.mac, "Renamed"), b)), outcome)
    }

    @Test
    fun `edit to a MAC saved on another entry is a Duplicate`() {
        assertEquals(WolSaveOutcome.Duplicate, WolSavedDevices.edit(listOf(a, b), a.mac, b.mac, "x"))
    }

    @Test
    fun `edit of a MAC that is no longer saved behaves like an upsert`() {
        assertEquals(
            WolSaveOutcome.Saved(listOf(a, b)),
            WolSavedDevices.edit(listOf(a), b.mac, b.mac, "B"),
        )
    }

    @Test
    fun `encode and decode round-trip, including names with separators`() {
        val devices = listOf(WolSavedDevice(a.mac, "tab\tname"), b)
        val encoded = WolSavedDevices.encode(devices)
        assertEquals(listOf(WolSavedDevice(a.mac, "tab name"), b), WolSavedDevices.decode(encoded))
    }

    @Test
    fun `decode skips blank, invalid, multicast and duplicate lines and keeps the first duplicate`() {
        val lines =
            listOf(
                "",
                "not-a-mac\tbroken",
                "01:00:5E:00:00:01\tmulticast",
                "aa-bb-cc-dd-ee-01\tfirst",
                "AA:BB:CC:DD:EE:01\tduplicate",
                "AA:BB:CC:DD:EE:02",
            )

        assertEquals(
            listOf(WolSavedDevice(a.mac, "first"), WolSavedDevice(b.mac, "")),
            WolSavedDevices.decode(lines.joinToString("\n")),
        )
    }

    @Test
    fun `decode of null or empty input is an empty list`() {
        assertEquals(emptyList<WolSavedDevice>(), WolSavedDevices.decode(null))
        assertEquals(emptyList<WolSavedDevice>(), WolSavedDevices.decode(""))
    }
}
