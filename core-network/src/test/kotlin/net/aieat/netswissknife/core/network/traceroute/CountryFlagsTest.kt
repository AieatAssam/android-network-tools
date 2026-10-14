package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CountryFlagsTest {

    @Test
    fun `formats regional indicator flags for supported country codes`() {
        assertEquals("🇬🇧", CountryFlags.flagEmoji("GB"))
        assertEquals("🇩🇪", CountryFlags.flagEmoji("DE"))
        assertEquals("🇬🇧", CountryFlags.flagEmoji("gb"))
        assertEquals("🇬🇧 United Kingdom", CountryFlags.label("GB", "GB"))
    }

    @Test
    fun `rejects malformed and unknown country codes`() {
        assertEquals("", CountryFlags.flagEmoji(null))
        assertEquals("", CountryFlags.flagEmoji(""))
        assertEquals("", CountryFlags.flagEmoji("GBR"))
        assertEquals("", CountryFlags.flagEmoji("1B"))
        assertEquals("", CountryFlags.flagEmoji("ß"))
        assertEquals("", CountryFlags.flagEmoji("ZZ"))
        assertNull(CountryFlags.countryName("ß"))
        assertNull(CountryFlags.countryName("ZZ"))
        assertEquals("Provider's country name", CountryFlags.label("ZZ", "Provider's country name"))
    }

    @Test
    fun `resolves country name using stable English display locale`() {
        assertEquals("Germany", CountryFlags.countryName("DE"))
    }
}
