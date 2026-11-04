package net.aieat.netswissknife.core.network.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WifiStandardPhyRateTest {
    @Test
    fun `wifi 4 on a 20 MHz channel is not labelled with the 40 MHz figure`() {
        val ceiling = WifiStandard.WIFI_4.phyRateCeiling(channelWidthMhz = 20)

        assertEquals("289 Mbps", ceiling.label)
        assertEquals(20, ceiling.widthMhz)
    }

    @Test
    fun `wifi 4 on a 40 MHz channel reports the 40 MHz four stream figure`() {
        val ceiling = WifiStandard.WIFI_4.phyRateCeiling(channelWidthMhz = 40)

        assertEquals("600 Mbps", ceiling.label)
        assertEquals(40, ceiling.widthMhz)
    }

    @Test
    fun `newer standards report the standard ceiling without claiming a width`() {
        val ceiling = WifiStandard.WIFI_5.phyRateCeiling(channelWidthMhz = 20)

        assertEquals(WifiStandard.WIFI_5.maxSpeedLabel, ceiling.label)
        assertNull(ceiling.widthMhz)
    }
}
