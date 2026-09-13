package net.aieat.netswissknife.app

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Characterizes the width -> navigation layout mapping [MainActivity] relies
 * on for its adaptive nav shell (`NavigationSuiteScaffoldDefaults`), so a
 * future library upgrade that shifts these breakpoints fails a test instead
 * of silently changing the app's compact/expanded behavior.
 */
@DisplayName("Adaptive navigation layout selection by window width")
class AdaptiveNavigationLayoutTest {

    private fun layoutTypeFor(widthDp: Int): NavigationSuiteType {
        val adaptiveInfo = WindowAdaptiveInfo(WindowSizeClass.compute(widthDp.toFloat(), 800f), Posture())
        return NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    }

    @Test
    @DisplayName("phone-width windows use a bottom navigation bar")
    fun `compact width uses navigation bar`() {
        assertEquals(NavigationSuiteType.NavigationBar, layoutTypeFor(400))
    }

    @Test
    @DisplayName("just below the medium breakpoint still uses a bottom navigation bar")
    fun `width just under 600dp still uses navigation bar`() {
        assertEquals(NavigationSuiteType.NavigationBar, layoutTypeFor(599))
    }

    @Test
    @DisplayName("tablet/foldable-width windows switch to a navigation rail")
    fun `medium width uses navigation rail`() {
        assertEquals(NavigationSuiteType.NavigationRail, layoutTypeFor(600))
    }

    @Test
    @DisplayName("desktop-width windows keep the navigation rail")
    fun `expanded width uses navigation rail`() {
        assertEquals(NavigationSuiteType.NavigationRail, layoutTypeFor(1600))
    }
}
