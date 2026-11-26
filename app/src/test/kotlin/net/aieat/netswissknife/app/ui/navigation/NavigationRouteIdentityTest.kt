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

    @Test
    fun `choosing a tool whose restored stack has a handoff on top lands on the tool`() {
        val restored = listOf(null, "home", "lan", "ping?host={host}&intent={intent}")

        assertEquals(2, toolLandingIndex(restored, "lan"))
    }

    @Test
    fun `every handoff target above its source gives way to the source tool`() {
        val handoffs =
            listOf(
                NavRoutes.Lan.route to NavRoutes.Ping.route,
                NavRoutes.Lan.route to NavRoutes.Ports.route,
                NavRoutes.Lan.route to NavRoutes.TlsInspector.route,
                NavRoutes.Lan.route to NavRoutes.HttpProbe.route,
                NavRoutes.Lan.route to NavRoutes.WakeOnLan.route,
                NavRoutes.MdnsDiscovery.route to NavRoutes.HttpProbe.route,
            )
        handoffs.forEach { (source, target) ->
            val stack = listOf(null, NavRoutes.Home.route, source, target)
            assertEquals(2, toolLandingIndex(stack, source), "$source under $target")
        }
    }

    @Test
    fun `a tool already on top or absent from the stack needs no further pop`() {
        assertNull(toolLandingIndex(listOf(null, "home", "lan", "ping?host={host}&intent={intent}"), "ping"))
        assertNull(toolLandingIndex(listOf(null, "home"), "home"))
        assertNull(toolLandingIndex(listOf(null, "home", "dns"), "lan"))
    }
}
