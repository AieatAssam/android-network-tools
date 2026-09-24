package net.aieat.netswissknife.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareUtilsTest {
    @Test
    fun boundShareText_keepsReportsAtOrBelowLimitUnchanged() {
        val report = "a".repeat(MAX_SHARE_TEXT_CHARS)

        assertEquals(report, boundShareText(report))
    }

    @Test
    fun boundShareText_truncatesOversizedReportsWithAnExplanation() {
        val report = "a".repeat(MAX_SHARE_TEXT_CHARS + 1)

        val bounded = boundShareText(report)

        assertTrue(bounded.length < MAX_SHARE_TEXT_CHARS + 100)
        assertTrue(bounded.startsWith("a".repeat(MAX_SHARE_TEXT_CHARS)))
        assertTrue(bounded.endsWith("[Report truncated to fit in a share message.]"))
    }

    @Test
    fun boundShareText_doesNotSplitSurrogatePairsAtTheLimit() {
        val report = "a".repeat(MAX_SHARE_TEXT_CHARS - 1) + "\uD83D\uDE80tail"

        val bounded = boundShareText(report)

        assertFalse(bounded.any { Character.isSurrogate(it) })
        assertTrue(bounded.endsWith("[Report truncated to fit in a share message.]"))
    }
}
