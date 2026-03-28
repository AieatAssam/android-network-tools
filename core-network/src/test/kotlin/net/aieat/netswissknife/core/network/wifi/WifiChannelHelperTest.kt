package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("WifiChannelHelper")
class WifiChannelHelperTest {

    @Nested
    @DisplayName("frequencyToChannel – 2.4 GHz")
    inner class Channel24GHz {
        @ParameterizedTest(name = "{0} MHz → channel {1}")
        @CsvSource(
            "2412, 1",
            "2417, 2",
            "2422, 3",
            "2432, 5",
            "2437, 6",
            "2462, 11",
            "2472, 13",
            "2484, 14"
        )
        fun `maps frequency to correct channel`(freqMhz: Int, expected: Int) {
            assertEquals(expected, WifiChannelHelper.frequencyToChannel(freqMhz, WifiBand.BAND_2_4GHZ))
        }
    }

    @Nested
    @DisplayName("frequencyToChannel – 5 GHz")
    inner class Channel5GHz {
        @ParameterizedTest(name = "{0} MHz → channel {1}")
        @CsvSource(
            "5180, 36",
            "5200, 40",
            "5220, 44",
            "5240, 48",
            "5745, 149",
            "5785, 157",
            "5825, 165"
        )
        fun `maps frequency to correct channel`(freqMhz: Int, expected: Int) {
            assertEquals(expected, WifiChannelHelper.frequencyToChannel(freqMhz, WifiBand.BAND_5GHZ))
        }
    }

    @Nested
    @DisplayName("frequencyToChannel – 6 GHz")
    inner class Channel6GHz {
        @Test
        fun `5955 MHz maps to channel 1`() {
            assertEquals(1, WifiChannelHelper.frequencyToChannel(5955, WifiBand.BAND_6GHZ))
        }

        @Test
        fun `6055 MHz maps to channel 21`() {
            assertEquals(21, WifiChannelHelper.frequencyToChannel(6055, WifiBand.BAND_6GHZ))
        }
    }

    @Nested
    @DisplayName("channelWidthMhz")
    inner class ChannelWidth {
        @ParameterizedTest(name = "constant {0} → {1} MHz")
        @CsvSource(
            "0, 20",
            "1, 40",
            "2, 80",
            "3, 160",
            "4, 160",
            "5, 320",
            "99, 20"
        )
        fun `maps constants to MHz values`(constant: Int, expectedMhz: Int) {
            assertEquals(expectedMhz, WifiChannelHelper.channelWidthMhz(constant))
        }
    }

    @Nested
    @DisplayName("overlapping24GHzChannels")
    inner class Overlapping {
        @Test
        fun `channel 1 overlaps channels 1 through 5`() {
            val result = WifiChannelHelper.overlapping24GHzChannels(1)
            assertTrue(result.containsAll(listOf(1, 2, 3, 4, 5)))
        }

        @Test
        fun `channel 6 overlaps channels 2 through 10`() {
            val result = WifiChannelHelper.overlapping24GHzChannels(6)
            assertEquals(setOf(2, 3, 4, 5, 6, 7, 8, 9, 10), result)
        }

        @Test
        fun `channel 11 overlaps channels 7 through 14`() {
            val result = WifiChannelHelper.overlapping24GHzChannels(11)
            assertTrue(result.containsAll(listOf(7, 8, 9, 10, 11, 12, 13)))
        }

        @Test
        fun `no channel below 1 in overlap set`() {
            val result = WifiChannelHelper.overlapping24GHzChannels(1)
            assertTrue(result.none { it < 1 })
        }

        @Test
        fun `no channel above 14 in overlap set`() {
            val result = WifiChannelHelper.overlapping24GHzChannels(13)
            assertTrue(result.none { it > 14 })
        }
    }

    @Nested
    @DisplayName("WifiBand.fromFrequency")
    inner class BandFromFrequency {
        @Test
        fun `2412 MHz is 2_4 GHz`() {
            assertEquals(WifiBand.BAND_2_4GHZ, WifiBand.fromFrequency(2412))
        }

        @Test
        fun `5180 MHz is 5 GHz`() {
            assertEquals(WifiBand.BAND_5GHZ, WifiBand.fromFrequency(5180))
        }

        @Test
        fun `5955 MHz is 6 GHz`() {
            assertEquals(WifiBand.BAND_6GHZ, WifiBand.fromFrequency(5955))
        }

        @Test
        fun `unknown frequency returns UNKNOWN`() {
            assertEquals(WifiBand.UNKNOWN, WifiBand.fromFrequency(1000))
        }
    }

    @Nested
    @DisplayName("WifiSecurity.fromCapabilities")
    inner class SecurityParsing {
        @Test
        fun `SAE only is WPA3`() {
            assertEquals(WifiSecurity.WPA3, WifiSecurity.fromCapabilities("[SAE][ESS]"))
        }

        @Test
        fun `PSK and SAE is WPA2_WPA3`() {
            assertEquals(WifiSecurity.WPA2_WPA3, WifiSecurity.fromCapabilities("[WPA2-PSK-CCMP][SAE][ESS]"))
        }

        @Test
        fun `WPA2-PSK is WPA2`() {
            assertEquals(WifiSecurity.WPA2, WifiSecurity.fromCapabilities("[WPA2-PSK-CCMP][ESS]"))
        }

        @Test
        fun `WEP is WEP`() {
            assertEquals(WifiSecurity.WEP, WifiSecurity.fromCapabilities("[WEP][ESS]"))
        }

        @Test
        fun `ESS only is OPEN`() {
            assertEquals(WifiSecurity.OPEN, WifiSecurity.fromCapabilities("[ESS]"))
        }

        @Test
        fun `OWE is Enhanced Open`() {
            assertEquals(WifiSecurity.OWE, WifiSecurity.fromCapabilities("[OWE][ESS]"))
        }
    }
}
