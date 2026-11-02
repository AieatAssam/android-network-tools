package net.aieat.netswissknife.app.ui.screens.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LAN result view: List or Map over the same device list")
class LanResultViewTest {
    @Test
    fun `a fresh result opens on the list`() {
        assertEquals(LanResultView.LIST, LanResultView.DEFAULT)
    }

    @Test
    fun `a stored map choice is restored`() {
        assertEquals(LanResultView.MAP, LanResultView.fromStoredName("MAP"))
    }

    @Test
    fun `a stored list choice is restored`() {
        assertEquals(LanResultView.LIST, LanResultView.fromStoredName("LIST"))
    }

    @Test
    fun `a missing or unknown stored name falls back to the list`() {
        assertEquals(LanResultView.LIST, LanResultView.fromStoredName(null))
        assertEquals(LanResultView.LIST, LanResultView.fromStoredName("TABLE"))
    }

    @Test
    fun `revealing a host from the map shows the list`() {
        assertEquals(LanResultView.LIST, LanResultView.MAP.afterReveal())
    }

    @Test
    fun `revealing a host from the list keeps the list`() {
        assertEquals(LanResultView.LIST, LanResultView.LIST.afterReveal())
    }
}
