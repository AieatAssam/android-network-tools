package net.aieat.netswissknife.app.ui.navigation

import net.aieat.netswissknife.app.ui.screens.groupBySection
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
    fun `the Home grid lists every tool, as the welcome guide says`() {
        val homeRoutes = groupBySection(MORE_TOOL_SECTIONS, NavRoutes.allTools) { it.route }.flatMap { it.second }
        assertEquals(NavRoutes.allTools.map { it.route }.toSet(), homeRoutes.map { it.route }.toSet())
    }

    @Test
    fun `every tool the welcome guide names opens from Home and from More`() {
        // The guide's use-case page lists MORE_TOOL_SECTIONS through NavRoutes.allTools, as Home and More do.
        val known = NavRoutes.allTools.associateBy { it.route }
        val guideRoutes = MORE_TOOL_SECTIONS.flatMap { section -> section.routes.mapNotNull { known[it]?.route } }
        val homeRoutes =
            groupBySection(MORE_TOOL_SECTIONS, NavRoutes.allTools) { it.route }
                .flatMap { it.second }
                .map { it.route }
                .toSet()
        assertEquals(sectionRoutes, guideRoutes)
        guideRoutes.forEach { route ->
            assertTrue(route in homeRoutes, "Guide names '$route', which Home does not show")
        }
    }

    @Test
    fun `Wake-on-LAN is listed under Utilities`() {
        val utilities = MORE_TOOL_SECTIONS.single { "wol" in it.routes }
        assertTrue("subnet" in utilities.routes && "speedtest" in utilities.routes)
    }
}
