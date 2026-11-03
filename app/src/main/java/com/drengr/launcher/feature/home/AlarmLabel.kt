package com.drengr.launcher.feature.home

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object AlarmLabel {
    /** "Alarm today 5:00 PM", "Alarm tomorrow 07:00", "Alarm Fri 7:00 AM", or "Alarm Nov 12 7:00 AM". */
    fun format(
        triggerMs: Long,
        now: LocalDateTime,
        use24h: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val time = Instant.ofEpochMilli(triggerMs).atZone(zone).toLocalDateTime()
        val clock = time.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())
        val day = when {
            days <= 0L -> "today"
            days == 1L -> "tomorrow"
            days < 7L -> time.format(DateTimeFormatter.ofPattern("EEE", locale))
            else -> time.format(DateTimeFormatter.ofPattern("MMM d", locale))
        }
        return "Alarm $day $clock"
    }
}
