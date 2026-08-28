package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("WifiChannelInfo")
class WifiChannelInfoTest {

    private fun channelInfo(score: Float) = WifiChannelInfo(
        channel = 6,
        frequencyMhz = 2437,
        band = WifiBand.BAND_2_4GHZ,
        accessPointCount = 3,
        congestionScore = score,
        accessPoints = emptyList()
    )

    @ParameterizedTest
    @CsvSource(
        "1.0, Very Busy",
        "0.75, Very Busy",
        "0.74, Busy",
        "0.5, Busy",
        "0.49, Moderate",
        "0.25, Moderate",
        "0.24, Clear",
        "0.0, Clear"
    )
    @DisplayName("labels congestion at each band boundary")
    fun congestionLabel(score: Float, expected: String) {
        assertEquals(expected, channelInfo(score).congestionLabel)
    }

    @Test
    @DisplayName("compares by value")
    fun valueSemantics() {
        val a = channelInfo(0.5f)
        assertEquals(a, channelInfo(0.5f))
        assertEquals(a.hashCode(), channelInfo(0.5f).hashCode())
        assertTrue(a.toString().contains("channel=6"))
        assertEquals(11, a.copy(channel = 11).channel)
        assertEquals(6, a.component1())
        assertEquals(2437, a.component2())
        assertEquals(WifiBand.BAND_2_4GHZ, a.component3())
        assertEquals(3, a.component4())
        assertEquals(0.5f, a.component5())
        assertTrue(a.component6().isEmpty())
    }
}
