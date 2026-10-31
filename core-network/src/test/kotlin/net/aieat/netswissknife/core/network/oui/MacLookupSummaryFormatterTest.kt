package net.aieat.netswissknife.core.network.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MacLookupSummaryFormatter")
class MacLookupSummaryFormatterTest {
    private val labels =
        MacLookupSummaryLabels(
            title = "MAC Lookup",
            input = "Input",
            address = "Address",
            type = "Type",
            locallyAdministered = "Locally administered",
            vendor = "Vendor",
            block = "Block",
            yes = "Yes",
            no = "No",
            unknownVendor = "Unknown vendor",
            locallyAdministeredVendor = "Not meaningful (locally administered)",
            broadcastVendor = "Not applicable (broadcast)",
            rejected = "Not a MAC address or OUI prefix",
            noEntries = "No entries",
            kindName = ::kindLabel,
        )

    private fun kindLabel(kind: MacAddressKind): String =
        when (kind) {
            MacAddressKind.UNICAST -> "Unicast"
            MacAddressKind.MULTICAST -> "Multicast"
            MacAddressKind.BROADCAST -> "Broadcast"
        }

    private val nothingRegistered =
        object : OuiRegistry {
            override fun match(hexDigits: String): OuiMatch? = null
        }

    private val raspberryOnly =
        object : OuiRegistry {
            override fun match(hexDigits: String): OuiMatch? {
                if (!hexDigits.startsWith("B827EB")) return null
                return OuiMatch("Raspberry Pi Foundation", OuiBlockSize.MA_L)
            }
        }

    @Test
    fun `formats each entry with its address, type, vendor and block`() {
        val entries = MacLookupEngine(raspberryOnly).lookupAll("b8:27:eb:11:22:33")

        val text = MacLookupSummaryFormatter.format(entries, labels)

        assertTrue(text.startsWith("MAC Lookup"), text)
        assertTrue(text.contains("Input: b8:27:eb:11:22:33"), text)
        assertTrue(text.contains("Address: B8:27:EB:11:22:33"), text)
        assertTrue(text.contains("Type: Unicast"), text)
        assertTrue(text.contains("Locally administered: No"), text)
        assertTrue(text.contains("Vendor: Raspberry Pi Foundation"), text)
        assertTrue(text.contains("Block: MA-L"), text)
    }

    @Test
    fun `states the vendor reason for locally administered, broadcast and unknown addresses`() {
        val input = "02:00:00:00:00:01, FF:FF:FF:FF:FF:FF, 00:00:5E:00:53:01"
        val entries = MacLookupEngine(nothingRegistered).lookupAll(input)

        val text = MacLookupSummaryFormatter.format(entries, labels)

        assertTrue(text.contains("Vendor: Not meaningful (locally administered)"), text)
        assertTrue(text.contains("Vendor: Not applicable (broadcast)"), text)
        assertTrue(text.contains("Vendor: Unknown vendor"), text)
    }

    @Test
    fun `lists rejected entries with their input and says so`() {
        val entries = MacLookupEngine(nothingRegistered).lookupAll("nope")

        val text = MacLookupSummaryFormatter.format(entries, labels)

        assertTrue(text.contains("nope: Not a MAC address or OUI prefix"), text)
    }

    @Test
    fun `an empty input gives a stated empty report`() {
        assertEquals("MAC Lookup\nNo entries\n", MacLookupSummaryFormatter.format(emptyList(), labels))
    }
}
