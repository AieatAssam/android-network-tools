package net.aieat.netswissknife.app.ui.screens.whois

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class WhoisDatesTest {
    private lateinit var deviceZone: TimeZone

    @BeforeEach
    fun saveDeviceZone() {
        deviceZone = TimeZone.getDefault()
    }

    @AfterEach
    fun restoreDeviceZone() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun `a registry date keeps its calendar day in every device zone`() {
        val utcMidnight = LocalDate.of(1995, 8, 14).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        listOf("America/Los_Angeles", "Pacific/Honolulu", "UTC", "Asia/Tokyo", "Pacific/Kiritimati").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals("1995-08-14", formatWhoisDate(utcMidnight), zone)
        }
    }

    @Test
    fun `a late UTC timestamp keeps its UTC day in every device zone`() {
        val lateUtc = LocalDate.of(2026, 11, 24).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

        listOf("America/New_York", "Europe/London", "Asia/Tokyo").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals("2026-11-24", formatWhoisDate(lateUtc), zone)
        }
    }
}
