package net.aieat.netswissknife.app.ui.screens

import android.Manifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WifiPermissionsTest {

    @Test
    fun `Android 12 and older requests fine location`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requiredWifiPermissions(32)
        )
    }

    @Test
    fun `Android 13 and newer requests fine location and nearby Wi-Fi`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            requiredWifiPermissions(33)
        )
    }
}
