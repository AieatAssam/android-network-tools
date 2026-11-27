package net.aieat.netswissknife.app.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NavigationRouteIdentityTest {
    @Test
    fun `query route arguments keep the base tool selected in app chrome`() {
        assertEquals("wol", navigationRouteIdentity("wol?intent={intent}&mac={mac}"))
        assertEquals("ping", navigationRouteIdentity("ping?host={host}&intent={intent}"))
        assertEquals("home", navigationRouteIdentity("home"))
        assertNull(navigationRouteIdentity(null))
    }
}
