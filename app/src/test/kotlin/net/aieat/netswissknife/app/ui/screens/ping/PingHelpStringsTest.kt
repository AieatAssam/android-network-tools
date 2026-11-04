package net.aieat.netswissknife.app.ui.screens.ping

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Ping help strings")
class PingHelpStringsTest {
    @Test
    fun `help bullets are not double-escaped percent signs`() {
        // The help bullets are rendered without String.format, so "%%" would show literally.
        val strings = File("src/main/res/values/strings.xml").readText()
        val helpArrayPattern =
            Regex("""<string-array name="help_ping_\w+">.*?</string-array>""", RegexOption.DOT_MATCHES_ALL)
        val helpArrays =
            helpArrayPattern.findAll(strings).map { it.value }.toList()
        assertFalse(helpArrays.isEmpty())
        helpArrays.forEach { array -> assertFalse(array.contains("%%"), array) }
    }
}
