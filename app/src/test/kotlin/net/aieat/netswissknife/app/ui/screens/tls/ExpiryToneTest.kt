package net.aieat.netswissknife.app.ui.screens.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpiryToneTest {
    @Test
    fun `certificate valid for more than 30 days is OK`() {
        assertEquals(ExpiryTone.OK, expiryTone(daysLeft = 31, expired = false, notYetValid = false))
        assertEquals(ExpiryTone.OK, expiryTone(daysLeft = 400, expired = false, notYetValid = false))
    }

    @Test
    fun `certificate expiring within 30 days is WARNING including the boundary`() {
        assertEquals(ExpiryTone.WARNING, expiryTone(daysLeft = 30, expired = false, notYetValid = false))
        assertEquals(ExpiryTone.WARNING, expiryTone(daysLeft = 0, expired = false, notYetValid = false))
    }

    @Test
    fun `expired or not yet valid certificate is ERROR regardless of day count`() {
        assertEquals(ExpiryTone.ERROR, expiryTone(daysLeft = -3, expired = true, notYetValid = false))
        assertEquals(ExpiryTone.ERROR, expiryTone(daysLeft = 0, expired = true, notYetValid = false))
        assertEquals(ExpiryTone.ERROR, expiryTone(daysLeft = 90, expired = false, notYetValid = true))
    }
}
