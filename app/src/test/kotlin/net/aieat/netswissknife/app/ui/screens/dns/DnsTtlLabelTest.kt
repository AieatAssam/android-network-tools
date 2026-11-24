package net.aieat.netswissknife.app.ui.screens.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("dnsTtlLabel")
class DnsTtlLabelTest {
    @Test
    fun `seconds below one minute are shown in seconds`() {
        assertEquals("0s", dnsTtlLabel(0))
        assertEquals("59s", dnsTtlLabel(59))
    }

    @Test
    fun `minutes keep their remaining seconds`() {
        assertEquals("1m", dnsTtlLabel(60))
        assertEquals("1m 30s", dnsTtlLabel(90))
    }

    @Test
    fun `hours keep their remaining minutes`() {
        assertEquals("1h", dnsTtlLabel(3_600))
        assertEquals("1h 1m", dnsTtlLabel(3_660))
        assertEquals("24h", dnsTtlLabel(86_400))
    }
}
