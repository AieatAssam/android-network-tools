package net.aieat.netswissknife.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SheetSizingTest {
    @Test
    fun `tall phone window is capped to the fraction of the window`() {
        // 400x640dp phone: 88% of 640 is 563.2dp, below the status-bar and handle limit (568dp).
        assertEquals(563.2f, maxSheetHeightDp(windowHeightDp = 640f, statusBarTopDp = 24f), 0.01f)
    }

    @Test
    fun `cap always leaves room above the sheet for the status bar and drag handle`() {
        val windowHeights = listOf(360f, 480f, 593f, 640f, 960f, 1200f)
        for (window in windowHeights) {
            val cap = maxSheetHeightDp(windowHeightDp = window, statusBarTopDp = 24f)
            assertTrue(cap <= window - 24f - SHEET_HANDLE_ALLOWANCE_DP + 0.01f, "window=$window cap=$cap")
        }
    }

    @Test
    fun `short landscape window still gets a positive cap`() {
        // 360dp tall landscape phone: status bar and handle allowance eat most of the height.
        val cap = maxSheetHeightDp(windowHeightDp = 360f, statusBarTopDp = 24f)
        assertTrue(cap > 0f && cap < 360f, "cap=$cap")
    }

    @Test
    fun `zero or negative window never yields a negative cap`() {
        assertEquals(0f, maxSheetHeightDp(windowHeightDp = 0f, statusBarTopDp = 24f), 0.01f)
        assertTrue(maxSheetHeightDp(windowHeightDp = 10f, statusBarTopDp = 24f) >= 0f)
    }
}
