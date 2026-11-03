package com.drengr.launcher.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class AlarmLabelTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 11, 3, 14, 30) // Tuesday 2:30 PM

    private fun label(at: LocalDateTime, use24h: Boolean = false) =
        AlarmLabel.format(at.atZone(zone).toInstant().toEpochMilli(), now, use24h, zone, Locale.US)

    @Test fun todayAndTomorrow() {
        assertEquals("Alarm today 5:00 PM", label(now.withHour(17).withMinute(0)))
        assertEquals("Alarm tomorrow 7:00 AM", label(now.plusDays(1).withHour(7).withMinute(0)))
        assertEquals("Alarm tomorrow 07:00", label(now.plusDays(1).withHour(7).withMinute(0), use24h = true))
    }

    @Test fun laterThisWeekThenDate() {
        assertEquals("Alarm Fri 7:00 AM", label(now.plusDays(3).withHour(7).withMinute(0)))
        assertEquals("Alarm Nov 12 7:00 AM", label(now.plusDays(9).withHour(7).withMinute(0)))
    }
}
