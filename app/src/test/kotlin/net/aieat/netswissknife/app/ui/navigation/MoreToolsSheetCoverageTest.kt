package net.aieat.netswissknife.app.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoreToolsSheetCoverageTest {
    private val sectionRoutes = MORE_TOOL_SECTIONS.flatMap { it.routes }

    @Test
    fun `every tool in allTools is listed in exactly one More sheet section`() {
        NavRoutes.allTools.forEach { tool ->
            assertEquals(
                1,
                sectionRoutes.count { it == tool.route },
                "Route '${tool.route}' must appear exactly once in MORE_TOOL_SECTIONS",
            )
        }
    }

    @Test
    fun `every More sheet route refers to a known tool`() {
        val known = NavRoutes.allTools.map { it.route }.toSet()
        sectionRoutes.forEach { route ->
            assertTrue(route in known, "Section route '$route' is not in NavRoutes.allTools")
        }
    }

    @Test
    fun `Wake-on-LAN is listed under Utilities`() {
        val utilities = MORE_TOOL_SECTIONS.single { "wol" in it.routes }
        assertTrue("subnet" in utilities.routes && "speedtest" in utilities.routes)
    }
}
