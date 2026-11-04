package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.wifi.WifiBand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private const val SDK_ANDROID_8_1 = 27
private const val SDK_ANDROID_9 = 28
private const val SDK_ANDROID_10 = 29
private const val SDK_ANDROID_11 = 30

class MacAddressPolicyTest {
    @Test
    fun `hardware address is shown in canonical upper-case form`() {
        assertEquals(
            MacAddressState.Known("A8:BB:CC:DD:EE:FF", randomized = false),
            MacAddressPolicy.resolve("a8-bb-cc-dd-ee-ff", SDK_ANDROID_11),
        )
    }

    @Test
    fun `locally administered address is flagged as randomized`() {
        assertEquals(
            MacAddressState.Known("02:11:22:33:44:55", randomized = true),
            MacAddressPolicy.resolve("02:11:22:33:44:55", SDK_ANDROID_11),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["02:00:00:00:00:00", "00:00:00:00:00:00"])
    fun `placeholder and all-zero addresses are never shown as a device address`(raw: String) {
        assertEquals(
            MacAddressState.Unavailable(MacUnavailableReason.HIDDEN_BY_ANDROID),
            MacAddressPolicy.resolve(raw, SDK_ANDROID_11),
        )
        assertEquals(
            MacAddressState.Unavailable(MacUnavailableReason.NOT_REPORTED),
            MacAddressPolicy.resolve(raw, SDK_ANDROID_9),
        )
    }

    @Test
    fun `missing address explains the restriction by Android version`() {
        assertEquals(
            MacAddressState.Unavailable(MacUnavailableReason.HIDDEN_BY_ANDROID),
            MacAddressPolicy.resolve(null, SDK_ANDROID_10),
        )
        assertEquals(
            MacAddressState.Unavailable(MacUnavailableReason.NOT_REPORTED),
            MacAddressPolicy.resolve(null, SDK_ANDROID_8_1),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-mac", "aa:bb:cc:dd:ee", "aa:bb:cc:dd:ee:gg", "aa:bb-cc:dd:ee:ff"])
    fun `malformed text is unavailable rather than guessed`(raw: String) {
        assertEquals(
            MacAddressState.Unavailable(MacUnavailableReason.NOT_REPORTED),
            MacAddressPolicy.resolve(raw, SDK_ANDROID_11),
        )
    }
}

class WifiSsidPolicyTest {
    @Test
    fun `name stays hidden without location permission even when the system reports it`() {
        assertEquals(
            SsidState.Unavailable(SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED),
            WifiSsidPolicy.resolve("\"Home\"", locationPermissionGranted = false),
        )
    }

    @Test
    fun `quotes are removed when the name is readable`() {
        assertEquals(SsidState.Known("Home"), WifiSsidPolicy.resolve("\"Home\"", locationPermissionGranted = true))
    }

    @Test
    fun `redacted, missing or blank names are unavailable`() {
        val reasons = listOf("<unknown ssid>", null, "   ", "\"\"")
        reasons.forEach { raw ->
            assertEquals(
                SsidState.Unavailable(SsidUnavailableReason.NOT_REPORTED),
                WifiSsidPolicy.resolve(raw, locationPermissionGranted = true),
                "raw=$raw",
            )
        }
    }
}

class LinkValueSentinelsTest {
    @Test
    fun `sentinel values read as unknown`() {
        assertNull(LinkValueSentinels.rssiDbm(-127))
        assertNull(LinkValueSentinels.linkSpeedMbps(-1))
        assertNull(LinkValueSentinels.linkSpeedMbps(0))
        assertNull(LinkValueSentinels.frequencyMhz(-1))
        assertNull(LinkValueSentinels.frequencyMhz(0))
        assertNull(LinkValueSentinels.mtuBytes(0))
    }

    @Test
    fun `plausible values pass through unchanged`() {
        assertEquals(-45, LinkValueSentinels.rssiDbm(-45))
        assertEquals(866, LinkValueSentinels.linkSpeedMbps(866))
        assertEquals(5180, LinkValueSentinels.frequencyMhz(5180))
        assertEquals(1500, LinkValueSentinels.mtuBytes(1500))
    }

    @Test
    fun `band and channel follow the frequency`() {
        val details =
            WifiLinkDetails(
                ssid = SsidState.Known("Home"),
                rssiDbm = -50,
                linkSpeedMbps = 866,
                frequencyMhz = 2437,
            )
        assertEquals(WifiBand.BAND_2_4GHZ, details.band)
        assertEquals(6, details.channel)

        val fiveGhz = details.copy(frequencyMhz = 5180)
        assertEquals(WifiBand.BAND_5GHZ, fiveGhz.band)
        assertEquals(36, fiveGhz.channel)

        assertNull(details.copy(frequencyMhz = null).band)
        assertNull(details.copy(frequencyMhz = null).channel)
    }
}
