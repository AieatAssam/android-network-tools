package net.aieat.netswissknife.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("recentHostsExcept")
class RecentHostsExceptTest {
    @Test
    fun `a recent equal to the field value is removed`() {
        assertEquals(
            listOf("https://b.example/"),
            recentHostsExcept(listOf("https://a.example/", "https://b.example/"), "https://a.example/"),
        )
    }

    @Test
    fun `comparison ignores case and surrounding spaces`() {
        assertEquals(
            listOf("https://b.example/"),
            recentHostsExcept(listOf("HTTPS://A.example/", "https://b.example/"), "  https://a.example/ "),
        )
    }

    @Test
    fun `a value that is not a recent keeps every recent`() {
        val recents = listOf("https://a.example/", "https://b.example/")
        assertEquals(recents, recentHostsExcept(recents, "https://c.example/"))
    }

    @Test
    fun `a null or blank field keeps every recent`() {
        val recents = listOf("https://a.example/")
        assertEquals(recents, recentHostsExcept(recents, null))
        assertEquals(recents, recentHostsExcept(recents, "   "))
    }

    @Test
    fun `when the only recent is the field value the result is empty`() {
        assertEquals(emptyList<String>(), recentHostsExcept(listOf("https://a.example/"), "https://a.example/"))
    }
}
