package net.aieat.netswissknife.app.ui.screens

import net.aieat.netswissknife.app.ui.navigation.ToolSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeScreenGroupingTest {
    private val sections =
        listOf(
            ToolSection(labelRes = 1, routes = listOf("ping", "dns")),
            ToolSection(labelRes = 2, routes = listOf("tls")),
            ToolSection(labelRes = 3, routes = listOf("subnet", "wol")),
        )

    @Test
    fun `tools are grouped under their section in section order`() {
        val tools = listOf("tls", "dns", "subnet", "ping", "wol")

        val grouped = groupBySection(sections, tools) { it }

        assertEquals(
            listOf(
                1 to listOf("ping", "dns"),
                2 to listOf("tls"),
                3 to listOf("subnet", "wol"),
            ),
            grouped.map { (section, members) -> section.labelRes to members },
        )
    }

    @Test
    fun `section member order follows the section, not the input list`() {
        val grouped = groupBySection(sections, listOf("dns", "ping")) { it }

        assertEquals(listOf("ping", "dns"), grouped.single().second)
    }

    @Test
    fun `empty sections are dropped`() {
        val grouped = groupBySection(sections, listOf("tls")) { it }

        assertEquals(listOf(2), grouped.map { it.first.labelRes })
    }

    @Test
    fun `tools outside every section are not shown`() {
        val grouped = groupBySection(sections, listOf("home", "ping")) { it }

        assertEquals(listOf("ping"), grouped.single().second)
    }

    @Test
    fun `routes are read through the accessor`() {
        data class Tool(
            val route: String,
        )

        val grouped = groupBySection(sections, listOf(Tool("tls"))) { it.route }

        assertEquals(listOf(Tool("tls")), grouped.single().second)
    }
}
